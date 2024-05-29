package automorph.protocol

import automorph.protocol.jsonrpc.{ErrorMapping, ErrorType, JsonRpcBase, Message}
import automorph.schema.{OpenApi, OpenRpc}
import automorph.spi.{MessageCodec, RpcProtocol}

/**
 * JSON-RPC protocol plugin.
 *
 * Provides the following JSON-RPC methods for service discovery:
 *   - `rpc.discover` - API schema in OpenRPC format
 *   - `api.discover` - API schema in OpenAPI format
 *
 * @constructor
 *   Creates a JSON-RPC protocol plugin.
 * @see
 *   [[https://www.jsonrpc.org/specification Protocol specification]]
 * @param messageCodec
 *   message codec plugin
 * @param mapError
 *   maps a JSON-RPC error to a corresponding exception
 * @param mapException
 *   maps an exception to a corresponding JSON-RPC error
 * @param namedArguments
 *   if true, pass arguments by name, if false pass arguments by position
 * @param mapOpenRpc
 *   transforms generated OpenRPC schema
 * @param mapOpenApi
 *   transforms generated OpenAPI schema
 * @param encodeMessage
 *   converts a JSON-RPC message to message format node
 * @param decodeMessage
 *   converts a message format node to JSON-RPC message
 * @param encodeOpenRpc
 *   converts an OpenRPC description to message format node
 * @param encodeOpenApi
 *   converts an OpenAPI schema to message format node
 * @param encodeStrings
 *   converts a list of strings to message format node
 * @tparam Value
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Context
 *   RPC message context type
 */
final case class JsonRpcProtocol[Value, Codec <: MessageCodec[Value], Context](
  messageCodec: Codec,
  mapError: (String, Int) => Throwable,
  mapException: Throwable => ErrorType,
  namedArguments: Boolean,
  mapOpenApi: OpenApi => OpenApi,
  mapOpenRpc: OpenRpc => OpenRpc,
  protected val encodeMessage: Message[Value] => Value,
  protected val decodeMessage: Value => Message[Value],
  protected val encodeOpenRpc: OpenRpc => Value,
  protected val encodeOpenApi: OpenApi => Value,
  protected val encodeStrings: List[String] => Value,
) extends JsonRpcBase[Value, Codec, Context] with RpcProtocol[Value, Codec, Context]

object JsonRpcProtocol extends ErrorMapping:

  /** Service discovery method providing API schema in OpenRPC format. */
  val openRpcFunction: String = "rpc.discover"

  /** Service discovery method providing API schema in OpenAPI format. */
  val openApiFunction: String = "api.discover"

  /**
   * Creates a JSON-RPC protocol plugin.
   *
   * Provides the following JSON-RPC methods for service discovery:
   *   - `rpc.discover` - API schema in OpenRPC format
   *   - `api.discover` - API schema in OpenAPI format
   *
   * @see
   *   [[https://www.jsonrpc.org/specification JSON-RPC protocol specification]]
   * @param messageCodec
   *   message codec plugin
   * @param mapError
   *   maps a JSON-RPC error to a corresponding exception
   * @param mapException
   *   maps an exception to a corresponding JSON-RPC error
   * @param namedArguments
   *   if true, pass arguments by name, if false pass arguments by position
   * @param mapOpenRpc
   *   transforms generated OpenRPC schema
   * @param mapOpenApi
   *   transforms generated OpenAPI schema
   * @tparam Value
   *   message node type
   * @tparam Codec
   *   message codec plugin type
   * @tparam Context
   *   RPC message context type
   * @return
   *   JSON-RPC protocol plugin
   */
  inline def apply[Value, Codec <: MessageCodec[Value], Context](
    messageCodec: Codec,
    mapError: (String, Int) => Throwable = mapError,
    mapException: Throwable => ErrorType = mapException,
    namedArguments: Boolean = true,
    mapOpenApi: OpenApi => OpenApi = identity,
    mapOpenRpc: OpenRpc => OpenRpc = identity,
  ): JsonRpcProtocol[Value, Codec, Context] =
    val encodeMessage = (message: Message[Value]) => messageCodec.encode[Message[Value]](message)
    val decodeMessage = (messageValue: Value) => messageCodec.decode[Message[Value]](messageValue)
    val encodeOpenRpc = (openRpc: OpenRpc) => messageCodec.encode[OpenRpc](openRpc)
    val encodeOpenApi = (openApi: OpenApi) => messageCodec.encode[OpenApi](openApi)
    val encodeStrings = (strings: List[String]) => messageCodec.encode[List[String]](strings)
    JsonRpcProtocol(
      messageCodec,
      mapError,
      mapException,
      namedArguments,
      mapOpenApi,
      mapOpenRpc,
      encodeMessage,
      decodeMessage,
      encodeOpenRpc,
      encodeOpenApi,
      encodeStrings,
    )
