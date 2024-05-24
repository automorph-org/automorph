package automorph.transport

import automorph.log.Logger
import automorph.spi.EffectSystem
import automorph.spi.EffectSystem.Completable
import automorph.transport.ConnectionPool.{
  AddConnection, EnqueueUsage, OpenConnection, Pool, ProvideAction, ServeUsage, UseConnection,
}
import automorph.util.Extensions.EffectOps
import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

final private[automorph] case class ConnectionPool[Effect[_], Endpoint, Connection](
  openConnection: Option[Endpoint => Effect[Connection]],
  closeConnection: Connection => Effect[Unit],
  maxPeerConnections: Option[Int],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  logger: Logger,
) {
  private val pools = TrieMap[String, Pool[Effect, Connection]]().withDefaultValue(Pool())
  private val active = new AtomicBoolean(false)
  private val closedMessage = "Connection pool is closed"
  implicit private val system: EffectSystem[Effect] = effectSystem

  def using[T](peerId: String, endpoint: Endpoint, use: Connection => Effect[T]): Effect[T] =
    if (active.get) {
      val pool = pools(peerId)
      val action = pool.synchronized {
        pool.unusedConnections.removeHeadOption().map(UseConnection.apply[Effect, Endpoint, Connection]).getOrElse {
          lazy val newConnectionAllowed = maxPeerConnections.forall(pool.managedConnections < _)
          openConnection.filter(_ => newConnectionAllowed).map { open =>
            pool.managedConnections += 1
            OpenConnection(open)
          }.getOrElse(EnqueueUsage[Effect, Endpoint, Connection]())
        }
      }
      provideConnection(pool, endpoint, action).flatMap { connection =>
        use(connection).flatFold(
          error => addConnection(pool, connection).flatMap(_ => effectSystem.failed(error)),
          result => addConnection(pool, connection).map(_ => result),
        )
      }
    } else {
      system.failed(new IllegalStateException(closedMessage))
    }

  def add(peerId: String, connection: Connection): Effect[Unit] =
    if (active.get) {
      val pool = pools(peerId)
      addConnection(pool, connection)
    } else {
      system.failed(new IllegalStateException(closedMessage))
    }

  def init(): Effect[Unit] = {
    active.set(true)
    system.successful {}
  }

  def close(): Effect[Unit] = {
    active.set(false)
    val connections = pools.values.flatMap(pool =>
      pool.synchronized {
        pool.unusedConnections.dropWhile(_ => true)
      }
    ).toSeq
    connections.foldLeft(effectSystem.successful {}) { case (effect, connection) =>
      effect.flatMap(_ => closeConnection(connection).either.map(_ => ()))
    }
  }

  private def addConnection(pool: Pool[Effect, Connection], connection: Connection): Effect[Unit] = {
    val action = pool.synchronized {
      pool.pendingUsages.removeHeadOption().map(ServeUsage.apply).getOrElse {
        pool.unusedConnections.addOne(connection)
        AddConnection[Effect, Connection]()
      }
    }
    action match {
      case ServeUsage(usage) => usage.succeed(connection)
      case AddConnection() => effectSystem.successful {}
    }
  }

  private def provideConnection(
    pool: Pool[Effect, Connection],
    context: Endpoint,
    action: ProvideAction[Effect, Endpoint, Connection],
  ): Effect[Connection] =
    action match {
      case OpenConnection(open) =>
        logger.trace(s"Opening ${protocol.name} connection")
        open(context).flatFold(
          error => {
            pool.synchronized {
              pool.managedConnections -= 1
            }
            logger.error(s"Failed to open ${protocol.name} connection")
            effectSystem.failed(error)
          },
          connection => {
            logger.debug(s"Opened ${protocol.name} connection")
            effectSystem.successful(connection)
          },
        )
      case EnqueueUsage() =>
        effectSystem.completable[Connection].flatMap { usage =>
          pool.pendingUsages.addOne(usage)
          usage.effect
        }
      case UseConnection(connection) => system.successful(connection)
    }
}

private[automorph] object ConnectionPool {

  sealed trait ProvideAction[Effect[_], Context, Connection]

  sealed trait AddAction[Effect[_], Connection]

  final case class Pool[Effect[_], Connection](
    pendingUsages: mutable.Queue[Completable[Effect, Connection]] =
      mutable.Queue.empty[Completable[Effect, Connection]],
    unusedConnections: mutable.Stack[Connection] = mutable.Stack.empty[Connection],
    var managedConnections: Int = 0,
  )

  final case class UseConnection[Effect[_], Context, Connection](connection: Connection)
    extends ProvideAction[Effect, Context, Connection]

  final case class OpenConnection[Effect[_], Context, Connection](open: Context => Effect[Connection])
    extends ProvideAction[Effect, Context, Connection]
  final case class EnqueueUsage[Effect[_], Context, Connection]() extends ProvideAction[Effect, Context, Connection]

  final case class ServeUsage[Effect[_], Connection](usage: Completable[Effect, Connection])
    extends AddAction[Effect, Connection]
  final case class AddConnection[Effect[_], Connection]() extends AddAction[Effect, Connection]
}
