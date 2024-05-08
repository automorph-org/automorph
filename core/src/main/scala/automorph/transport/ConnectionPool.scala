package automorph.transport

import automorph.log.Logger
import automorph.spi.EffectSystem
import automorph.spi.EffectSystem.Completable
import automorph.transport.ConnectionPool.{
  Action, ReportError, UseConnection, QueueRequest, AddConnection, OpenConnection, ServeRequest,
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
  private val unusedConnections = mutable.Stack.empty[Connection]
  private var active = false
  private var managedConnections = 0
  private val closedMessage = "Connection pool is closed"
  implicit private val system: EffectSystem[Effect] = effectSystem

  def using[T](use: Connection => Effect[T]): Effect[T] = {
    val action = this.synchronized {
      if (active) {
        unusedConnections.removeHeadOption().map(UseConnection.apply[Effect, Connection]).getOrElse {
          openConnection.filter(_ => managedConnections < maxConnections).map { open =>
            managedConnections += 1
            OpenConnection(open)
          }.getOrElse(QueueRequest[Effect, Connection]())
        }
      } else {
        ReportError[Effect, Connection]()
      }
    }
    provideConnection(action).flatMap { connection =>
      use(connection).either.flatMap {
        case Left(error) => add(connection).flatMap(_ => effectSystem.failed(error))
        case Right(result) => add(connection).map(_ => result)
      }
    }
  }

  def add(connection: Connection): Effect[Unit] = {
    val action = this.synchronized {
      if (active) {
        pendingRequests.removeHeadOption().map(ServeRequest(_)).getOrElse {
          unusedConnections.addOne(connection)
          AddConnection[Effect, Connection]()
        }
      } else {
        ReportError[Effect, Connection]()
      }
    }
    action match {
      case ServeRequest(request) => request.succeed(connection)
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
      case QueueRequest() =>
        effectSystem.completable[Connection].flatMap { request =>
          pendingRequests.addOne(request)
          request.effect
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
  final case class QueueRequest[Effect[_], Connection]() extends Action[Effect, Connection]

  final case class ServeRequest[Effect[_], Connection](request: Completable[Effect, Connection])
    extends Action[Effect, Connection]
  final case class AddConnection[Effect[_], Connection]() extends Action[Effect, Connection]
  final case class ReportError[Effect[_], Connection]() extends Action[Effect, Connection]
}
