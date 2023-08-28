package test.transport.local

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.local.endpoint.LocalEndpoint

final case class LocalServer[Effect[_], Context](
  effectSystem: EffectSystem[Effect],
) extends ServerTransport[Effect, Context] {
  private var endpoint: LocalEndpoint[Effect, Context] = LocalEndpoint(effectSystem)

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
