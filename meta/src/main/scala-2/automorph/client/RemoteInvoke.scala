package automorph.client

import automorph.reflection.ApiReflection
import automorph.spi.MessageCodec
import automorph.RpcResult
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * Remote function invocation proxy.
 *
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
private[automorph] trait RemoteInvoke[Value, Codec <: MessageCodec[Value], Effect[_], Context, Result] {

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
   * @param requestContext
   *   request context
   * @return
   *   result value
   */
  def invoke(arguments: Seq[(String, Any)], argumentNodes: Seq[Value], requestContext: Context): Effect[Result]

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  def apply()(implicit requestContext: Context): Effect[Result] =
    invoke(Seq(), Seq(), requestContext)

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  def apply[T1](p1: (String, T1))(implicit requestContext: Context): Effect[Result] =
    macro RemoteInvoke.apply1Macro[Effect[Result], T1, Context]

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  def apply[T1, T2](p1: (String, T1), p2: (String, T2))(implicit requestContext: Context): Effect[Result] =
    macro RemoteInvoke.apply2Macro[Effect[Result], T1, T2, Context]

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  def apply[T1, T2, T3](p1: (String, T1), p2: (String, T2), p3: (String, T3))(implicit
    requestContext: Context
  ): Effect[Result] =
    macro RemoteInvoke.apply3Macro[Effect[Result], T1, T2, T3, Context]

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  def apply[T1, T2, T3, T4](p1: (String, T1), p2: (String, T2), p3: (String, T3), p4: (String, T4))(implicit
    requestContext: Context
  ): Effect[Result] =
    macro RemoteInvoke.apply4Macro[Effect[Result], T1, T2, T3, T4, Context]

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  def apply[T1, T2, T3, T4, T5](
    p1: (String, T1),
    p2: (String, T2),
    p3: (String, T3),
    p4: (String, T4),
    p5: (String, T5),
  )(implicit requestContext: Context): Effect[Result] =
    macro RemoteInvoke.apply5Macro[Effect[Result], T1, T2, T3, T4, T5, Context]

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  def apply[T1, T2, T3, T4, T5, T6](
    p1: (String, T1),
    p2: (String, T2),
    p3: (String, T3),
    p4: (String, T4),
    p5: (String, T5),
    p6: (String, T6),
  )(implicit requestContext: Context): Effect[Result] =
    macro RemoteInvoke.apply6Macro[Effect[Result], T1, T2, T3, T4, T5, T6, Context]

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  def apply[T1, T2, T3, T4, T5, T6, T7](
    p1: (String, T1),
    p2: (String, T2),
    p3: (String, T3),
    p4: (String, T4),
    p5: (String, T5),
    p6: (String, T6),
    p7: (String, T7),
  )(implicit requestContext: Context): Effect[Result] =
    macro RemoteInvoke.apply7Macro[Effect[Result], T1, T2, T3, T4, T5, T6, T7, Context]

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  def apply[T1, T2, T3, T4, T5, T6, T7, T8](
    p1: (String, T1),
    p2: (String, T2),
    p3: (String, T3),
    p4: (String, T4),
    p5: (String, T5),
    p6: (String, T6),
    p7: (String, T7),
    p8: (String, T8),
  )(implicit requestContext: Context): Effect[Result] =
    macro RemoteInvoke.apply8Macro[Effect[Result], T1, T2, T3, T4, T5, T6, T7, T8, Context]

  /**
   * Invokes the remote function using specified argument names and values.
   *
   * Parameters 'p1', 'p2' ... 'pN' represent function argument values. Effect[R] parameters 'T1', 'T2' ... 'TN'
   * represent function parameter types.
   *
   * @return
   *   remote function invocation result
   */
  def apply[T1, T2, T3, T4, T5, T6, T7, T8, T9](
    p1: (String, T1),
    p2: (String, T2),
    p3: (String, T3),
    p4: (String, T4),
    p5: (String, T5),
    p6: (String, T6),
    p7: (String, T7),
    p8: (String, T8),
    p9: (String, T9),
  )(implicit requestContext: Context): Effect[Result] =
    macro RemoteInvoke.apply9Macro[Effect[Result], T1, T2, T3, T4, T5, T6, T7, T8, T9, Context]
}

object RemoteInvoke {

  def apply1Macro[Result, T1: c.WeakTypeTag, Context](
    c: blackbox.Context
  )(p1: c.Expr[(String, T1)])(requestContext: c.Expr[Context]): c.Expr[Result] = {
    import c.universe.{Quasiquote, weakTypeOf}

    // This remote invoke needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[Result](q"""
      val remoteInvoke = ${c.prefix}
      remoteInvoke.invoke(
        Seq($p1),
        Seq(
          remoteInvoke.codec.encode[${weakTypeOf[T1]}]($p1._2)
        ),
        $requestContext
      )
    """)
  }

  def apply2Macro[Result, T1: c.WeakTypeTag, T2: c.WeakTypeTag, Context](
    c: blackbox.Context
  )(p1: c.Expr[(String, T1)], p2: c.Expr[(String, T2)])(requestContext: c.Expr[Context]): c.Expr[Result] = {
    import c.universe.{Quasiquote, weakTypeOf}

    // This remote invoke needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[Result](q"""
      val remoteInvoke = ${c.prefix}
      remoteInvoke.invoke(
        Seq($p1, $p2),
        Seq(
          remoteInvoke.codec.encode[${weakTypeOf[T1]}]($p1._2),
          remoteInvoke.codec.encode[${weakTypeOf[T2]}]($p2._2)
        ),
        $requestContext
      )
    """)
  }

  def apply3Macro[Result, T1: c.WeakTypeTag, T2: c.WeakTypeTag, T3: c.WeakTypeTag, Context](
    c: blackbox.Context
  )(p1: c.Expr[(String, T1)], p2: c.Expr[(String, T2)], p3: c.Expr[(String, T3)])(
    requestContext: c.Expr[Context]
  ): c.Expr[Result] = {
    import c.universe.{Quasiquote, weakTypeOf}

    // This remote invoke needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[Result](q"""
      val remoteInvoke = ${c.prefix}
      remoteInvoke.invoke(
        Seq($p1, $p2, $p3),
        Seq(
          remoteInvoke.codec.encode[${weakTypeOf[T1]}]($p1._2),
          remoteInvoke.codec.encode[${weakTypeOf[T2]}]($p2._2),
          remoteInvoke.codec.encode[${weakTypeOf[T3]}]($p3._2)
        ),
        $requestContext
      )
    """)
  }

  def apply4Macro[Result, T1: c.WeakTypeTag, T2: c.WeakTypeTag, T3: c.WeakTypeTag, T4: c.WeakTypeTag, Context](
    c: blackbox.Context
  )(p1: c.Expr[(String, T1)], p2: c.Expr[(String, T2)], p3: c.Expr[(String, T3)], p4: c.Expr[(String, T4)])(
    requestContext: c.Expr[Context]
  ): c.Expr[Result] = {
    import c.universe.{Quasiquote, weakTypeOf}

    // This remote invoke needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[Result](q"""
      val remoteInvoke = ${c.prefix}
      remoteInvoke.invoke(
        Seq($p1, $p2, $p3, $p4),
        Seq(
          remoteInvoke.codec.encode[${weakTypeOf[T1]}]($p1._2),
          remoteInvoke.codec.encode[${weakTypeOf[T2]}]($p2._2),
          remoteInvoke.codec.encode[${weakTypeOf[T3]}]($p3._2),
          remoteInvoke.codec.encode[${weakTypeOf[T4]}]($p4._2)
        ),
        $requestContext
      )
    """)
  }

  def apply5Macro[
    Result,
    T1: c.WeakTypeTag,
    T2: c.WeakTypeTag,
    T3: c.WeakTypeTag,
    T4: c.WeakTypeTag,
    T5: c.WeakTypeTag,
    Context,
  ](c: blackbox.Context)(
    p1: c.Expr[(String, T1)],
    p2: c.Expr[(String, T2)],
    p3: c.Expr[(String, T3)],
    p4: c.Expr[(String, T4)],
    p5: c.Expr[(String, T5)],
  )(requestContext: c.Expr[Context]): c.Expr[Result] = {
    import c.universe.{Quasiquote, weakTypeOf}

    // This remote invoke needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[Result](q"""
      val remoteInvoke = ${c.prefix}
      remoteInvoke.invoke(
        Seq($p1, $p2, $p3, $p4, $p5),
        Seq(
          remoteInvoke.codec.encode[${weakTypeOf[T1]}]($p1._2),
          remoteInvoke.codec.encode[${weakTypeOf[T2]}]($p2._2),
          remoteInvoke.codec.encode[${weakTypeOf[T3]}]($p3._2),
          remoteInvoke.codec.encode[${weakTypeOf[T4]}]($p4._2),
          remoteInvoke.codec.encode[${weakTypeOf[T5]}]($p5._2)
        ),
        $requestContext
      )
    """)
  }

  def apply6Macro[
    Result,
    T1: c.WeakTypeTag,
    T2: c.WeakTypeTag,
    T3: c.WeakTypeTag,
    T4: c.WeakTypeTag,
    T5: c.WeakTypeTag,
    T6: c.WeakTypeTag,
    Context,
  ](c: blackbox.Context)(
    p1: c.Expr[(String, T1)],
    p2: c.Expr[(String, T2)],
    p3: c.Expr[(String, T3)],
    p4: c.Expr[(String, T4)],
    p5: c.Expr[(String, T5)],
    p6: c.Expr[(String, T6)],
  )(requestContext: c.Expr[Context]): c.Expr[Result] = {
    import c.universe.{Quasiquote, weakTypeOf}

    // This remote invoke needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[Result](q"""
      val remoteInvoke = ${c.prefix}
      remoteInvoke.invoke(
        Seq($p1, $p2, $p3, $p4, $p5, $p6),
        Seq(
          remoteInvoke.codec.encode[${weakTypeOf[T1]}]($p1._2),
          remoteInvoke.codec.encode[${weakTypeOf[T2]}]($p2._2),
          remoteInvoke.codec.encode[${weakTypeOf[T3]}]($p3._2),
          remoteInvoke.codec.encode[${weakTypeOf[T4]}]($p4._2),
          remoteInvoke.codec.encode[${weakTypeOf[T5]}]($p5._2),
          remoteInvoke.codec.encode[${weakTypeOf[T6]}]($p6._2)
        ),
        $requestContext
      )
    """)
  }

  def apply7Macro[
    Result,
    T1: c.WeakTypeTag,
    T2: c.WeakTypeTag,
    T3: c.WeakTypeTag,
    T4: c.WeakTypeTag,
    T5: c.WeakTypeTag,
    T6: c.WeakTypeTag,
    T7: c.WeakTypeTag,
    Context,
  ](c: blackbox.Context)(
    p1: c.Expr[(String, T1)],
    p2: c.Expr[(String, T2)],
    p3: c.Expr[(String, T3)],
    p4: c.Expr[(String, T4)],
    p5: c.Expr[(String, T5)],
    p6: c.Expr[(String, T6)],
    p7: c.Expr[(String, T7)],
  )(requestContext: c.Expr[Context]): c.Expr[Result] = {
    import c.universe.{Quasiquote, weakTypeOf}

    // This remote invoke needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[Result](q"""
      val remoteInvoke = ${c.prefix}
      remoteInvoke.invoke(
        Seq($p1, $p2, $p3, $p4, $p5, $p6, $p7),
        Seq(
          remoteInvoke.codec.encode[${weakTypeOf[T1]}]($p1._2),
          remoteInvoke.codec.encode[${weakTypeOf[T2]}]($p2._2),
          remoteInvoke.codec.encode[${weakTypeOf[T3]}]($p3._2),
          remoteInvoke.codec.encode[${weakTypeOf[T4]}]($p4._2),
          remoteInvoke.codec.encode[${weakTypeOf[T5]}]($p5._2),
          remoteInvoke.codec.encode[${weakTypeOf[T6]}]($p6._2),
          remoteInvoke.codec.encode[${weakTypeOf[T7]}]($p7._2)
        ),
        $requestContext
      )
    """)
  }

  def apply8Macro[
    Result,
    T1: c.WeakTypeTag,
    T2: c.WeakTypeTag,
    T3: c.WeakTypeTag,
    T4: c.WeakTypeTag,
    T5: c.WeakTypeTag,
    T6: c.WeakTypeTag,
    T7: c.WeakTypeTag,
    T8: c.WeakTypeTag,
    Context,
  ](c: blackbox.Context)(
    p1: c.Expr[(String, T1)],
    p2: c.Expr[(String, T2)],
    p3: c.Expr[(String, T3)],
    p4: c.Expr[(String, T4)],
    p5: c.Expr[(String, T5)],
    p6: c.Expr[(String, T6)],
    p7: c.Expr[(String, T7)],
    p8: c.Expr[(String, T8)],
  )(requestContext: c.Expr[Context]): c.Expr[Result] = {
    import c.universe.{Quasiquote, weakTypeOf}

    // This remote invoke needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[Result](q"""
      val remoteInvoke = ${c.prefix}
      remoteInvoke.invoke(
        Seq($p1, $p2, $p3, $p4, $p5, $p6, $p7, $p8),
        Seq(
          remoteInvoke.codec.encode[${weakTypeOf[T1]}]($p1._2),
          remoteInvoke.codec.encode[${weakTypeOf[T2]}]($p2._2),
          remoteInvoke.codec.encode[${weakTypeOf[T3]}]($p3._2),
          remoteInvoke.codec.encode[${weakTypeOf[T4]}]($p4._2),
          remoteInvoke.codec.encode[${weakTypeOf[T5]}]($p5._2),
          remoteInvoke.codec.encode[${weakTypeOf[T6]}]($p6._2),
          remoteInvoke.codec.encode[${weakTypeOf[T7]}]($p7._2),
          remoteInvoke.codec.encode[${weakTypeOf[T8]}]($p8._2)
        ),
        $requestContext
      )
    """)
  }

  def apply9Macro[
    Result,
    T1: c.WeakTypeTag,
    T2: c.WeakTypeTag,
    T3: c.WeakTypeTag,
    T4: c.WeakTypeTag,
    T5: c.WeakTypeTag,
    T6: c.WeakTypeTag,
    T7: c.WeakTypeTag,
    T8: c.WeakTypeTag,
    T9: c.WeakTypeTag,
    Context,
  ](c: blackbox.Context)(
    p1: c.Expr[(String, T1)],
    p2: c.Expr[(String, T2)],
    p3: c.Expr[(String, T3)],
    p4: c.Expr[(String, T4)],
    p5: c.Expr[(String, T5)],
    p6: c.Expr[(String, T6)],
    p7: c.Expr[(String, T7)],
    p8: c.Expr[(String, T8)],
    p9: c.Expr[(String, T9)],
  )(requestContext: c.Expr[Context]): c.Expr[Result] = {
    import c.universe.{Quasiquote, weakTypeOf}

    // This remote invoke needs to be assigned to a stable identifier due to macro expansion limitations
    c.Expr[Result](q"""
      val remoteInvoke = ${c.prefix}
      remoteInvoke.invoke(
        Seq($p1, $p2, $p3, $p4, $p5, $p6, $p7, $p8, $p9),
        Seq(
          remoteInvoke.codec.encode[${weakTypeOf[T1]}]($p1._2),
          remoteInvoke.codec.encode[${weakTypeOf[T2]}]($p2._2),
          remoteInvoke.codec.encode[${weakTypeOf[T3]}]($p3._2),
          remoteInvoke.codec.encode[${weakTypeOf[T4]}]($p4._2),
          remoteInvoke.codec.encode[${weakTypeOf[T5]}]($p5._2),
          remoteInvoke.codec.encode[${weakTypeOf[T6]}]($p6._2),
          remoteInvoke.codec.encode[${weakTypeOf[T7]}]($p7._2),
          remoteInvoke.codec.encode[${weakTypeOf[T8]}]($p8._2),
          remoteInvoke.codec.encode[${weakTypeOf[T9]}]($p9._2)
        ),
        $requestContext
      )
    """)
  }

  @scala.annotation.nowarn("msg=never used")
  def decodeResultMacro[Value: c.WeakTypeTag, Codec, Context: c.WeakTypeTag, Result: c.WeakTypeTag](
    c: blackbox.Context
  )(codec: c.Expr[Codec])(codecBound: c.Expr[Codec <:< MessageCodec[Value]]): c.Expr[(Value, Context) => Result] = {
    import c.universe.{Quasiquote, weakTypeOf}

    val resultType = weakTypeOf[Result]
    val nodeType = weakTypeOf[Value]
    val contextType = weakTypeOf[Context]
    ApiReflection.contextualResult[c.type, Context, RpcResult[?, ?]](c)(resultType).map { contextualResultType =>
      c.Expr[(Value, Context) => Result](q"""
          (resultNode: $nodeType, responseContext: $contextType) => RpcResult(
            $codec.decode[$contextualResultType](resultNode),
            responseContext
          )
        """)
    }.getOrElse {
      c.Expr[(Value, Context) => Result](q"""
          (resultNode: $nodeType, _: $contextType) => $codec.decode[$resultType](resultNode)
        """)
    }
  }

  def decodeResult[Value, Codec, Context, Result](codec: Codec)(implicit
    codecBound: Codec <:< MessageCodec[Value]
  ): (Value, Context) => Result =
    macro decodeResultMacro[Value, Codec, Context, Result]

}
