package test.transport.local

import automorph.spi.{EffectSystem, RequestHandler, ServerTransport}
import automorph.transport.local.LocalContext
import automorph.transport.local.endpoint.LocalEndpoint
import automorph.transport.local.endpoint.LocalEndpoint.Context

final case class LocalServer[Effect[_]](
  effectSystem: EffectSystem[Effect],
  context: Context = LocalContext.defaultContext,
) extends ServerTransport[Effect, Context] {
  private var endpoint = LocalEndpoint(effectSystem)

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
