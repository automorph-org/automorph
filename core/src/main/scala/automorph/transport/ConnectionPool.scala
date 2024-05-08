package automorph.transport

import automorph.log.Logger
import automorph.spi.EffectSystem
import automorph.spi.EffectSystem.Completable
import automorph.transport.ConnectionPool.{
  Action, Closed, FreeConnection, NoConnection, NoRequest, OpenConnection, PendingRequest,
}
import automorph.util.Extensions.EffectOps
import scala.collection.mutable

final private[automorph] case class ConnectionPool[Effect[_], Connection](
  private val openConnection: Option[() => Effect[Connection]],
  private val closeConnection: Connection => Effect[Unit],
  maxConnections: Int,
  protocol: Protocol,
  effectSystem: EffectSystem[Effect],
  logger: Logger,
) {
  private val pendingRequests = mutable.Queue.empty[Completable[Effect, Connection]]
  private val unusedConnections = mutable.HashSet.empty[Connection]
  private var active = false
  private var managedConnections = 0
  implicit private val system: EffectSystem[Effect] = effectSystem

  def usingConnection[T](function: Connection => Effect[T]): Effect[T] = {
    val action = this.synchronized {
      if (active) {
        unusedConnections.drop(1).headOption.map(FreeConnection.apply[Effect, Connection]).getOrElse {
          openConnection.filter(_ => managedConnections < maxConnections).map { open =>
            managedConnections += 1
            OpenConnection(open)
          }.getOrElse(NoConnection[Effect, Connection]())
        }
      } else {
        Closed[Effect, Connection]()
      }
    }
    provideConnection(action).flatMap { connection =>
      function(connection).either.flatMap {
        case Left(error) => addConnection(connection).flatMap(_ => effectSystem.failed(error))
        case Right(result) => addConnection(connection).map(_ => result)
      }
    }
  }

  def addConnection(connection: Connection): Effect[Unit] = {
    val action = this.synchronized {
      if (active) {
        pendingRequests.removeHeadOption().map(PendingRequest(_)).getOrElse {
          unusedConnections.add(connection)
          NoRequest[Effect, Connection]()
        }
      } else {
        Closed[Effect, Connection]()
      }
    }
    action match {
      case PendingRequest(request) => request.succeed(connection)
      case NoRequest() => effectSystem.successful {}
      case _ => closeConnection(connection)
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
      unusedConnections.dropWhile(_ => true)
    }
    connections.foldLeft(effectSystem.successful {}) { case (effect, connection) =>
      effect.flatMap(_ => closeConnection(connection).either.map(_ => ()))
    }.map(_ => this)
  }

  private def provideConnection(action: Action[Effect, Connection]): Effect[Connection] =
    action match {
      case OpenConnection(open) =>
        logger.trace(s"Opening ${protocol.name} connection")
        open().either.flatMap {
          case Left(error) =>
            this.synchronized(managedConnections -= 1)
            logger.error(s"Failed to open ${protocol.name} connection")
            effectSystem.failed(error)
          case Right(connection) =>
            logger.debug(s"Opened ${protocol.name} connection")
            effectSystem.successful(connection)
        }
      case NoConnection() =>
        effectSystem.completable[Connection].flatMap { request =>
          pendingRequests.addOne(request)
          request.effect
        }
      case FreeConnection(connection) => system.successful(connection)
      case _ => system.failed(new IllegalStateException("Connection pool is closed"))
    }
}

private[automorph] object ConnectionPool {
  sealed trait Action[Effect[_], Connection]

  final case class FreeConnection[Effect[_], Connection](connection: Connection) extends Action[Effect, Connection]

  final case class OpenConnection[Effect[_], Connection](open: () => Effect[Connection])
    extends Action[Effect, Connection]
  final case class NoConnection[Effect[_], Connection]() extends Action[Effect, Connection]

  final case class PendingRequest[Effect[_], Connection](request: Completable[Effect, Connection])
    extends Action[Effect, Connection]
  final case class NoRequest[Effect[_], Connection]() extends Action[Effect, Connection]
  final case class Closed[Effect[_], Connection]() extends Action[Effect, Connection]
}
