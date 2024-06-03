package automorph.server.meta

import automorph.RpcResult
import automorph.log.MacroLogger
import automorph.reflection.ApiReflection.functionToExpr
import automorph.reflection.{ApiReflection, ClassReflection}
import automorph.server.ServerBinding
import automorph.spi.MessageCodec
import scala.quoted.{Expr, Quotes, Type}

/** RPC handler API bindings generator. */
private[automorph] object ServerBindingGenerator:

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
  inline def generate[Value, Codec <: MessageCodec[Value], Effect[_], Context, Api <: AnyRef](
    codec: Codec,
    api: Api,
  ): Seq[ServerBinding[Value, Effect, Context]] =
    ${ generateMacro[Value, Codec, Effect, Context, Api]('codec, 'api) }

  private def generateMacro[
    Value: Type,
    Codec <: MessageCodec[Value],
    Effect[_]: Type,
    Context: Type,
    Api <: AnyRef: Type,
  ](codec: Expr[Codec], api: Expr[Api])(
    using quotes: Quotes
  ): Expr[Seq[ServerBinding[Value, Effect, Context]]] =
    val ref = ClassReflection(quotes)

    // Detect and validate public methods in the API type
    val apiMethods = ApiReflection.apiMethods[Api, Effect](ref)
    val validMethods = apiMethods.flatMap(_.swap.toOption) match
      case Seq() => apiMethods.flatMap(_.toOption)
      case errors => ref.q.reflect.report.errorAndAbort(
          s"Failed to bind API methods:\n${errors.map(error => s"  $error").mkString("\n")}"
        )

    // Generate bound API method bindings
    val bindings = validMethods.map { method =>
      generateBinding[Value, Codec, Effect, Context, Api](ref)(method, codec, api)
    }
    Expr.ofSeq(bindings)

  private def generateBinding[Value: Type, Codec <: MessageCodec[Value], Effect[_]: Type, Context: Type, Api: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec], api: Expr[Api]): Expr[ServerBinding[Value, Effect, Context]] =
    given Quotes =
      ref.q

    val argumentDecoders = generateArgumentDecoders[Value, Codec, Context](ref)(method, codec)
    val encodeResult = generateEncodeResult[Value, Codec, Effect, Context](ref)(method, codec)
    val call = generateCall[Effect, Context, Api](ref)(method, api)
    logMethod[Api](ref)(method)
    logCode(ref)("Argument decoders", argumentDecoders)
    logCode(ref)("Encode result", encodeResult)
    logCode(ref)("Call", call)
    '{
      ServerBinding(
        ${ Expr(method.lift.rpcFunction) },
        $argumentDecoders,
        $encodeResult,
        $call,
        ${ Expr(ApiReflection.acceptsContext[Context](ref)(method)) },
      )
    }

  private def generateArgumentDecoders[Value: Type, Codec <: MessageCodec[Value], Context: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec]): Expr[Map[String, Option[Value] => Any]] =
    import ref.q.reflect.{Term, TypeRepr, asTerm}
    given Quotes =
      ref.q

    // Map multiple parameter lists to flat argument node list offsets
    val parameterListOffsets = method.parameters.map(_.size).foldLeft(Seq(0)) { (indices, size) =>
      indices :+ indices.last + size
    }
    val lastArgumentIndex = method.parameters.map(_.size).sum - 1

    // Create encoded non-existent value expression
    //  codec.encode(None)
    val encodeNoneCall = ApiReflection.call(
      ref.q,
      codec.asTerm,
      MessageCodec.encodeMethod,
      List(TypeRepr.of[None.type]),
      List(List('{ None }.asTerm)),
    )

    // Create a map of method parameter names to functions decoding method argument node into a value
    //   Map(
    //     parameterNName -> ((argumentValue: Value) =>
    //       codec.decode[ParameterNType](argumentValue.getOrElse(codec.encode(None)))
    //     ...
    //   ): Map[String, Value => Any]
    val argumentDecoders = method.parameters.toList.zip(parameterListOffsets).flatMap((parameters, offset) =>
      parameters.toList.zipWithIndex.flatMap { (parameter, index) =>
        Option.when(offset + index != lastArgumentIndex || !ApiReflection.acceptsContext[Context](ref)(method)) {
          '{
            ${ Expr(parameter.name) } -> ((argumentValue: Option[Value]) =>
              ${
                // Decode an argument node if present or empty node if missing into a value
                val decodeArguments = List(List('{
                  argumentValue.getOrElse(${ encodeNoneCall.asExprOf[Value] })
                }.asTerm))
                ApiReflection.call(
                  ref.q,
                  codec.asTerm,
                  MessageCodec.decodeMethod,
                  List(parameter.dataType),
                  decodeArguments,
                ).asExprOf[Any]
              }
            )
          }
        }
      }
    )
    '{ Map(${ Expr.ofSeq(argumentDecoders) }*) }

  private def generateEncodeResult[Value: Type, Codec <: MessageCodec[Value], Effect[_]: Type, Context: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec]): Expr[Any => (Value, Option[Context])] =
    import ref.q.reflect.asTerm
    given Quotes =
      ref.q

    // Create a result encoding function
    //   (result: Any) =>
    //     codec.encode[ResultType](result.asInstanceOf[ResultType]) -> Option.empty[Context]
    //       OR
    //   (result: Any) =>
    //     codec.encode[RpcResultResultType](result.asInstanceOf[ResultType].result) -> Some(
    //       result.asInstanceOf[ResultType].context
    //     )
    val resultType = ApiReflection.unwrapType[Effect](ref.q)(method.resultType).dealias
    ApiReflection.contextualResult[Context, RpcResult](ref.q)(resultType).map { contextualResultType =>
      contextualResultType.asType match
        case '[resultValueType] => '{
            (result: Any) =>
              ${
                ApiReflection.call(
                  ref.q,
                  codec.asTerm,
                  MessageCodec.encodeMethod,
                  List(contextualResultType),
                  List(List('{ result.asInstanceOf[RpcResult[resultValueType, Context]].result }.asTerm)),
                ).asExprOf[Value]
              } -> Some(result.asInstanceOf[RpcResult[resultValueType, Context]].context)
          }
    }.getOrElse {
      resultType.asType match
        case '[resultValueType] => '{
            (result: Any) =>
              ${
                ApiReflection.call(
                  ref.q,
                  codec.asTerm,
                  MessageCodec.encodeMethod,
                  List(resultType),
                  List(List('{ result.asInstanceOf[resultValueType] }.asTerm)),
                ).asExprOf[Value]
              } -> Option.empty[Context]
          }
    }

  private def generateCall[Effect[_]: Type, Context: Type, Api](ref: ClassReflection)(
    method: ref.RefMethod,
    api: Expr[Api],
  ): Expr[(Seq[Any], Context) => Any] =
    import ref.q.reflect.{Select, asTerm}
    given Quotes =
      ref.q

    // Map multiple parameter lists to flat argument node list offsets
    val parameterListOffsets = method.parameters.map(_.size).foldLeft(Seq(0)) { (indices, size) =>
      indices :+ indices.last + size
    }
    val lastArgumentIndex = method.parameters.map(_.size).sum - 1

    // Create API method call function
    //   (arguments: Seq[Any], context: Context) => Any
    ApiReflection.unwrapType[Effect](ref.q)(method.resultType).dealias.asType match
      case '[resultType] =>
        '{ (arguments, context) =>
          ${
            // Create the method argument lists by type coercing supplied arguments
            //   List(List(
            //     arguments(N).asInstanceOf[NType]
            //   )): List[List[ParameterXType]]
            val apiMethodArguments = method.parameters.toList.zip(parameterListOffsets).map((parameters, offset) =>
              parameters.toList.zipWithIndex.map { (parameter, index) =>
                val argumentIndex = offset + index
                if argumentIndex == lastArgumentIndex && ApiReflection.acceptsContext[Context](ref)(method) then
                  // Use supplied request context as a last argument if the method accepts context as its last parameter
                  'context.asTerm
                else
                  // Coerce the argument type
                  parameter.dataType.asType match
                    case '[parameterType] => '{
                        arguments(${ Expr(argumentIndex) }).asInstanceOf[parameterType]
                      }.asTerm
              }
            )

            // Call the API method and type coerce the result
            //   api.method(arguments*).asInstanceOf[Any]: Any
            // FIXME - coerce the result to the effect type
            //   .asInstanceOf[Effect[Any]]
            Select.unique(api.asTerm, method.name).appliedToTypes(List.empty).appliedToArgss(
              apiMethodArguments
            ).asExprOf[Any]
            //            ApiReflection.call(ref.q, api.asTerm, method.name, List.empty, apiMethodArguments)
            //              .asExprOf[Effect[resultType]]
          }
        }

  private def logMethod[Api: Type](ref: ClassReflection)(method: ref.RefMethod): Unit =
    MacroLogger.debug(s"\n${ApiReflection.methodSignature[Api](ref)(method)}")

  private def logCode(ref: ClassReflection)(name: String, expression: Expr[Any]): Unit =
    import ref.q.reflect.{Printer, asTerm}

    MacroLogger.debug(s"  $name:\n    ${expression.asTerm.show(using Printer.TreeShortCode)}\n")
