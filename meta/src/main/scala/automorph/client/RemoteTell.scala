package automorph.client

import automorph.spi.MessageCodec

/**
 * Remote function one-way message proxy.
 *
 * @constructor
 *   Creates a new remote function one-way message proxy.
 * @param functionName
 *   remote function name.
 * @param codec
 *   message codec plugin
 * @tparam Node
 *   message node type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
final private[automorph] case class RemoteTell[Node, Codec <: MessageCodec[Node], Effect[_], Context] private (
  functionName: String,
  codec: Codec,
  private val sendMessage: (String, Seq[(String, Node)], Option[Context]) => Effect[Unit],
) extends RemoteInvoke[Node, Codec, Effect, Context, Unit] {

  override def invoke(arguments: Seq[(String, Any)], argumentNodes: Seq[Node], requestContext: Context): Effect[Unit] =
    sendMessage(functionName, arguments.map(_._1).zip(argumentNodes), Some(requestContext))
}

private[automorph] object RemoteTell {

  /**
   * Creates a new one-way remote function message with specified RPC function name.
   *
   * @param functionName
   *   RPC function name
   * @param codec
   *   message codec plugin
   * @param sendMessage
   *   sends an RPC message with specified function name, arguments and request context
   * @tparam Node
   *   message node type
   * @tparam Codec
   *   message codec plugin type
   * @tparam Effect
   *   effect type
   * @tparam Context
   *   RPC message context type
   */
  def apply[Node, Codec <: MessageCodec[Node], Effect[_], Context](
    functionName: String,
    codec: Codec,
    sendMessage: (String, Seq[(String, Node)], Option[Context]) => Effect[Unit],
  ): RemoteTell[Node, Codec, Effect, Context] =
    new RemoteTell(functionName, codec, sendMessage)
}
