package automorph.client

import automorph.reflection.ApiReflection
import automorph.spi.MessageCodec
import automorph.RpcResult
import scala.quoted.{Expr, Quotes, Type}

/**
 * RPC function invocation proxy.
 *
 * @tparam Value
 *   message codec value representation type
 * @tparam Codec
 *   message codec plugin type
 * @tparam Effect
 *   effect type
 * @tparam Context
 *   RPC message context type
 * @tparam Result
 *   result type
 */
private[automorph] trait RemoteInvoke[Value, Codec <: MessageCodec[Value], Effect[_], Context, Result]:

  /** Remote function name. */
  def functionName: String

  /** Message codec plugin. */
  def codec: Codec

  /**
   * Sends a remote function invocation request using specified result type extracted from the response.
   *
   * The specified request context is passed to the underlying message transport plugin.
   *
   * @param arguments
   *   argument names and values
   * @param context
   *   request context
   * @return
   *   result value
   */
  def invoke(arguments: Seq[(String, Any)], argumentValues: Seq[Value], context: Context): Effect[Result]

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  def apply()(using context: Context): Effect[Result] =
    invoke(Seq.empty, Seq.empty, context)

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  inline def apply[T1](p1: (String, T1))(using context: Context): Effect[Result] =
    invoke(Seq(p1), Seq(codec.encode(p1._2)), context)

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  inline def apply[T1, T2](p1: (String, T1), p2: (String, T2))(using context: Context): Effect[Result] =
    invoke(Seq(p1, p2), Seq(codec.encode(p1._2), codec.encode(p2._2)), context)

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  inline def apply[T1, T2, T3](p1: (String, T1), p2: (String, T2), p3: (String, T3))(using
    context: Context
  ): Effect[Result] =
    invoke(Seq(p1, p2, p3), Seq(codec.encode(p1._2), codec.encode(p2._2), codec.encode(p3._2)), context)

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  inline def apply[T1, T2, T3, T4](p1: (String, T1), p2: (String, T2), p3: (String, T3), p4: (String, T4))(using
    context: Context
  ): Effect[Result] =
    invoke(
      Seq(p1, p2, p3, p4),
      Seq(codec.encode(p1._2), codec.encode(p2._2), codec.encode(p3._2), codec.encode(p4._2)),
      context,
    )

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  inline def apply[T1, T2, T3, T4, T5](
    p1: (String, T1),
    p2: (String, T2),
    p3: (String, T3),
    p4: (String, T4),
    p5: (String, T5),
  )(using context: Context): Effect[Result] =
    invoke(
      Seq(p1, p2, p3, p4, p5),
      Seq(codec.encode(p1._2), codec.encode(p2._2), codec.encode(p3._2), codec.encode(p4._2), codec.encode(p5._2)),
      context,
    )

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  inline def apply[T1, T2, T3, T4, T5, T6](
    p1: (String, T1),
    p2: (String, T2),
    p3: (String, T3),
    p4: (String, T4),
    p5: (String, T5),
    p6: (String, T6),
  )(using context: Context): Effect[Result] =
    invoke(
      Seq(p1, p2, p3, p4, p5, p6),
      Seq(
        codec.encode(p1._2),
        codec.encode(p2._2),
        codec.encode(p3._2),
        codec.encode(p4._2),
        codec.encode(p5._2),
        codec.encode(p6._2),
      ),
      context,
    )

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  inline def apply[T1, T2, T3, T4, T5, T6, T7](
    p1: (String, T1),
    p2: (String, T2),
    p3: (String, T3),
    p4: (String, T4),
    p5: (String, T5),
    p6: (String, T6),
    p7: (String, T7),
  )(using context: Context): Effect[Result] =
    invoke(
      Seq(p1, p2, p3, p4, p5, p6, p7),
      Seq(
        codec.encode(p1._2),
        codec.encode(p2._2),
        codec.encode(p3._2),
        codec.encode(p4._2),
        codec.encode(p5._2),
        codec.encode(p6._2),
        codec.encode(p7._2),
      ),
      context,
    )

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  inline def apply[T1, T2, T3, T4, T5, T6, T7, T8](
    p1: (String, T1),
    p2: (String, T2),
    p3: (String, T3),
    p4: (String, T4),
    p5: (String, T5),
    p6: (String, T6),
    p7: (String, T7),
    p8: (String, T8),
  )(using context: Context): Effect[Result] =
    invoke(
      Seq(p1, p2, p3, p4, p5, p6, p7, p8),
      Seq(
        codec.encode(p1._2),
        codec.encode(p2._2),
        codec.encode(p3._2),
        codec.encode(p4._2),
        codec.encode(p5._2),
        codec.encode(p6._2),
        codec.encode(p7._2),
        codec.encode(p8._2),
      ),
      context,
    )

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  inline def apply[T1, T2, T3, T4, T5, T6, T7, T8, T9](
    p1: (String, T1),
    p2: (String, T2),
    p3: (String, T3),
    p4: (String, T4),
    p5: (String, T5),
    p6: (String, T6),
    p7: (String, T7),
    p8: (String, T8),
    p9: (String, T9),
  )(using context: Context): Effect[Result] =
    invoke(
      Seq(p1, p2, p3, p4, p5, p6, p7, p8, p9),
      Seq(
        codec.encode(p1._2),
        codec.encode(p2._2),
        codec.encode(p3._2),
        codec.encode(p4._2),
        codec.encode(p5._2),
        codec.encode(p6._2),
        codec.encode(p7._2),
        codec.encode(p8._2),
        codec.encode(p9._2),
      ),
      context,
    )

private[automorph] object RemoteInvoke:

  inline def decodeResult[Value, Codec <: MessageCodec[Value], Context, R](codec: Codec): (Value, Context) => R =
    ${ decodeResultMacro[Value, Codec, Context, R]('codec) }

  private def decodeResultMacro[Value: Type, Codec <: MessageCodec[Value]: Type, Context: Type, R: Type](using
    quotes: Quotes
  )(codec: Expr[Codec]): Expr[(Value, Context) => R] =
    import quotes.reflect.{TypeRepr, asTerm}

    val resultType = TypeRepr.of[R].dealias
    ApiReflection.contextualResult[Context, RpcResult](quotes)(resultType).map { contextualResultType =>
      contextualResultType.asType match
        case '[resultValueType] => '{ (resultValue: Value, responseContext: Context) =>
            RpcResult(
              ${
                ApiReflection.call(
                  quotes,
                  codec.asTerm,
                  MessageCodec.decodeMethod,
                  List(contextualResultType),
                  List(List('{ resultValue }.asTerm)),
                ).asExprOf[resultValueType]
              },
              responseContext,
            )
          }.asInstanceOf[Expr[(Value, Context) => R]]
    }.getOrElse {
      '{ (resultValue: Value, _: Context) =>
        ${
          ApiReflection
            .call(quotes, codec.asTerm, MessageCodec.decodeMethod, List(resultType), List(List('{ resultValue }.asTerm)))
            .asExprOf[R]
        }
      }
    }
