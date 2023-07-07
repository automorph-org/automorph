package automorph.client

import automorph.spi.MessageCodec

/**
 * Remote function call proxy.
 *
 * @constructor
 *   Creates a new remote function call proxy.
 * @param functionName
 *   remote function name
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
 * @tparam Result
 *   result type
 */
final case class RemoteCall[Node, Codec <: MessageCodec[Node], Effect[_], Context, Result](
  functionName: String,
  codec: Codec,
  private val performCall: (String, Seq[(String, Node)], (Node, Context) => Result, Option[Context]) => Effect[Result],
  private val decodeResult: (Node, Context) => Result,
) extends RemoteInvoke[Node, Codec, Effect, Context, Result]:

  override def invoke(
    arguments: Seq[(String, Any)],
    argumentNodes: Seq[Node],
    requestContext: Context,
  ): Effect[Result] =
    performCall(functionName, arguments.map(_._1).zip(argumentNodes), decodeResult, Some(requestContext))

object RemoteCall:

  /**
   * Creates a new remote function call proxy.
   *
   * @param functionName
   *   remote function name
   * @param codec
   *   message codec plugin
   * @param peformCall
   *   performs an RPC call using specified arguments
   * @tparam Node
   *   message node type
   * @tparam Codec
   *   message codec plugin type
   * @tparam Effect
   *   effect type
   * @tparam Context
   *   RPC message context type
   * @tparam Result
   *   result type
   */
  inline def apply[Node, Codec <: MessageCodec[Node], Effect[_], Context, Result](
    functionName: String,
    codec: Codec,
    performCall: (String, Seq[(String, Node)], (Node, Context) => Result, Option[Context]) => Effect[Result],
  ): RemoteCall[Node, Codec, Effect, Context, Result] =
    new RemoteCall(functionName, codec, performCall, RemoteInvoke.decodeResult[Node, Codec, Context, Result](codec))
