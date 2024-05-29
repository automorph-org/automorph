package automorph.client.meta

import automorph.{RpcFunction, RpcResult}
import automorph.client.ClientBinding
import automorph.log.MacroLogger
import automorph.reflection.{ApiReflection, ClassReflection}
import automorph.spi.MessageCodec
import scala.annotation.nowarn
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * RPC client API bindings generator.
 *
 * Note: Consider this class to be private and do not use it. It remains public only due to Scala 2 macro limitations.
 */
object ClientBindingGenerator {

  /**
   * Generates client bindings for all valid public methods of an API type.
   *
   * @param codec
   *   message codec plugin
   * @tparam Value
   *   message node type
   * @tparam Codec
   *   message codec plugin type
   * @tparam Effect
   *   effect type
   * @tparam Context
   *   message context type
   * @tparam Api
   *   API type
   * @return
   *   mapping of API method names to client function bindings
   */
  def generate[Value, Codec <: MessageCodec[Value], Effect[_], Context, Api <: AnyRef](
    codec: Codec
  ): Seq[ClientBinding[Value, Context]] =
    macro generateMacro[Value, Codec, Effect, Context, Api]

  def generateMacro[
    Value: c.WeakTypeTag,
    Codec <: MessageCodec[Value],
    Effect[_],
    Context: c.WeakTypeTag,
    Api <: AnyRef: c.WeakTypeTag,
  ](c: blackbox.Context)(codec: c.Expr[Codec])(implicit
    effectType: c.WeakTypeTag[Effect[?]]
  ): c.Expr[Seq[ClientBinding[Value, Context]]] = {
    import c.universe.Quasiquote
    val ref = ClassReflection[c.type](c)

    // Detect and validate public methods in the API type
    val apiMethods = ApiReflection.apiMethods[c.type, Api, Effect[?]](ref)
    val validMethods = apiMethods.flatMap(_.swap.toOption) match {
      case Seq() => apiMethods.flatMap(_.toOption)
      case errors => ref.c.abort(
          ref.c.enclosingPosition,
          s"Failed to bind API methods:\n${errors.map(error => s"  $error").mkString("\n")}",
        )
    }

    // Generate bound API method bindings
    val bindings = validMethods.map { method =>
      generateBinding[c.type, Value, Codec, Effect, Context, Api](ref)(method, codec)
    }
    c.Expr[Seq[ClientBinding[Value, Context]]](q"""
      Seq(..$bindings)
    """)
  }

  @nowarn("msg=never used")
  private def generateBinding[C <: blackbox.Context, Value, Codec <: MessageCodec[Value], Effect[_], Context, Api](
    ref: ClassReflection[C]
  )(method: ref.RefMethod, codec: ref.c.Expr[Codec])(implicit
    nodeType: ref.c.WeakTypeTag[Value],
    effectType: ref.c.WeakTypeTag[Effect[?]],
    contextType: ref.c.WeakTypeTag[Context],
  ): ref.c.Expr[ClientBinding[Value, Context]] = {
    import ref.c.universe.{Liftable, Quasiquote}

    val encodeArguments = generateArgumentEncoders[C, Value, Codec, Context](ref)(method, codec)
    val decodeResult = generateDecodeResult[C, Value, Codec, Effect, Context](ref)(method, codec)
    logBoundMethod[C, Api](ref)(method, encodeArguments, decodeResult)
    implicit val functionLiftable: Liftable[RpcFunction] = ApiReflection.functionLiftable(ref)
    ref.c.Expr[ClientBinding[Value, Context]](q"""
      automorph.client.ClientBinding[$nodeType, $contextType](
        ${method.lift.rpcFunction},
        $encodeArguments,
        $decodeResult,
        ${ApiReflection.acceptsContext[C, Context](ref)(method)}
      )
    """)
  }

  private def generateArgumentEncoders[
    C <: blackbox.Context,
    Value,
    Codec <: MessageCodec[Value],
    Context: ref.c.WeakTypeTag,
  ](ref: ClassReflection[C])(method: ref.RefMethod, codec: ref.c.Expr[Codec]): ref.c.Expr[Map[String, Any => Value]] = {
    import ref.c.universe.Quasiquote

    // Map multiple parameter lists to flat argument node list offsets
    val parameterListOffsets = method.parameters.map(_.size).foldLeft(Seq(0)) { (indices, size) =>
      indices :+ indices.last + size
    }
    val lastArgumentIndex = method.parameters.map(_.size).sum - 1

    // Create a map of method parameter names to functions encoding method argument value into a node
    //   Map(
    //     parameterNName -> (
    //       (argument: Any) => codec.encode[ParameterNType](argument.asInstanceOf[ParameterNType])
    //     )
    //     ...
    //   ): Map[String, Any => Value]
    val argumentEncoders = method.parameters.toList.zip(parameterListOffsets).flatMap { case (parameters, offset) =>
      parameters.toList.zipWithIndex.flatMap { case (parameter, index) =>
        Option.when(
          offset + index != lastArgumentIndex || !ApiReflection.acceptsContext[C, Context](ref)(method)
        ) {
          q"""
            ${parameter.name} -> (
              (argument: Any) => $codec.encode[${parameter.dataType}](argument.asInstanceOf[${parameter.dataType}])
            )
          """
        }
      }
    }
    ref.c.Expr[Map[String, Any => Value]](q"Map(..$argumentEncoders)")
  }

  private def generateDecodeResult[C <: blackbox.Context, Value, Codec <: MessageCodec[Value], Effect[_], Context](
    ref: ClassReflection[C]
  )(method: ref.RefMethod, codec: ref.c.Expr[Codec])(implicit
    nodeType: ref.c.WeakTypeTag[Value],
    effectType: ref.c.WeakTypeTag[Effect[?]],
    contextType: ref.c.WeakTypeTag[Context],
  ): ref.c.Expr[(Value, Context) => Any] = {
    import ref.c.universe.Quasiquote

    // Create a result decoding function
    //   (resultValue: Value, responseContext: Context) => codec.decode[ResultType](resultValue)
    //     OR
    //   (resultValue: Value, responseContext: Context) => RpcResult(
    //     codec.decode[RpcResultResultType](resultValue),
    //     responseContext
    //   )
    val resultType = ApiReflection.unwrapType[C, Effect[?]](ref.c)(method.resultType).dealias
    ApiReflection.contextualResult[C, Context, RpcResult[?, ?]](ref.c)(resultType).map { contextualResultType =>
      ref.c.Expr[(Value, Context) => Any](q"""
        (resultValue: $nodeType, responseContext: $contextType) => automorph.RpcResult(
          $codec.decode[$contextualResultType](resultValue),
          responseContext
        )
      """)
    }.getOrElse {
      ref.c.Expr[(Value, Context) => Any](q"""
        (resultValue: $nodeType, _: $contextType) => $codec.decode[$resultType](resultValue)
      """)
    }
  }

  private def logBoundMethod[C <: blackbox.Context, Api: ref.c.WeakTypeTag](ref: ClassReflection[C])(
    method: ref.RefMethod,
    encodeArguments: ref.c.Expr[Any],
    decodeResult: ref.c.Expr[Any],
  ): Unit =
    MacroLogger.debug(
      s"""${ApiReflection.methodSignature[C, Api](ref)(method)} =
        |  ${ref.c.universe.showCode(encodeArguments.tree)}
        |  ${ref.c.universe.showCode(decodeResult.tree)}
        |""".stripMargin
    )
}
