package automorph.spi

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
   * Processes an RPC request by invoking a bound remote function based on the specified RPC request
   * along with request context and return an RPC response.
   *
   * @param requestBody
   *   request message body
   * @param context
   *   request context
   * @param id
   *   request correlation identifier
   * @return
   *   request processing result
   */
  def processRequest(requestBody: Array[Byte], context: Context, id: String): Effect[Option[Result[Context]]]

  /**
   * Enable or disable automatic provision of service discovery via RPC functions returning bound API schema.
   *
   * @param discovery service discovery enabled
   * @return RPC request handler
   */
  def discovery(discovery: Boolean): RequestHandler[Effect, Context]

  /** * Automatic provision of service discovery via RPC functions returning bound API schema. */
  def discovery: Boolean

  /** Message format media (MIME) type. */
  def mediaType: String
}

case object RequestHandler {

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
    new RequestHandler[Effect, Context] {
      def processRequest(requestBody: Array[Byte], context: Context, id: String): Effect[Option[Result[Context]]] =
        throw new IllegalStateException("RPC request handler not initialized")

      override def discovery(enabled: Boolean): RequestHandler[Effect, Context] =
        this

      /** * Automatic provision of service discovery via RPC functions returning bound API schema. */
      override def discovery: Boolean =
        false

      /** Message format media (MIME) type. */
      override def mediaType: String =
        ""
    }
}
