package automorph.spi

import automorph.spi.protocol.{ApiSchema, ParseError, Request, Response}
import scala.util.Try

/**
 * Remote procedure call protocol plugin.
 *
 * Enables use of a specific RPC protocol.
 *
 * The underlying RPC protocol must support remote function invocation.
 *
 * @tparam Value
 *   message codec value representation type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Context
 *   RPC message context type
 */
trait RpcProtocol[Value, Codec <: MessageCodec[Value], Context] {

  /** Protocol-specific RPC message metadata. */
  type Metadata

  /** Message codec plugin. */
  val messageCodec: Codec

  /** Protocol name. */
  def name: String

  /**
   * Creates an RPC request.
   *
   * @param function
   *   invoked function name
   * @param arguments
   *   named arguments
   * @param respond
   *   true if the request mandates a response, false if there should be no response
   * @param context
   *   request context
   * @param id
   *   request correlation identifier
   * @return
   *   RPC request
   */
  def createRequest(
    function: String,
    arguments: Iterable[(String, Value)],
    respond: Boolean,
    context: Context,
    id: String,
  ): Try[Request[Value, Metadata, Context]]

  /**
   * Parses an RPC request.
   *
   * @param body
   *   RPC request message body
   * @param context
   *   request context
   * @param id
   *   request correlation identifier
   * @return
   *   RPC request if the message is valid or RPC error if the message is invalid
   */
  def parseRequest(
    body: Array[Byte],
    context: Context,
    id: String,
  ): Either[ParseError[Metadata], Request[Value, Metadata, Context]]

  /**
   * Creates an RPC response.
   *
   * @param result
   *   RPC response result
   * @param requestMetadata
   *   corresponding RPC request metadata
   * @return
   *   RPC response
   */
  def createResponse(result: Try[Value], requestMetadata: Metadata): Try[Response[Value, Metadata]]

  /**
   * Parses an RPC response.
   *
   * @param body
   *   RPC response message body
   * @param context
   *   response context
   * @param id
   *   request correlation identifier
   * @return
   *   RPC response if the message is valid or RPC error if the message is invalid
   */
  def parseResponse(
    body: Array[Byte],
    context: Context,
    id: String,
  ): Either[ParseError[Metadata], Response[Value, Metadata]]

  /** RPC API schema operations. */
  def apiSchemas: Seq[ApiSchema[Value]]
}
