package automorph.transport.http.client

import automorph.spi.EffectSystem
import automorph.transport.http.{HttpContext, HttpMethod}
import sttp.capabilities.WebSockets
import sttp.client3.{PartialRequest, SttpBackend}
import java.net.URI
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
 * @tparam Effect
 *   effect type
 */
final case class SttpClient[Effect[_]] private (
  effectSystem: EffectSystem[Effect],
  backend: SttpBackend[Effect, ?],
  url: URI,
  method: HttpMethod,
  webSocket: Boolean,
) extends SttpClientBase[Effect] {}

object SttpClient {

  /** Request context type. */
  type Context = HttpContext[TransportContext]

  /**
   * Creates an STTP HTTP client message transport plugin with the specified STTP backend.
   *
   * Use the alternative [[SttpClient.webSocket]] function for STTP backends with WebSocket capability.
   *
   * @param effectSystem
   *   effect system plugin
    @param backend
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
    macro applyMacro[Effect, Capabilities]

  def applyMacro[Effect[_], Capabilities: c.WeakTypeTag](c: blackbox.Context)(
    effectSystem: c.Expr[EffectSystem[Effect]],
    backend: c.Expr[SttpBackend[Effect, Capabilities]],
    url: c.Expr[URI],
    method: c.Expr[HttpMethod],
  ): c.Expr[SttpClient[Effect]] = {
    import c.universe.{Quasiquote, weakTypeOf}

    val webSocket = c.Expr[Boolean](if (weakTypeOf[Capabilities] <:< weakTypeOf[WebSockets]) {
      q"""true"""
    } else {
      q"""false"""
    })
    c.Expr[SttpClient[Effect]](q"""
      automorph.transport.http.client.SttpClient.create($effectSystem, $backend, $url, $method, webSocket = $webSocket)
    """)
  }

  /** This method must never be used and should be considered private. */
  def create[Effect[_]](
    effectSystem: EffectSystem[Effect],
    backend: SttpBackend[Effect, ?],
    url: URI,
    method: HttpMethod,
    webSocket: Boolean,
  ): SttpClient[Effect] =
    SttpClient(effectSystem, backend, url, method, webSocket)

  /** Transport context. */
  final case class TransportContext(request: PartialRequest[Either[String, String], Any])

  object TransportContext {

    /** Implicit default context value. */
    implicit val defaultContext: HttpContext[TransportContext] = HttpContext()
  }
}
