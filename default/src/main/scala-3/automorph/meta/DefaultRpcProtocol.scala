package automorph.meta

import automorph.codec.CirceJsonCodec
import automorph.protocol.JsonRpcProtocol
import automorph.spi.MessageCodec

private[automorph] trait DefaultRpcProtocol:

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
   * Creates a default JSON-RPC protocol plugin.
   *
   * @see
   *   [[https://www.jsonrpc.org/specification Protocol specification]]
   * @tparam Context
   *   RPC message context type
   * @return
   *   RPC protocol plugin
   */
  def rpcProtocol[Context]: JsonRpcProtocol[Value, Codec, Context] =
    JsonRpcProtocol(messageCodec)

  /**
   * Creates a default JSON-RPC protocol plugin with specified message codec plugin.
   *
   * @see
   *   [[https://www.jsonrpc.org/specification Protocol specification]]
   * @param messageCodec
   *   message codec plugin
   * @return
   *   RPC protocol plugin
   * @tparam ValueType
   *   message node type
   * @tparam CodecType
   *   message codec plugin type
   * @tparam Context
   *   RPC message context type
   */
  inline def rpcProtocol[ValueType, CodecType <: MessageCodec[ValueType], Context](
    messageCodec: CodecType
  ): JsonRpcProtocol[ValueType, CodecType, Context] =
    JsonRpcProtocol(messageCodec)
