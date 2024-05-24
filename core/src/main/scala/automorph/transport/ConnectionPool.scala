package automorph.transport

import automorph.log.Logger
import automorph.spi.EffectSystem
import automorph.spi.EffectSystem.Completable
import automorph.transport.ConnectionPool.{
  Action, AddConnection, EnqueueUsage, OpenConnection, ReportError, ServeUsage, UseConnection,
}
import automorph.util.Extensions.EffectOps
import scala.collection.mutable

final private[automorph] case class ConnectionPool[Effect[_], Connection](
  openConnection: Option[() => Effect[Connection]],
  closeConnection: Connection => Effect[Unit],
  maxConnectionsPerTarget: Option[Int],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  logger: Logger,
) {
  private val pendingUsages =
    mutable.HashMap[String, mutable.Queue[Completable[Effect, Connection]]]().withDefaultValue(mutable.Queue.empty)
  private val unusedConnections =
    mutable.HashMap[String, mutable.Stack[Connection]]().withDefaultValue(mutable.Stack.empty)
  private val managedConnections = mutable.HashMap[String, Int]().withDefaultValue(0)
  private val closedMessage = "Connection pool is closed"
  implicit private val system: EffectSystem[Effect] = effectSystem
  private var active = false

  def using[T](target: String, use: Connection => Effect[T]): Effect[T] = {
    val action = this.synchronized {
      if (active) {
        unusedConnections(target).removeHeadOption().map(UseConnection.apply[Effect, Connection]).getOrElse {
          lazy val targetManagedConnections = managedConnections(target)
          lazy val newConnectionAllowed = maxConnectionsPerTarget.forall(targetManagedConnections < _)
          openConnection.filter(_ => newConnectionAllowed).map { open =>
            managedConnections.put(target, targetManagedConnections + 1)
            OpenConnection(open)
          }.getOrElse(EnqueueUsage[Effect, Connection]())
        }
      } else {
        ReportError[Effect, Connection]()
      }
    }
    provideConnection(target, action).flatMap { connection =>
      use(connection).flatFold(
        error => add(target, connection).flatMap(_ => effectSystem.failed(error)),
        result => add(target, connection).map(_ => result),
      )
    }
  }

  def add(target: String, connection: Connection): Effect[Unit] = {
    val action = this.synchronized {
      if (active) {
        pendingUsages(target).removeHeadOption().map(ServeUsage.apply).getOrElse {
          unusedConnections(target).addOne(connection)
          AddConnection[Effect, Connection]()
        }
      } else {
        ReportError[Effect, Connection]()
      }
    }
    action match {
      case ServeUsage(usage) => usage.succeed(connection)
      case AddConnection() => effectSystem.successful {}
      case _ => system.failed(new IllegalStateException(closedMessage))
    }
  }

  def init(): Effect[ConnectionPool[Effect, Connection]] = {
    this.synchronized {
      active = true
    }
    system.successful(this)
  }

  def close(): Effect[ConnectionPool[Effect, Connection]] = {
    val connections = this.synchronized {
      active = false
      unusedConnections.values.flatMap(_.dropWhile(_ => true))
    }
    connections.foldLeft(effectSystem.successful {}) { case (effect, connection) =>
      effect.flatMap(_ => closeConnection(connection).either.map(_ => ()))
    }.map(_ => this)
  }

  private def provideConnection(target: String, action: Action[Effect, Connection]): Effect[Connection] =
    action match {
      case OpenConnection(open) =>
        logger.trace(s"Opening ${protocol.name} connection")
        open().flatFold(
          error => {
            this.synchronized {
              managedConnections.put(target, managedConnections(target) - 1)
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
          pendingUsages(target).addOne(usage)
          usage.effect
        }
      case UseConnection(connection) => system.successful(connection)
      case _ => system.failed(new IllegalStateException(closedMessage))
    }
}

private[automorph] object ConnectionPool {
  sealed trait Action[Effect[_], Connection]

  final case class UseConnection[Effect[_], Connection](connection: Connection) extends Action[Effect, Connection]

  final case class OpenConnection[Effect[_], Connection](open: () => Effect[Connection])
    extends Action[Effect, Connection]
  final case class EnqueueUsage[Effect[_], Connection]() extends Action[Effect, Connection]

  final case class ServeUsage[Effect[_], Connection](usage: Completable[Effect, Connection])
    extends Action[Effect, Connection]
  final case class AddConnection[Effect[_], Connection]() extends Action[Effect, Connection]
  final case class ReportError[Effect[_], Connection]() extends Action[Effect, Connection]
}
