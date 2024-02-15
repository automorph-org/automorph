package test.core

import automorph.{RpcClient, RpcServer}
import automorph.schema.OpenApi
import automorph.spi.MessageCodec
import test.api.{ComplexApi, InvalidApi, SimpleApi}

object Fixtures {

  final case class Apis[Effect[_], Context](
    simpleApi: SimpleApi[Effect],
    complexApi: ComplexApi[Effect, Context],
    invalidApi: InvalidApi[Effect],
  )

  final case class Functions[Effect[_]](
    callOpenApi: String => Effect[OpenApi],
    callString: (String, (String, String)) => Effect[String],
    tell: (String, (String, String)) => Effect[Unit],
  )

  final case class Fixture[Effect[_], Context](
    id: String,
    client: RpcClient[?, ?, Effect, Context],
    server: RpcServer[?, ?, Effect, Context],
    apis: Apis[Effect, Context],
    functions: Functions[Effect],
  ) {
    private type GenericServer[E[_], C] = RpcServer[Any, MessageCodec[Any], E, C]
    private type GenericClient[E[_], C] = RpcClient[Any, MessageCodec[Any], E, C]

    val genericClient: GenericClient[Effect, Context] = client.asInstanceOf[GenericClient[Effect, Context]]
    val genericServer: GenericServer[Effect, Context] = server.asInstanceOf[GenericServer[Effect, Context]]
  }
}
