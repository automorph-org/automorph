package automorph.server.meta

import automorph.{RpcFunction, RpcResult}
import automorph.log.MacroLogger
import automorph.reflection.{ApiReflection, ClassReflection}
import automorph.server.ServerBinding
import automorph.spi.MessageCodec
import scala.annotation.nowarn
import scala.language.experimental.macros
import scala.reflect.macros.blackbox

/**
 * RPC handler API bindings generator.
 *
 * Note: Consider this class to be private and do not use it. It remains public only due to Scala 2 macro limitations.
 */
object ServerBindingGenerator {

  /**
   * Generates handler bindings for all valid public methods of an API type.
   *
   * @param codec
   *   message codec plugin
   * @param api
   *   API instance
   * @tparam Value
   *   message codec value representation type
   * @tparam Codec
   *   message codec plugin type
   * @tparam Effect
   *   effect type
   * @tparam Context
   *   RPC message context type
   * @tparam Api
   *   API type
   * @return
   *   mapping of API method names to handler function bindings
   */
  def generate[Value, Codec <: MessageCodec[Value], Effect[_], Context, Api <: AnyRef](
    codec: Codec,
    api: Api,
  ): Seq[ServerBinding[Value, Effect, Context]] =
    macro generateMacro[Value, Codec, Effect, Context, Api]

  def generateMacro[
    Value: c.WeakTypeTag,
    Codec <: MessageCodec[Value],
    Effect[_],
    Context: c.WeakTypeTag,
    Api <: AnyRef: c.WeakTypeTag,
  ](c: blackbox.Context)(codec: c.Expr[Codec], api: c.Expr[Api])(implicit
    effectType: c.WeakTypeTag[Effect[?]]
  ): c.Expr[Seq[ServerBinding[Value, Effect, Context]]] = {
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
      generateBinding[c.type, Value, Codec, Effect, Context, Api](ref)(method, codec, api)
    }
    c.Expr[Seq[ServerBinding[Value, Effect, Context]]](q"""
      Seq(..$bindings)
    """)
  }

  private def generateBinding[
    C <: blackbox.Context,
    Value: ref.c.WeakTypeTag,
    Codec <: MessageCodec[Value],
    Effect[_],
    Context: ref.c.WeakTypeTag,
    Api,
  ](ref: ClassReflection[C])(method: ref.RefMethod, codec: ref.c.Expr[Codec], api: ref.c.Expr[Api])(implicit
    effectType: ref.c.WeakTypeTag[Effect[?]]
  ): ref.c.Expr[ServerBinding[Value, Effect, Context]] = {
    import ref.c.universe.{Liftable, Quasiquote}

    val argumentDecoders = generateArgumentDecoders[C, Value, Codec, Context](ref)(method, codec)
    val encodeResult = generateEncodeResult[C, Value, Codec, Effect, Context](ref)(method, codec)
    val call = generateCall[C, Effect, Context, Api](ref)(method, api)
    logMethod[C, Api](ref)(method)
    logCode[C](ref)("Argument decoders", argumentDecoders)
    logCode[C](ref)("Encode result", encodeResult)
    logCode[C](ref)("Call", call)
    implicit val functionLiftable: Liftable[RpcFunction] = ApiReflection.functionLiftable(ref)
    ref.c.Expr[ServerBinding[Value, Effect, Context]](q"""
      automorph.server.ServerBinding(
        ${method.lift.rpcFunction},
        $argumentDecoders,
        $encodeResult,
        $call,
        ${ApiReflection.acceptsContext[C, Context](ref)(method)}
      )
    """)
  }

  private def generateArgumentDecoders[
    C <: blackbox.Context,
    Value: ref.c.WeakTypeTag,
    Codec <: MessageCodec[Value],
    Context: ref.c.WeakTypeTag,
  ](ref: ClassReflection[C])(
    method: ref.RefMethod,
    codec: ref.c.Expr[Codec],
  ): ref.c.Expr[Map[String, Option[Value] => Any]] = {
    import ref.c.universe.{Quasiquote, weakTypeOf}

    // Map multiple parameter lists to flat argument node list offsets
    val parameterListOffsets = method.parameters.map(_.size).foldLeft(Seq(0)) { (indices, size) =>
      indices :+ indices.last + size
    }
    val lastArgumentIndex = method.parameters.map(_.size).sum - 1

    // Create a map of method parameter names to functions decoding method argument node into a value
    //   Map(
    //     parameterNName -> ((argumentValue: Value) =>
    //       codec.decode[ParameterNType](argumentValue.getOrElse(codec.encode(None)))
    //     ...
    //   ): Map[String, Value => Any]
    val nodeType = weakTypeOf[Value].dealias
    val argumentDecoders = method.parameters.toList.zip(parameterListOffsets).flatMap { case (parameters, offset) =>
      parameters.toList.zipWithIndex.flatMap { case (parameter, index) =>
        Option.when(offset + index != lastArgumentIndex || !ApiReflection.acceptsContext[C, Context](ref)(method)) {
          q"""
            ${parameter.name} -> (
              (argumentValue: Option[$nodeType]) =>
                // Decode an argument node if present or empty node if missing into a value
                $codec.decode[${parameter.dataType}](argumentValue.getOrElse($codec.encode(None)))
            )
          """
        }
      }
    }
    ref.c.Expr[Map[String, Option[Value] => Any]](q"Map(..$argumentDecoders)")
  }

  private def generateEncodeResult[C <: blackbox.Context, Value, Codec <: MessageCodec[Value], Effect[_], Context](
    ref: ClassReflection[C]
  )(method: ref.RefMethod, codec: ref.c.Expr[Codec])(implicit
    nodeType: ref.c.WeakTypeTag[Value],
    effectType: ref.c.WeakTypeTag[Effect[?]],
    contextType: ref.c.WeakTypeTag[Context],
  ): ref.c.Expr[Any => (Value, Option[Context])] = {
    import ref.c.universe.Quasiquote

    // Create a result encoding function
    //   (result: Any) =>
    //     codec.encode[ResultType](result.asInstanceOf[ResultType]) -> Option.empty[Context]
    //       OR
    //   (result: Any) =>
    //     codec.encode[RpcResultResultType](result.asInstanceOf[ResultType].result) -> Some(
    //       result.asInstanceOf[ResultType].context
    //     )
    val resultType = ApiReflection.unwrapType[C, Effect[?]](ref.c)(method.resultType).dealias
    ref.c.Expr[Any => (Value, Option[Context])](
      ApiReflection.contextualResult[C, Context, RpcResult[?, ?]](ref.c)(resultType).map { contextualResultType =>
        q"""
          (result: Any) =>
            $codec.encode[$contextualResultType](result.asInstanceOf[$resultType].result) -> Some(
              result.asInstanceOf[$resultType].context.asInstanceOf[$contextType]
            )
        """
      }.getOrElse {
        q"""
          (result: Any) =>
            $codec.encode[$resultType](result.asInstanceOf[$resultType]) -> Option.empty[$contextType]
        """
      }
    )
  }

  @nowarn("msg=never used")
  private def generateCall[C <: blackbox.Context, Effect[_], Context: ref.c.WeakTypeTag, Api](ref: ClassReflection[C])(
    method: ref.RefMethod,
    api: ref.c.Expr[Api],
  )(implicit
    effectType: ref.c.WeakTypeTag[Effect[?]]
  ): ref.c.Expr[(Seq[Any], Context) => Any] = {
    import ref.c.universe.{Quasiquote, weakTypeOf}

    // Map multiple parameter lists to flat argument node list offsets
    val parameterListOffsets = method.parameters.map(_.size).foldLeft(Seq(0)) { (indices, size) =>
      indices :+ indices.last + size
    }
    val lastArgumentIndex = method.parameters.map(_.size).sum - 1

    // Create API method call function
    //   (arguments: Seq[Any], context: Context) => Any
    val finalContextType = weakTypeOf[Context].dealias
    ref.c.Expr[(Seq[Any], Context) => Any](q"""
      (arguments: Seq[Any], context: $finalContextType) => ${
        // Create the method argument lists by type coercing supplied arguments
        //   List(List(
        //     arguments(N).asInstanceOf[NType]
        //   )): List[List[ParameterXType]]
        val apiMethodArguments = method.parameters.toList.zip(parameterListOffsets).map {
          case (parameters, offset) => parameters.toList.zipWithIndex.map { case (parameter, index) =>
              val argumentIndex = offset + index
              if (argumentIndex == lastArgumentIndex && ApiReflection.acceptsContext[C, Context](ref)(method)) {
                // Use supplied request context as a last argument if the method accepts context as its last parameter
                q"context"
              } else {
                // Coerce the argument type
                q"arguments($argumentIndex).asInstanceOf[${parameter.dataType}]"
              }
            }
        }

        // Call the API method and type coerce the result
        //   api.method(arguments*).asInstanceOf[Any]: Any
        // FIXME - coerce the result to the effect type
        //   .asInstanceOf[$effectType[Any]]"
        q"$api.${method.symbol}(...$apiMethodArguments).asInstanceOf[Any]"
      }
    """)
  }

  private def logMethod[C <: blackbox.Context, Api](ref: ClassReflection[C])(
    method: ref.RefMethod
  ): Unit =
    MacroLogger.debug(s"\n${ApiReflection.methodSignature[C, Api](ref)(method)}")

  private def logCode[C <: blackbox.Context](ref: ClassReflection[C])(name: String, expression: ref.c.Expr[Any]): Unit =
    MacroLogger.debug(s"  $name:\n    ${ref.c.universe.showCode(expression.tree)}\n")
}
