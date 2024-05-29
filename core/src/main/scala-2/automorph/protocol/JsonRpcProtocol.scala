package automorph.protocol

import automorph.protocol.jsonrpc.{ErrorMapping, ErrorType, JsonRpcBase, Message}
import automorph.schema.{OpenApi, OpenRpc}
import automorph.spi.{MessageCodec, RpcProtocol}
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * JSON-RPC protocol implementation.
 *
 * Provides the following JSON-RPC methods for service discovery:
 *   - `rpc.discover` - API schema in OpenRPC format
 *   - `api.discover` - API schema in OpenAPI format
 *
 * @constructor
 *   Creates a JSON-RPC 2.0 protocol implementation.
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
  mapError: (String, Int) => Throwable = JsonRpcProtocol.mapError,
  mapException: Throwable => ErrorType = JsonRpcProtocol.mapException,
  namedArguments: Boolean = true,
  mapOpenApi: OpenApi => OpenApi = identity,
  mapOpenRpc: OpenRpc => OpenRpc = identity,
  protected val encodeMessage: Message[Value] => Value,
  protected val decodeMessage: Value => Message[Value],
  protected val encodeOpenRpc: OpenRpc => Value,
  protected val encodeOpenApi: OpenApi => Value,
  protected val encodeStrings: List[String] => Value,
) extends JsonRpcBase[Value, Codec, Context] with RpcProtocol[Value, Codec, Context]

object JsonRpcProtocol extends ErrorMapping {

  /** Service discovery method providing API schema in OpenRPC format. */
  val openRpcFunction: String = "rpc.discover"

  /** Service discovery method providing API schema in OpenAPI format. */
  val openApiFunction: String = "api.discover"

  def applyMacro[Value: c.WeakTypeTag, Codec <: MessageCodec[Value], Context](c: blackbox.Context)(
    codec: c.Expr[Codec],
    mapError: c.Expr[(String, Int) => Throwable],
    mapException: c.Expr[Throwable => ErrorType],
    namedArguments: c.Expr[Boolean],
    mapOpenApi: c.Expr[OpenApi => OpenApi],
    mapOpenRpc: c.Expr[OpenRpc => OpenRpc],
  ): c.Expr[JsonRpcProtocol[Value, Codec, Context]] = {
    import c.universe.{Quasiquote, weakTypeOf}

    c.Expr[JsonRpcProtocol[Value, Codec, Context]](q"""
      new automorph.protocol.JsonRpcProtocol(
        $codec,
        $mapError,
        $mapException,
        $namedArguments,
        $mapOpenApi,
        $mapOpenRpc,
        message => $codec.encode[automorph.protocol.jsonrpc.Message[${weakTypeOf[Value]}]](message),
        messageNode => $codec.decode[automorph.protocol.jsonrpc.Message[${weakTypeOf[Value]}]](messageNode),
        openRpc => $codec.encode[automorph.schema.OpenRpc](openRpc),
        openApi => $codec.encode[automorph.schema.OpenApi](openApi),
        strings => $codec.encode[List[String]](strings)
      )
    """)
  }

  def applyDefaultsMacro[Value, Codec <: MessageCodec[Value], Context](
    c: blackbox.Context
  )(codec: c.Expr[Codec]): c.Expr[JsonRpcProtocol[Value, Codec, Context]] = {
    import c.universe.Quasiquote

    c.Expr[JsonRpcProtocol[Value, Codec, Context]](q"""
      automorph.protocol.JsonRpcProtocol(
        $codec,
        automorph.protocol.JsonRpcProtocol.mapError,
        automorph.protocol.JsonRpcProtocol.mapException,
        true,
        identity,
        identity
      )
    """)
  }

  /**
   * Creates a JSON-RPC protocol plugin.
   *
   * Provides the following JSON-RPC methods for service discovery:
   *   - `rpc.discover` - API schema in OpenRPC format
   *   - `api.discover` - API schema in OpenAPI format
   *
   * @see
   *   [[https://www.jsonrpc.org/specification JSON-RPC protocol specification]]
   * @param codec
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
  def apply[Value, Codec <: MessageCodec[Value], Context](
    codec: Codec,
    mapError: (String, Int) => Throwable,
    mapException: Throwable => ErrorType,
    namedArguments: Boolean,
    mapOpenApi: OpenApi => OpenApi,
    mapOpenRpc: OpenRpc => OpenRpc,
  ): JsonRpcProtocol[Value, Codec, Context] =
    macro applyMacro[Value, Codec, Context]

  /**
   * Creates a JSON-RPC protocol plugin.
   *
   * Provides the following JSON-RPC methods for service discovery:
   *   - `rpc.discover` - API schema in OpenRPC format
   *   - `api.discover` - API schema in OpenAPI format
   *
   * @see
   *   [[https://www.jsonrpc.org/specification JSON-RPC protocol specification]]
   * @param codec
   *   message codec plugin
   * @tparam Value
   *   message node type
   * @tparam Codec
   *   message codec plugin type
   * @tparam Context
   *   RPC message context type
   * @return
   *   JSON-RPC protocol plugin
   */
  def apply[Value, Codec <: MessageCodec[Value], Context](codec: Codec): JsonRpcProtocol[Value, Codec, Context] =
    macro applyDefaultsMacro[Value, Codec, Context]
}
