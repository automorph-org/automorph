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
 * @tparam Value
 *   message codec value representation type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 */
final private[automorph] case class RemoteTell[Value, Codec <: MessageCodec[Value], Effect[_], Context] private (
  functionName: String,
  codec: Codec,
  private val sendMessage: (String, Seq[(String, Value)], Option[Context]) => Effect[Unit],
) extends RemoteInvoke[Value, Codec, Effect, Context, Unit] {

  override def invoke(arguments: Seq[(String, Any)], argumentValues: Seq[Value], context: Context): Effect[Unit] =
    sendMessage(functionName, arguments.map(_._1).zip(argumentValues), Some(context))
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
   * @tparam Value
   *   message codec value representation type
   * @tparam Codec
   *   message codec plugin type
   * @tparam Effect
   *   effect type
   * @tparam Context
   *   RPC message context type
   */
  def apply[Value, Codec <: MessageCodec[Value], Effect[_], Context](
    functionName: String,
    codec: Codec,
    sendMessage: (String, Seq[(String, Value)], Option[Context]) => Effect[Unit],
  ): RemoteTell[Value, Codec, Effect, Context] =
    new RemoteTell(functionName, codec, sendMessage)
}
