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
 * @tparam Value
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
final case class RemoteCall[Value, Codec <: MessageCodec[Value], Effect[_], Context, Result](
  functionName: String,
  codec: Codec,
  private val performCall: (String, Seq[(String, Value)], (Value, Context) => Result, Option[Context]) => Effect[Result],
  private val decodeResult: (Value, Context) => Result,
) extends RemoteInvoke[Value, Codec, Effect, Context, Result] {

  override def invoke(
    arguments: Seq[(String, Any)],
    argumentValues: Seq[Value],
    requestContext: Context,
  ): Effect[Result] =
    performCall(functionName, arguments.map(_._1).zip(argumentValues), decodeResult, Some(requestContext))
}

object RemoteCall {

  def applyMacro[Value: c.WeakTypeTag, Codec <: MessageCodec[Value]: c.WeakTypeTag, Effect[
    _
  ], Context: c.WeakTypeTag, Result: c.WeakTypeTag](c: blackbox.Context)(
    functionName: c.Expr[String],
    codec: c.Expr[Codec],
    performCall: c.Expr[(String, Seq[(String, Value)], (Value, Context) => Result, Option[Context]) => Effect[Result]],
  ): c.Expr[RemoteCall[Value, Codec, Effect, Context, Result]] = {
    import c.universe.{Quasiquote, weakTypeOf}

    val nodeType = weakTypeOf[Value]
    val codecType = weakTypeOf[Codec]
    val contextType = weakTypeOf[Context]
    val resultType = weakTypeOf[Result]
    c.Expr[RemoteCall[Value, Codec, Effect, Context, Result]](q"""
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
   * @tparam Value
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
  def apply[Value, Codec <: MessageCodec[Value], Effect[_], Context, Result](
    functionName: String,
    codec: Codec,
    performCall: (String, Seq[(String, Value)], (Value, Context) => Result, Option[Context]) => Effect[Result],
  ): RemoteCall[Value, Codec, Effect, Context, Result] =
    macro applyMacro[Value, Codec, Effect, Context, Result]
}
