package automorph

import automorph.meta.DefaultRpcProtocol
import automorph.spi.AsyncEffectSystem
import automorph.system.FutureSystem
import automorph.transport.http.client.HttpClient
import automorph.transport.http.endpoint.UndertowHttpEndpoint
import automorph.transport.http.server.UndertowServer
import automorph.transport.http.server.UndertowServer.builder
import automorph.transport.http.{HttpContext, HttpMethod}
import io.undertow.Undertow
import java.net.URI
import scala.concurrent.{ExecutionContext, Future}

/** Default component constructors. */
object Default extends DefaultRpcProtocol with DefaultTransport {

  /** Default effect type. */
  type Effect[T] = Future[T]

  /**
   * Creates a standard JRE JSON-RPC over HTTP & WebSocket client using default RPC protocol with 'Future' as an effect
   * type.
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
   * @param url
   *   HTTP endpoint URL
   * @param method
   *   HTTP request method
   * @param executionContext
   *   execution context
   * @return
   *   asynchronous RPC client
   */
  def rpcClient(url: URI, method: HttpMethod = HttpMethod.Post)(implicit
    executionContext: ExecutionContext
  ): RpcClient[Node, Codec, Effect, ClientContext] =
    rpcClientCustom(effectSystem, url, method)

  /**
   * Creates a standard JRE HTTP & WebSocket client transport plugin with 'Future' as an effect type.
   *
   * @see
   *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
   * @see
   *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
   * @see
   *   [[https://openjdk.org/groups/net/httpclient/intro.html documentation]]
   * @see
   *   [[https://docs.oracle.com/en/java/javase/19/docs/api/java.net.http/java/net/http/HttpClient.html API]]
   * @param url
   *   HTTP endpoint URL
   * @param method
   *   HTTP request method
   * @param executionContext
   *   execution context
   * @return
   *   client transport plugin
   */
  def clientTransport(url: URI, method: HttpMethod = HttpMethod.Post)(implicit
    executionContext: ExecutionContext
  ): HttpClient[Effect] =
    clientTransportCustom(Default.effectSystem, url, method)

  /**
   * Creates an Undertow JSON-RPC server over HTTP & WebSocket using default RPC protocol with 'Future' as an effect
   * type.
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
   * @param executionContext
   *   execution context
   * @return
   *   asynchronous RPC server
   */
  def rpcServer(
    port: Int,
    path: String = "/",
    methods: Iterable[HttpMethod] = HttpMethod.values,
    webSocket: Boolean = true,
    mapException: Throwable => Int = HttpContext.toStatusCode,
    builder: Undertow.Builder = builder,
  )(implicit executionContext: ExecutionContext): RpcServer[Node, Codec, Effect, ServerContext] =
    rpcServerCustom(effectSystem, port, path, methods, webSocket, mapException, builder)

  /**
   * Creates an Undertow RPC over HTTP & WebSocket server transport plugin with 'Future' as an effect type.
   *
   * @see
   *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
   * @see
   *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
   * @see
   *   [[https://undertow.io Library documentation]]
   * @see
   *   [[https://www.javadoc.io/doc/io.undertow/undertow-core/latest/index.html API]]
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
   * @param executionContext
   *   execution context
   * @return
   *   server transport plugin
   */
  def serverTransport(
    port: Int,
    path: String = "/",
    methods: Iterable[HttpMethod] = HttpMethod.values,
    webSocket: Boolean = true,
    mapException: Throwable => Int = HttpContext.toStatusCode,
    builder: Undertow.Builder = builder,
  )(implicit
    executionContext: ExecutionContext
  ): UndertowServer[Effect] =
    serverTransportCustom(effectSystem, port, path, methods, webSocket, mapException, builder)

  /**
   * Creates an Undertow RPC over HTTP endpoint using default RPC protocol with 'Future' as an effect type.
   *
   * The endpoint can be integrated into an existing server to receive remote API requests using specific transport
   * protocol and invoke bound API methods to process them.
   *
   * @see
   *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
   * @see
   *   [[https://undertow.io Library documentation]]
   * @see
   *   [[https://www.javadoc.io/doc/io.undertow/undertow-core/latest/index.html API]]
   * @param mapException
   *   maps an exception to a corresponding HTTP status code
   * @param executionContext
   *   execution context
   * @return
   *   RPC endpoint
   */
  def rpcEndpoint(mapException: Throwable => Int = HttpContext.toStatusCode)(implicit
    executionContext: ExecutionContext
  ): RpcEndpoint[Node, Codec, Effect, ServerContext, Adapter] =
    rpcEndpointCustom(effectSystem, mapException)

  /**
   * Creates an Undertow RPC over HTTP & WebSocket endpoint transport plugin with 'Future' as an effect type.
   *
   * @see
   *   [[https://en.wikipedia.org/wiki/HTTP Transport protocol]]
   * @see
   *   [[https://en.wikipedia.org/wiki/WebSocket Alternative transport protocol]]
   * @see
   *   [[https://undertow.io Library documentation]]
   * @see
   *   [[https://www.javadoc.io/doc/io.undertow/undertow-core/latest/index.html API]]
   * @param mapException
   *   maps an exception to a corresponding HTTP status code
   * @param executionContext
   *   execution context
   * @return
   *   endpoint transport plugin
   */
  def endpointTransport(mapException: Throwable => Int = HttpContext.toStatusCode)(implicit
    executionContext: ExecutionContext
  ): UndertowHttpEndpoint[Effect] =
    endpointTransportCustom(effectSystem, mapException)

  /**
   * Creates an asynchronous effect system plugin with 'Future' as an effect type.
   *
   * @see
   *   [[https://docs.scala-lang.org/overviews/core/futures.html Library documentation]]
   * @see
   *   [[https://scala-lang.org/api/3.x/scala/concurrent/Future.html Effect type]]
   * @return
   *   asynchronous effect system plugin
   */
  def effectSystem(implicit executionContext: ExecutionContext): AsyncEffectSystem[Effect] =
    FutureSystem()
}
