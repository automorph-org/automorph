package automorph.transport.client

import automorph.spi.{EffectSystem, RpcHandler}
import automorph.transport.client.SttpClient.Context
import automorph.transport.{HttpContext, HttpListen, HttpMethod}
import sttp.capabilities.WebSockets
import sttp.client3.{PartialRequest, SttpBackend}
import java.net.URI
import scala.quoted.{Expr, Quotes, Type}

/**
 * STTP HTTP & WebSocket client message transport plugin.
 *
 * Uses the supplied RPC request as HTTP request body and returns HTTP response body as a result.
 *
 * @see
 *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
 * @see
 *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
 * @see
 *   [[https://sttp.softwaremill.com/en/latest Library documentation]]
 * @see
 *   [[https://javadoc.io/doc/com.softwaremill.sttp.client3/core_3/latest/index.html API]]
 * @constructor
 *   Creates an STTP HTTP & WebSocket client message transport plugin with the specified STTP backend.
 * @param effectSystem
 *   effect system plugin
 * @param backend
 *   STTP backend
 * @param url
 *   remote API HTTP or WebSocket URL
 * @param method
 *   HTTP request method (default: POST)
 * @param listen
 *   listen for RPC requests from the server settings (default: disabled)
 * @param webSocketSupport
 *   specified STTP backend supports WebSocket
 * @param rpcHandler
 *   RPC request handler
 * @tparam Effect
 *   effect type
 */
final case class SttpClient[Effect[_]] private (
  effectSystem: EffectSystem[Effect],
  backend: SttpBackend[Effect, ?],
  url: URI,
  method: HttpMethod,
  listen: HttpListen,
  rpcHandler: RpcHandler[Effect, Context],
  webSocketSupport: Boolean,
) extends SttpClientBase[Effect]:

  override def rpcHandler(handler: RpcHandler[Effect, Context]): SttpClient[Effect] =
    copy(rpcHandler = handler)

object SttpClient:

  /** Request context type. */
  type Context = HttpContext[Transport]

  /**
   * Creates an STTP HTTP client message transport plugin with the specified STTP backend.
   *
   * @param effectSystem
   *   effect system plugin
   * @param backend
   *   STTP backend
   * @param url
   *   remote API HTTP URL
   * @param method
   *   HTTP request method (default: POST)
   * @param listen
   *   number of opened connections reserved for listening to requests from the server
   * @tparam Effect
   *   effect type
   * @tparam Capabilities
   *   STTP backend capabilities
   * @return
   *   STTP HTTP client message transport plugin
   */
  inline def apply[Effect[_], Capabilities](
    effectSystem: EffectSystem[Effect],
    backend: SttpBackend[Effect, Capabilities],
    url: URI,
    method: HttpMethod,
    listen: HttpListen,
  ): SttpClient[Effect] =
    ${ applyMacro[Effect, Capabilities]('effectSystem, 'backend, 'url, 'method, 'listen) }

  private def applyMacro[Effect[_]: Type, Capabilities: Type](
    effectSystem: Expr[EffectSystem[Effect]],
    backend: Expr[SttpBackend[Effect, Capabilities]],
    url: Expr[URI],
    method: Expr[HttpMethod],
    listen: Expr[HttpListen],
  )(using quotes: Quotes): Expr[SttpClient[Effect]] =
    import quotes.reflect.TypeRepr

    val webSocketSupport = if TypeRepr.of[Capabilities] <:< TypeRepr.of[WebSockets] then
      '{ true }
    else
      '{ false }
    '{
      SttpClient(
        ${ effectSystem },
        ${ backend },
        ${ url },
        ${ method },
        ${ listen },
        RpcHandler.dummy[Effect, Context],
        webSocketSupport = ${ webSocketSupport },
      )
    }

  /**
   * Creates an STTP HTTP client message transport plugin with the specified STTP backend
   * and listening for RPC requests from the server disabled.
   *
   * @param effectSystem
   *   effect system plugin
   * @param backend
   *   STTP backend
   * @param url
   *   remote API HTTP URL
   * @param method
   *   HTTP request method (default: POST)
   * @param listen
   *   number of opened connections reserved for listening to requests from the server
   * @tparam Effect
   *   effect type
   * @tparam Capabilities
   *   STTP backend capabilities
   * @return
   *   STTP HTTP client message transport plugin
   */
  inline def apply[Effect[_], Capabilities](
    effectSystem: EffectSystem[Effect],
    backend: SttpBackend[Effect, Capabilities],
    url: URI,
    method: HttpMethod,
  ): SttpClient[Effect] =
    ${ applyListenMacro[Effect, Capabilities]('effectSystem, 'backend, 'url, 'method) }

  private def applyListenMacro[Effect[_]: Type, Capabilities: Type](
    effectSystem: Expr[EffectSystem[Effect]],
    backend: Expr[SttpBackend[Effect, Capabilities]],
    url: Expr[URI],
    method: Expr[HttpMethod],
  )(using quotes: Quotes): Expr[SttpClient[Effect]] =
    import quotes.reflect.TypeRepr

    val webSocketSupport = if TypeRepr.of[Capabilities] <:< TypeRepr.of[WebSockets] then
      '{ true }
    else
      '{ false }
    '{
      SttpClient(
        ${ effectSystem },
        ${ backend },
        ${ url },
        ${ method },
        HttpListen(),
        RpcHandler.dummy[Effect, Context],
        webSocketSupport = ${ webSocketSupport },
      )
    }

  /**
   * Creates an STTP HTTP client message transport plugin with the specified STTP backend using POST HTTP method
   * and listening for RPC requests from the server disabled.
   *
   * @param effectSystem
   *   effect system plugin
   * @param backend
   *   STTP backend
   * @param url
   *   remote API HTTP URL
   * @tparam Effect
   *   effect type
   * @tparam Capabilities
   *   STTP backend capabilities
   * @return
   *   STTP HTTP client message transport plugin using POST HTTP method
   */
  inline def apply[Effect[_], Capabilities](
    effectSystem: EffectSystem[Effect],
    backend: SttpBackend[Effect, Capabilities],
    url: URI,
  ): SttpClient[Effect] =
    ${ applyMethodListenMacro[Effect, Capabilities]('effectSystem, 'backend, 'url) }

  private def applyMethodListenMacro[Effect[_]: Type, Capabilities: Type](
    effectSystem: Expr[EffectSystem[Effect]],
    backend: Expr[SttpBackend[Effect, Capabilities]],
    url: Expr[URI],
  )(using quotes: Quotes): Expr[SttpClient[Effect]] =
    import quotes.reflect.TypeRepr

    val webSocketSupport = if TypeRepr.of[Capabilities] <:< TypeRepr.of[WebSockets] then
      '{ true }
    else
      '{ false }
    '{
      SttpClient(
        ${ effectSystem },
        ${ backend },
        ${ url },
        HttpMethod.Post,
        HttpListen(),
        RpcHandler.dummy[Effect, Context],
        webSocketSupport = ${ webSocketSupport },
      )
    }

  /** Transport-specific context. */
  final case class Transport(request: PartialRequest[Either[String, String], Any])

  object Transport:

    /** Implicit default context value. */
    implicit val context: HttpContext[Transport] = HttpContext()
