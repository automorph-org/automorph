package automorph.transport

import automorph.log.Logging
import automorph.spi.EffectSystem
import automorph.spi.EffectSystem.Completable
import automorph.transport.ConnectionPool.{Action, NoConnection, EnqueueUse, OpenConnection, Pool, UseConnection}
import automorph.util.Extensions.EffectOps
import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

/**
 * Connection pool.
 *
 * @constructor
 *   Creates a connection pool.
 * @param openConnection
 *   function to open a new connection
 * @param closeConnection
 *   function to close a connection
 * @param protocol
 *   transport protocol
 * @param effectSystem
 *   effect system
 * @param maxPeerConnections
 *   maximum number of connections per peer
 * @tparam Effect
 *   effect type
 * @tparam Endpoint
 *   connection endpoint type
 * @tparam Connection
 *   connection type
 */
final private[automorph] case class ConnectionPool[Effect[_], Endpoint, Connection](
  openConnection: Option[(Endpoint, Int) => Effect[Connection]],
  closeConnection: Connection => Effect[Unit],
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  maxPeerConnections: Option[Int] = None,
) extends Logging {
  private val pools = TrieMap[String, Pool[Effect, Connection]]().withDefaultValue(Pool())
  private val active = new AtomicBoolean(false)
  private val closedMessage = "Connection pool is closed"
  implicit private val system: EffectSystem[Effect] = effectSystem

  /**
   * Performs an operation using a connection acquired from this connection pool when it becomes available
   * and then return the connection back to the pool.
   *
   * @param peer
   *   connected peer identifier
   * @param endpoint
   *   connected endpoint
   * @param use
   *   function using the connection
   * @tparam T
   *   connection use function result type
   * @return
   *   connection use result
   */
  def using[T](peer: String, endpoint: Endpoint, use: Connection => Effect[T]): Effect[T] =
    acquire(peer, endpoint).flatMap { case (connection, connectionId) =>
      val pool = pools(peer)
      use(connection).flatFold(
        error => addConnection(pool, connection, connectionId).flatMap(_ => system.failed(error)),
        result => addConnection(pool, connection, connectionId).map(_ => result),
      )
    }

  /**
   * Acquire a connection from this connection pool when it becomes available.
   *
   * @param peer
   *   connected peer identifier
   * @param endpoint
   *   connected endpoint
   * @return
   *   connection & connection identifier
   */
  def acquire(peer: String, endpoint: Endpoint): Effect[(Connection, Int)] =
    if (active.get) {
      val pool = pools(peer)
      val action = pool.synchronized {
        pool.unusedConnections.removeHeadOption().map { case (connection, connectionId) =>
          UseConnection[Effect, Endpoint, Connection](connection, connectionId)
        }.getOrElse {
          openConnection match {
            case Some(open) if maxPeerConnections.forall(pool.managedConnections < _) =>
              pool.managedConnections += 1
              OpenConnection(open)
            case Some(_) => EnqueueUse[Effect, Endpoint, Connection]()
            case _ => NoConnection[Effect, Endpoint, Connection](peer)
          }
        }
      }
      provideConnection(pool, endpoint, action)
    } else {
      system.failed(new IllegalStateException(closedMessage))
    }

  /**
   * Adds a connection to this connection pool.
   *
   * @param peer
   *   connected peer identifier
   * @param connection
   *   connection
   * @return
   *   nothing
   */
  def add(peer: String, connection: Connection): Effect[Unit] =
    if (active.get) {
      val pool = pools(peer)
      addConnection(pool, connection, pool.nextId.getAndAdd(1))
    } else {
      system.failed(new IllegalStateException(closedMessage))
    }

  /**
   * Removes a connection from the connection pool.
   *
   * @param peer
   *   connected peer identifier
   * @param connectionId
   *   connection identifier
   */
  def remove(peer: String, connectionId: Int): Unit = {
    if (!active.get) {
      throw new IllegalStateException(closedMessage)
    }
    val pool = pools(peer)
    pool.synchronized {
      pool.unusedConnections.removeFirst(_._2 == connectionId).map(_ => ()).getOrElse {
        pool.removedIds += connectionId
        ()
      }
    }
  }

  /**
   * Initialize this connection pool.
   *
   * @return
   *   nothing
   */
  def init(): Effect[Unit] =
    system.evaluate {
      active.set(true)
    }

  /**
   * Close this connection pool.
   *
   * @return
   *   nothing
   */
  def close(): Effect[Unit] =
    system.evaluate {
      active.set(false)
      pools.values.flatMap(pool =>
        pool.synchronized {
          pool.managedConnections = 0
          pool.nextId.set(0)
          pool.removedIds.clear()
          pool.unusedConnections.dropWhile(_ => true)
        }
      ).toSeq
    }.flatMap { connections =>
      connections.foldLeft(system.successful {}) { case (effect, (connection, _)) =>
        effect.flatMap(_ => closeConnection(connection).either.map(_ => ()))
      }
    }

  private def addConnection(pool: Pool[Effect, Connection], connection: Connection, connectionId: Int): Effect[Unit] =
    if (active.get) {
      system.evaluate {
        pool.synchronized {
          if (pool.removedIds.contains(connectionId)) {
            pool.removedIds -= connectionId
            ()
          } else {
            pool.pendingUses.removeHeadOption().map(_ => ()).getOrElse {
              pool.unusedConnections.addOne(connection -> connectionId)
              ()
            }
          }
        }
      }
    } else {
      closeConnection(connection)
    }

  private def provideConnection(
    pool: Pool[Effect, Connection],
    endpoint: Endpoint,
    action: Action[Effect, Endpoint, Connection],
  ): Effect[(Connection, Int)] =
    action match {
      case OpenConnection(open) =>
        logger.trace(s"Opening ${protocol.name} connection")
        val connectionId = pool.nextId.getAndAdd(1)
        open(endpoint, connectionId).flatFold(
          error => {
            pool.synchronized {
              pool.managedConnections -= 1
            }
            logger.error(s"Failed to open ${protocol.name} connection")
            system.failed(error)
          },
          connection => {
            logger.debug(s"Opened ${protocol.name} connection")
            system.successful(connection -> connectionId)
          },
        )
      case EnqueueUse() =>
        system.completable[(Connection, Int)].flatMap { use =>
          pool.pendingUses.addOne(use)
          use.effect
        }
      case NoConnection(peer) => system.failed(new IllegalStateException(s"No connection for: $peer"))
      case UseConnection(connection, connectionId) => system.successful(connection -> connectionId)
    }
}

private[automorph] object ConnectionPool {

  sealed trait Action[Effect[_], Context, Connection]

  final case class Pool[Effect[_], Connection](
    pendingUses: mutable.Queue[Completable[Effect, (Connection, Int)]] =
      mutable.Queue.empty[Completable[Effect, (Connection, Int)]],
    unusedConnections: mutable.Stack[(Connection, Int)] = mutable.Stack.empty[(Connection, Int)],
    nextId: AtomicInteger = new AtomicInteger(0),
    removedIds: mutable.HashSet[Int] = new mutable.HashSet,
    var managedConnections: Int = 0,
  )

  final case class UseConnection[Effect[_], Endpoint, Connection](connection: Connection, connectionId: Int)
    extends Action[Effect, Endpoint, Connection]

  final case class OpenConnection[Effect[_], Endpoint, Connection](open: (Endpoint, Int) => Effect[Connection])
    extends Action[Effect, Endpoint, Connection]

  final case class NoConnection[Effect[_], Endpoint, Connection](peer: String)
    extends Action[Effect, Endpoint, Connection]

  final case class EnqueueUse[Effect[_], Endpoint, Connection]() extends Action[Effect, Endpoint, Connection]
}
