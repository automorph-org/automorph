package automorph.transport.client

import automorph.spi.EffectSystem
import automorph.transport.{HttpContext, HttpListen, HttpMethod}
import sttp.capabilities.WebSockets
import sttp.client3.{PartialRequest, SttpBackend}
import java.net.URI
import scala.annotation.nowarn
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

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
 *   HTTP request method
 * @param listen
 *   listen for server requests settings
 * @param webSocketSupport
 *   specified STTP backend supports WebSocket
 * @tparam Effect
 *   effect type
 */
@nowarn("msg=copied from the case class constructor")
final case class SttpClient[Effect[_]] private (
  effectSystem: EffectSystem[Effect],
  backend: SttpBackend[Effect, ?],
  url: URI,
  method: HttpMethod,
  listen: HttpListen,
  webSocketSupport: Boolean,
) extends SttpClientBase[Effect] {}

object SttpClient {

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
  def apply[Effect[_], Capabilities](
    effectSystem: EffectSystem[Effect],
    backend: SttpBackend[Effect, Capabilities],
    url: URI,
    method: HttpMethod,
    listen: HttpListen,
  ): SttpClient[Effect] =
    macro applyMacro[Effect, Capabilities]

  def applyMacro[Effect[_], Capabilities: c.WeakTypeTag](c: blackbox.Context)(
    effectSystem: c.Expr[EffectSystem[Effect]],
    backend: c.Expr[SttpBackend[Effect, Capabilities]],
    url: c.Expr[URI],
    method: c.Expr[HttpMethod],
    listen: c.Expr[HttpListen],
  ): c.Expr[SttpClient[Effect]] = {
    import c.universe.{Quasiquote, weakTypeOf}

    val webSocketSupport = c.Expr[Boolean](if (weakTypeOf[Capabilities] <:< weakTypeOf[WebSockets]) {
      q"""true"""
    } else {
      q"""false"""
    })
    c.Expr[SttpClient[Effect]](q"""
      automorph.transport.client.SttpClient.create(
        $effectSystem, $backend, $url, $method, $listen, webSocketSupport = $webSocketSupport
      )
    """)
  }

  /**
   * Creates an STTP HTTP client message transport plugin with the specified STTP backend and no listen connections.
   *
   * @param effectSystem
   *   effect system plugin
   * @param backend
   *   STTP backend
   * @param url
   *   remote API HTTP URL
   * @param method
   *   HTTP request method (default: POST)
   * @tparam Effect
   *   effect type
   * @tparam Capabilities
   *   STTP backend capabilities
   * @return
   *   STTP HTTP client message transport plugin
   */
  def apply[Effect[_], Capabilities](
    effectSystem: EffectSystem[Effect],
    backend: SttpBackend[Effect, Capabilities],
    url: URI,
    method: HttpMethod = HttpMethod.Post,
  ): SttpClient[Effect] =
  macro applyListenMacro[Effect, Capabilities]

  def applyListenMacro[Effect[_], Capabilities: c.WeakTypeTag](c: blackbox.Context)(
    effectSystem: c.Expr[EffectSystem[Effect]],
    backend: c.Expr[SttpBackend[Effect, Capabilities]],
    url: c.Expr[URI],
    method: c.Expr[HttpMethod],
  ): c.Expr[SttpClient[Effect]] = {
    import c.universe.{Quasiquote, weakTypeOf}

    val webSocketSupport = c.Expr[Boolean](if (weakTypeOf[Capabilities] <:< weakTypeOf[WebSockets]) {
      q"""true"""
    } else {
      q"""false"""
    })
    c.Expr[SttpClient[Effect]](q"""
      automorph.transport.client.SttpClient.create(
        $effectSystem, $backend, $url, $method, automorph.transport.HttpListen(), webSocketSupport = $webSocketSupport
      )
    """)
  }

  /**
   * Creates an STTP HTTP client message transport plugin with the specified STTP backend using POST HTTP method
   * and no listen connections.
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
  def apply[Effect[_], Capabilities](
    effectSystem: EffectSystem[Effect],
    backend: SttpBackend[Effect, Capabilities],
    url: URI,
  ): SttpClient[Effect] =
    macro applyMethodListenMacro[Effect, Capabilities]

  def applyMethodListenMacro[Effect[_], Capabilities: c.WeakTypeTag](c: blackbox.Context)(
    effectSystem: c.Expr[EffectSystem[Effect]],
    backend: c.Expr[SttpBackend[Effect, Capabilities]],
    url: c.Expr[URI],
  ): c.Expr[SttpClient[Effect]] = {
    import c.universe.{Quasiquote, weakTypeOf}

    val webSocketSupport = c.Expr[Boolean](if (weakTypeOf[Capabilities] <:< weakTypeOf[WebSockets]) {
      q"""true"""
    } else {
      q"""false"""
    })
    c.Expr[SttpClient[Effect]](q"""
      import automorph.transport.HttpMethod
      automorph.transport.client.SttpClient.create(
        $effectSystem, $backend, $url, HttpMethod.Post,
        automorph.transport.HttpListen(), webSocketSupport = $webSocketSupport
      )
    """)
  }

  /** This method must never be used and should be considered private. */
  def create[Effect[_]](
    effectSystem: EffectSystem[Effect],
    backend: SttpBackend[Effect, ?],
    url: URI,
    method: HttpMethod,
    listen: HttpListen = HttpListen(),
    webSocketSupport: Boolean,
  ): SttpClient[Effect] =
    SttpClient(effectSystem, backend, url, method, listen, webSocketSupport)

  /** Transport-specific context. */
  final case class Transport(request: PartialRequest[Either[String, String], Any])

  object Transport {

    /** Implicit default context value. */
    implicit val context: HttpContext[Transport] = HttpContext()
  }
}
