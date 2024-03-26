package test.transport

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.server.GenericEndpoint

final case class LocalServer[Effect[_], Context](
  effectSystem: EffectSystem[Effect]
) extends ServerTransport[Effect, Context, Unit] {
  private var genericEndpoint: GenericEndpoint[Effect, Context] = GenericEndpoint(effectSystem)

  def handler: RequestHandler[Effect, Context] =
    genericEndpoint.handler

  override def endpoint: Unit =
    ()

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}

  override def requestHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context, Unit] = {
    genericEndpoint = genericEndpoint.requestHandler(handler)
    this
  }
}
