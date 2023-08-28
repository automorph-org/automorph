package test.transport.local

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.generic.endpoint.GenericEndpoint

final case class LocalServer[Effect[_], Context](
  effectSystem: EffectSystem[Effect],
) extends ServerTransport[Effect, Context] {
  private var endpoint: GenericEndpoint[Effect, Context] = GenericEndpoint(effectSystem)

  def handler: RequestHandler[Effect, Context] =
    endpoint.handler

  override def withHandler(handler: RequestHandler[Effect, Context]): ServerTransport[Effect, Context] = {
    endpoint = endpoint.withHandler(handler)
    this
  }

  override def init(): Effect[Unit] =
    effectSystem.successful {}

  override def close(): Effect[Unit] =
    effectSystem.successful {}
}
