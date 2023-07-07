package automorph.client

import automorph.spi.MessageCodec
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

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
) extends RemoteInvoke[Node, Codec, Effect, Context, Result] {

  override def invoke(
    arguments: Seq[(String, Any)],
    argumentNodes: Seq[Node],
    requestContext: Context,
  ): Effect[Result] =
    performCall(functionName, arguments.map(_._1).zip(argumentNodes), decodeResult, Some(requestContext))
}

object RemoteCall {

  def applyMacro[Node: c.WeakTypeTag, Codec <: MessageCodec[Node]: c.WeakTypeTag, Effect[
    _
  ], Context: c.WeakTypeTag, Result: c.WeakTypeTag](c: blackbox.Context)(
    functionName: c.Expr[String],
    codec: c.Expr[Codec],
    performCall: c.Expr[(String, Seq[(String, Node)], (Node, Context) => Result, Option[Context]) => Effect[Result]],
  ): c.Expr[RemoteCall[Node, Codec, Effect, Context, Result]] = {
    import c.universe.{Quasiquote, weakTypeOf}

    val nodeType = weakTypeOf[Node]
    val codecType = weakTypeOf[Codec]
    val contextType = weakTypeOf[Context]
    val resultType = weakTypeOf[Result]
    c.Expr[RemoteCall[Node, Codec, Effect, Context, Result]](q"""
      new automorph.client.RemoteCall(
        $functionName,
        $codec,
        $performCall,
        automorph.client.RemoteInvoke.decodeResult[$nodeType, $codecType, $contextType, $resultType]($codec)
      )
    """)
  }

  /**
   * Creates a new remote function call proxy.
   *
   * @param functionName
   *   remote function name
   * @param codec
   *   message codec plugin
   * @param performCall
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
  def apply[Node, Codec <: MessageCodec[Node], Effect[_], Context, Result](
    functionName: String,
    codec: Codec,
    performCall: (String, Seq[(String, Node)], (Node, Context) => Result, Option[Context]) => Effect[Result],
  ): RemoteCall[Node, Codec, Effect, Context, Result] =
    macro applyMacro[Node, Codec, Effect, Context, Result]
}
