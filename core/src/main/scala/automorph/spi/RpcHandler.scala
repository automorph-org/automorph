package automorph.spi

import automorph.spi.RpcHandler.Result

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
trait RpcHandler[Effect[_], Context] {

  /**
   * Processes an RPC request by invoking a bound remote function based on the specified RPC request along with request
   * context and return an RPC response.
   *
   * @param body
   *   request message body
   * @param context
   *   request context
   * @param id
   *   request correlation identifier included in logs associated with the request
   * @return
   *   request processing result
   */
  def processRequest(body: Array[Byte], context: Context, id: String): Effect[Option[Result[Context]]]

  /**
   * Enable or disable automatic provision of service discovery via RPC functions returning bound API schema.
   *
   * @param discovery
   *   service discovery enabled
   * @return
   *   RPC request handler
   */
  def discovery(discovery: Boolean): RpcHandler[Effect, Context]

  /**
   * * Automatic provision of service discovery via RPC functions returning bound API schema.
   */
  def discovery: Boolean

  /** Message format media (MIME) type. */
  def mediaType: String
}

object RpcHandler {

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

  final private case class DummyRpcHandler[Effect[_], Context]() extends RpcHandler[Effect, Context] {

    def processRequest(body: Array[Byte], context: Context, id: String): Effect[Option[Result[Context]]] =
      error

    override def discovery(enabled: Boolean): RpcHandler[Effect, Context] =
      error

    /** Automatic provision of service discovery via RPC functions returning bound API schema. */
    override def discovery: Boolean =
      error

    /** Message format media (MIME) type. */
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
  def dummy[Effect[_], Context]: RpcHandler[Effect, Context] =
    DummyRpcHandler()
}
