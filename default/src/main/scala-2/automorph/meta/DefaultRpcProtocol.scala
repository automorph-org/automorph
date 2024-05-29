package automorph.meta

import automorph.codec.CirceJsonCodec
import automorph.protocol.JsonRpcProtocol
import automorph.spi.MessageCodec
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

private[automorph] trait DefaultRpcProtocol {

  /** Default message node type. */
  type Value = CirceJsonCodec.Value

  /** Default message codec plugin type. */
  type Codec = CirceJsonCodec

  /**
   * Creates a Circe JSON message codec plugin.
   *
   * @see
   *   [[https://www.json.org Message format]]
   * @see
   *   [[https://circe.github.io/circe Library documentation]]
   * @see
   *   [[https://circe.github.io/circe/api/io/circe/Json.html Value type]]
   * @return
   *   message codec plugin
   */
  def messageCodec: Codec =
    CirceJsonCodec()

  /**
   * Creates a JSON-RPC protocol plugin.
   *
   * @see
   *   [[https://www.jsonrpc.org/specification Protocol specification]]
   * @tparam Context
   *   RPC message context type
   * @return
   *   RPC protocol plugin
   */
  def rpcProtocol[Context]: JsonRpcProtocol[Value, Codec, Context] =
    JsonRpcProtocol(
      messageCodec,
      JsonRpcProtocol.mapError,
      JsonRpcProtocol.mapException,
      true,
      identity,
      identity,
    )

  /**
   * Creates a JSON-RPC protocol plugin with specified message codec plugin.
   *
   * @see
   *   [[https://www.jsonrpc.org/specification Protocol specification]]
   * @param messageCodec
   *   message codec plugin
   * @tparam ValueType
   *   message node type
   * @tparam CodecType
   *   message codec plugin type
   * @tparam Context
   *   RPC message context type
   * @return
   *   RPC protocol plugin
   */
  def rpcProtocol[ValueType, CodecType <: MessageCodec[ValueType], Context](
    messageCodec: CodecType
  ): JsonRpcProtocol[ValueType, CodecType, Context] =
    macro DefaultRpcProtocol.rpcProtocolMacro[ValueType, CodecType, Context]
}

object DefaultRpcProtocol {

  def rpcProtocolMacro[ValueType, CodecType <: MessageCodec[ValueType], Context](
    c: blackbox.Context
  )(messageCodec: c.Expr[CodecType]): c.Expr[JsonRpcProtocol[ValueType, CodecType, Context]] = {
    import c.universe.Quasiquote

    c.Expr[JsonRpcProtocol[ValueType, CodecType, Context]](q"""
      automorph.protocol.JsonRpcProtocol($messageCodec)
    """)
  }
}
