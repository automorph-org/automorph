package automorph

import automorph.Default.{Codec, Node, rpcProtocol}
import automorph.spi.EffectSystem
import automorph.transport.http.{HttpContext, HttpMethod}
import automorph.transport.http.client.HttpClient
import automorph.transport.http.server.UndertowServer
import automorph.transport.http.server.UndertowServer.defaultBuilder
import io.undertow.Undertow

import java.net.URI

private[automorph] trait DefaultTransport {

  /** Request context type. */
  type ClientContext = HttpClient.Context

  /** Request context type. */
  type ServerContext = UndertowServer.Context

  /**
   * Creates a standard JRE JSON-RPC over HTTP & WebSocket client using default RPC protocol with
   * specified effect system plugin.
   *
   * The client can be used to perform type-safe remote API calls or send one-way messages.
   *
   * @see
   *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
   * @see
   *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
   * @see
   *   [[https://openjdk.org/groups/net/httpclient/intro.html documentation]]
   * @see
   *   [[https://docs.oracle.com/en/java/javase/19/docs/api/java.net.http/java/net/http/HttpClient.html API]]
   * @param effectSystem
   *   effect system plugin
   * @param url
   *   HTTP endpoint URL
   * @param method
   *   HTTP request method
   * @tparam EffectType
   *   effect type
   * @return
   *   RPC client
   */
  def rpcClientCustom[EffectType[_]](
    effectSystem: EffectSystem[EffectType],
    url: URI,
    method: HttpMethod = HttpMethod.Post,
  ): RpcClient[Node, Codec, EffectType, ClientContext] =
    RpcClient(clientTransportCustom(effectSystem, url, method), rpcProtocol)

  /**
   * Creates a standard JRE HTTP & WebSocket client transport protocol plugin with
   * specified effect system plugin.
   *
   * @see
   *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
   * @see
   *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
   * @see
   *   [[https://openjdk.org/groups/net/httpclient/intro.html documentation]]
   * @see
   *   [[https://docs.oracle.com/en/java/javase/19/docs/api/java.net.http/java/net/http/HttpClient.html API]]
   * @param effectSystem
   *   effect system plugin
   * @param url
   *   HTTP endpoint URL
   * @param method
   *   HTTP request method
   * @tparam EffectType
   *   effect type
   * @return
   *   client transport protocol plugin
   */
  def clientTransportCustom[EffectType[_]](
    effectSystem: EffectSystem[EffectType],
    url: URI,
    method: HttpMethod = HttpMethod.Post,
  ): HttpClient[EffectType] =
    HttpClient(effectSystem, url, method)

  /**
   * Creates an Undertow RPC over HTTP & WebSocket server using default RPC protocol with
   * specified effect system plugin.
   *
   * The server can be used to serve remote API requests and invoke bound API methods to process them.
   *
   * @see
   *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
   * @see
   *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
   * @see
   *   [[https://undertow.io Library documentation]]
   * @see
   *   [[https://www.javadoc.io/doc/io.undertow/undertow-core/latest/index.html API]]
   * @param effectSystem
   *   effect system plugin
   * @param port
   *   port to listen on for HTTP connections
   * @param path
   *   HTTP URL path (default: /)
   * @param methods
   *   allowed HTTP request methods (default: any)
   * @param webSocket
   *   both HTTP and WebSocket protocols enabled if true, HTTP only if false
   * @param mapException
   *   maps an exception to a corresponding HTTP status code
   * @param builder
   *   Undertow web server builder
   * @tparam EffectType
   *   effect type
   * @return
   *   RPC server
   */
  def rpcServerCustom[EffectType[_]](
    effectSystem: EffectSystem[EffectType],
    port: Int,
    path: String = "/",
    methods: Iterable[HttpMethod] = HttpMethod.values,
    webSocket: Boolean = true,
    mapException: Throwable => Int = HttpContext.defaultExceptionToStatusCode,
    builder: Undertow.Builder = defaultBuilder,
  ): RpcServer[Node, Codec, EffectType, ServerContext] =
    RpcServer(
      serverTransportCustom(effectSystem, port, path, methods, webSocket, mapException, builder), rpcProtocol
    )

  /**
   * Creates an Undertow RPC over HTTP & WebSocket server transport protocol plugin with
   * specified effect system plugin.
   *
   * @see
   *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
   * @see
   *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
   * @see
   *   [[https://undertow.io Library documentation]]
   * @see
   *   [[https://www.javadoc.io/doc/io.undertow/undertow-core/latest/index.html API]]
   * @param effectSystem
   *   effect system plugin
   * @param port
   *   port to listen on for HTTP connections
   * @param path
   *   HTTP URL path (default: /)
   * @param methods
   *   allowed HTTP request methods (default: any)
   * @param webSocket
   *   both HTTP and WebSocket protocols enabled if true, HTTP only if false
   * @param mapException
   *   maps an exception to a corresponding HTTP status code
   * @param builder
   *   Undertow web server builder
   * @tparam EffectType
   *   effect type
   * @return
   *   server transport protocol plugin
   */
  def serverTransportCustom[EffectType[_]](
    effectSystem: EffectSystem[EffectType],
    port: Int,
    path: String = "/",
    methods: Iterable[HttpMethod] = HttpMethod.values,
    webSocket: Boolean = true,
    mapException: Throwable => Int = HttpContext.defaultExceptionToStatusCode,
    builder: Undertow.Builder = defaultBuilder,
  ): UndertowServer[EffectType] =
    UndertowServer(effectSystem, port, path, methods, webSocket, mapException, builder)
}
