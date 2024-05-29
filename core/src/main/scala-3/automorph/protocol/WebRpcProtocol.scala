package automorph.protocol

import automorph.protocol.webrpc.{ErrorMapping, ErrorType, Message, WebRpcBase}
import automorph.schema.OpenApi
import automorph.spi.{MessageCodec, RpcProtocol}
import automorph.transport.HttpContext

/**
 * Web-RPC protocol implementation.
 *
 * Provides the following Web-RPC functions for service discovery:
 *   - `api.discover` - API schema in OpenAPI format
 *
 * @constructor
 *   Creates a Web-RPC 2.0 protocol implementation.
 * @see
 *   [[https://automorph.org/docs/Web-RPC Web-RPC protocol specification]]
 * @param messageCodec
 *   message codec plugin
 * @param pathPrefix
 *   API path prefix
 * @param mapError
 *   maps a Web-RPC error to a corresponding exception
 * @param mapException
 *   maps an exception to a corresponding Web-RPC error
 * @param mapOpenApi
 *   transforms generated OpenAPI schema
 * @param encodeRequest
 *   converts a Web-RPC request to message format node
 * @param decodeRequest
 *   converts a message format node to Web-RPC request
 * @param encodeResponse
 *   converts a Web-RPC response to message format node
 * @param decodeResponse
 *   converts a message format node to Web-RPC response
 * @param encodeOpenApi
 *   converts an OpenAPI schema to message format node
 * @param encodeString
 *   converts a string to message format node
 * @tparam Value
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Context
 *   RPC message context type
 */
final case class WebRpcProtocol[Value, Codec <: MessageCodec[Value], Context <: HttpContext[?]](
  messageCodec: Codec,
  pathPrefix: String,
  mapError: (String, Option[Int]) => Throwable,
  mapException: Throwable => ErrorType,
  mapOpenApi: OpenApi => OpenApi,
  protected val encodeRequest: Message.Request[Value] => Value,
  protected val decodeRequest: Value => Message.Request[Value],
  protected val encodeResponse: Message[Value] => Value,
  protected val decodeResponse: Value => Message[Value],
  protected val encodeOpenApi: OpenApi => Value,
  protected val encodeString: String => Value,
) extends WebRpcBase[Value, Codec, Context] with RpcProtocol[Value, Codec, Context]

object WebRpcProtocol extends ErrorMapping:

  /** Service discovery method providing API schema in OpenAPI format. */
  val openApiFunction: String = "api.discover"

  /**
   * Creates a Web-RPC protocol plugin.
   *
   * Provides the following Web-RPC functions for service discovery:
   *   - `api.discover` - API schema in OpenAPI format
   *
   * @see
   *   [[https://automorph.org/rest-rpc Web-RPC protocol specification]]
   * @param codec
   *   message codec plugin
   * @param pathPrefix
   *   API path prefix
   * @param mapError
   *   maps a Web-RPC error to a corresponding exception
   * @param mapException
   *   maps an exception to a corresponding Web-RPC error
   * @param mapOpenApi
   *   transforms generated OpenAPI schema
   * @tparam Value
   *   message node type
   * @tparam Codec
   *   message codec plugin type
   * @tparam Context
   *   RPC message context type
   * @return
   *   Web-RPC protocol plugin
   */
  inline def apply[Value, Codec <: MessageCodec[Value], Context <: HttpContext[?]](
    messageCodec: Codec,
    pathPrefix: String,
    mapError: (String, Option[Int]) => Throwable = mapError,
    mapException: Throwable => ErrorType = mapException,
    mapOpenApi: OpenApi => OpenApi = identity,
  ): WebRpcProtocol[Value, Codec, Context] =
    val encodeRequest = (value: Message.Request[Value]) => messageCodec.encode[Message.Request[Value]](value)
    val decodeRequest = (requestNode: Value) => messageCodec.decode[Message.Request[Value]](requestNode)
    val encodeResponse = (value: Message[Value]) => messageCodec.encode[Message[Value]](value)
    val decodeResponse = (responseNode: Value) => messageCodec.decode[Message[Value]](responseNode)
    val encodeOpenApi = (openApi: OpenApi) => messageCodec.encode[OpenApi](openApi)
    val encodeString = (string: String) => messageCodec.encode[String](string)
    WebRpcProtocol(
      messageCodec,
      pathPrefix,
      mapError,
      mapException,
      mapOpenApi,
      encodeRequest,
      decodeRequest,
      encodeResponse,
      decodeResponse,
      encodeOpenApi,
      encodeString,
    )
