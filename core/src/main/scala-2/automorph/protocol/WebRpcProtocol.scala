package automorph.protocol

import automorph.protocol.webrpc.{ErrorMapping, ErrorType, Message, WebRpcBase}
import automorph.schema.OpenApi
import automorph.spi.{MessageCodec, RpcProtocol}
import automorph.transport.HttpContext
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Web-RPC protocol plugin.
 *
 * Provides the following Web-RPC functions for service discovery:
 *   - `api.discover` - API schema in OpenAPI format
 *
 * @constructor
 *   Creates a Web-RPC protocol plugin.
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
  mapError: (String, Option[Int]) => Throwable = WebRpcProtocol.mapError,
  mapException: Throwable => ErrorType = WebRpcProtocol.mapException,
  mapOpenApi: OpenApi => OpenApi = identity,
  protected val encodeRequest: Message.Request[Value] => Value,
  protected val decodeRequest: Value => Message.Request[Value],
  protected val encodeResponse: Message[Value] => Value,
  protected val decodeResponse: Value => Message[Value],
  protected val encodeOpenApi: OpenApi => Value,
  protected val encodeString: String => Value,
) extends WebRpcBase[Value, Codec, Context] with RpcProtocol[Value, Codec, Context]

object WebRpcProtocol extends ErrorMapping {

  /** Service discovery method providing API schema in OpenAPI format. */
  val openApiFunction: String = "api.discover"

  def applyMacro[Value: c.WeakTypeTag, Codec <: MessageCodec[Value], Context <: HttpContext[?]](c: blackbox.Context)(
    codec: c.Expr[Codec],
    pathPrefix: c.Expr[String],
    mapError: c.Expr[(String, Option[Int]) => Throwable],
    mapException: c.Expr[Throwable => ErrorType],
    mapOpenApi: c.Expr[OpenApi => OpenApi],
  ): c.Expr[WebRpcProtocol[Value, Codec, Context]] = {
    import c.universe.{Quasiquote, weakTypeOf}
    Seq(weakTypeOf[Value], weakTypeOf[Codec])

    c.Expr[WebRpcProtocol[Value, Codec, Context]](q"""
      new automorph.protocol.WebRpcProtocol(
        $codec,
        $pathPrefix,
        $mapError,
        $mapException,
        $mapOpenApi,
        request => $codec.encode[automorph.protocol.webrpc.Message.Request[${weakTypeOf[Value]}]](request),
        requestValue => $codec.decode[automorph.protocol.webrpc.Message.Request[${weakTypeOf[Value]}]](requestValue),
        response => $codec.encode[automorph.protocol.webrpc.Message[${weakTypeOf[Value]}]](response),
        responseValue => $codec.decode[automorph.protocol.webrpc.Message[${weakTypeOf[Value]}]](responseValue),
        openApi => $codec.encode[automorph.schema.OpenApi](openApi),
        string => $codec.encode[String](string)
      )
    """)
  }

  def applyDefaultsMacro[Value, Codec <: MessageCodec[Value], Context <: HttpContext[?]](
    c: blackbox.Context
  )(codec: c.Expr[Codec], pathPrefix: c.Expr[String]): c.Expr[WebRpcProtocol[Value, Codec, Context]] = {
    import c.universe.Quasiquote

    c.Expr[WebRpcProtocol[Value, Codec, Context]](q"""
      automorph.protocol.WebRpcProtocol(
        $codec,
        $pathPrefix,
        automorph.protocol.WebRpcProtocol.mapError,
        automorph.protocol.WebRpcProtocol.mapException,
        identity
      )
    """)
  }

  /**
   * Creates a Web-RPC protocol plugin.
   *
   * Provides the following JSON-RPC functions for service discovery:
   *   - `api.discover` - API schema in OpenAPI format
   *
   * @see
   *   [[https://www.jsonrpc.org/specification Web-RPC protocol specification]]
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
  def apply[Value, Codec <: MessageCodec[Value], Context <: HttpContext[?]](
    codec: Codec,
    pathPrefix: String,
    mapError: (String, Option[Int]) => Throwable,
    mapException: Throwable => ErrorType,
    mapOpenApi: OpenApi => OpenApi,
  ): WebRpcProtocol[Value, Codec, Context] =
    macro applyMacro[Value, Codec, Context]

  /**
   * Creates a Web-RPC protocol plugin.
   *
   * Provides the following Web-RPC functions for service discovery:
   *   - `api.discover` - API schema in OpenAPI format
   *
   * @see
   *   [[https://www.jsonrpc.org/specification Web-RPC protocol specification]]
   * @param codec
   *   message codec plugin
   * @param pathPrefix
   *   API path prefix
   * @tparam Value
   *   message node type
   * @tparam Codec
   *   message codec plugin type
   * @tparam Context
   *   RPC message context type
   * @return
   *   Web-RPC protocol plugin
   */
  def apply[Value, Codec <: MessageCodec[Value], Context <: HttpContext[?]](
    codec: Codec,
    pathPrefix: String,
  ): WebRpcProtocol[Value, Codec, Context] =
    macro applyDefaultsMacro[Value, Codec, Context]
}
