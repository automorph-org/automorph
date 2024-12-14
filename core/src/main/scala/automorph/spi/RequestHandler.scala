package automorph.spi

import automorph.RpcCall
import automorph.spi.RequestHandler.Result

/**
 * RPC request handler.
 *
 * Processes remote API requests and invoke bound API methods.
 *
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
trait RequestHandler[Effect[_], Context] {

  /**
   * Processes an RPC request by invoking a bound remote function based on the specified RPC request along with request
   * context and return an RPC response.
   *
   * @param requestBody
   *   request message body
   * @param context
   *   request context
   * @param id
   *   request correlation identifier included in logs associated with the request
   * @return
   *   request processing result
   */
  def processRequest(requestBody: Array[Byte], context: Context, id: String): Effect[Option[Result[Context]]]

  /**
   * Enable or disable automatic provision of service discovery via RPC functions returning bound API schema.
   *
   * @param discovery
   *   service discovery enabled
   * @return
   *   RPC request handler
   */
  def discovery(discovery: Boolean): RequestHandler[Effect, Context]

  /**
   * Register filtering function to reject RPC requests based on specified criteria.
   *
   * @param filter
   *   filters RPC calls and raises arbitrary errors for non-matching requests
   * @return
   *   RPC request handler
   */
  def callFilter(filter: RpcCall[Context] => Option[Throwable]): RequestHandler[Effect, Context]

  /**
   * Automatic provision of service discovery via RPC functions returning bound API schema enabled.
   */
  def discovery: Boolean

  /**
   * Filters RPC calls and raises arbitrary errors for non-matching requests
   */
  def filterCall: RpcCall[Context] => Option[Throwable]

  /** Message format media (MIME) type. */
  def mediaType: String
}

object RequestHandler {

  /**
   * RPC handler request processing result.
   *
   * @param responseBody
   *   response message body
   * @param exception
   *   failed call exception
   * @param context
   *   response context
   * @tparam Context
   *   response context type
   */
  final case class Result[Context](
    responseBody: Array[Byte],
    exception: Option[Throwable],
    context: Option[Context],
  )

  final private case class DummyRequestHandler[Effect[_], Context]() extends RequestHandler[Effect, Context] {

    def processRequest(requestBody: Array[Byte], context: Context, id: String): Effect[Option[Result[Context]]] =
      error

    override def discovery(enabled: Boolean): RequestHandler[Effect, Context] =
      error

    override def callFilter(filter: RpcCall[Context] => Option[Throwable]): RequestHandler[Effect, Context] =
      error

    override def discovery: Boolean =
      error

    override def filterCall: RpcCall[Context] => Option[Throwable] =
      error

    override def mediaType: String =
      error

    private def error[T]: T =
      throw new IllegalStateException("RPC request handler not initialized")
  }

  /**
   * Dummy RPC request handler.
   *
   * @tparam Effect
   *   effect type
   * @tparam Context
   *   RPC message context type
   * @return
   *   dummy RPC request handler
   */
  private[automorph] def dummy[Effect[_], Context]: RequestHandler[Effect, Context] =
    DummyRequestHandler()
}
