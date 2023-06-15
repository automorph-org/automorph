package automorph.handler.meta

import automorph.RpcResult
import automorph.handler.HandlerBinding
import automorph.log.MacroLogger
import automorph.reflection.MethodReflection.functionToExpr
import automorph.reflection.{ClassReflection, MethodReflection}
import automorph.spi.MessageCodec
import scala.quoted.{Expr, Quotes, Type}
import scala.util.{Failure, Try}

/**
 * RPC handler API bindings generator.
 */
private[automorph] case object HandlerBindings:

  /**
   * Generates handler bindings for all valid public methods of an API type.
   *
   * @param codec
   *   message codec plugin
   * @param api
   *   API instance
   * @tparam Node
   *   node type
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
  inline def generate[Node, Codec <: MessageCodec[Node], Effect[_], Context, Api <: AnyRef](
    codec: Codec,
    api: Api,
  ): Seq[HandlerBinding[Node, Effect, Context]] =
    ${ generateMacro[Node, Codec, Effect, Context, Api]('codec, 'api) }

  private def generateMacro[
    Node: Type,
    Codec <: MessageCodec[Node]: Type,
    Effect[_]: Type,
    Context: Type,
    Api <: AnyRef: Type
  ](codec: Expr[Codec], api: Expr[Api])(
    using quotes: Quotes
  ): Expr[Seq[HandlerBinding[Node, Effect, Context]]] =
    val ref = ClassReflection(quotes)

    // Detect and validate public methods in the API type
    val apiMethods = MethodReflection.apiMethods[Api, Effect](ref)
    val validMethods = apiMethods.flatMap(_.swap.toOption) match
      case Seq() => apiMethods.flatMap(_.toOption)
      case errors => ref.q.reflect.report.errorAndAbort(
          s"Failed to bind API methods:\n${errors.map(error => s"  $error").mkString("\n")}"
        )

    // Generate bound API method bindings
    val bindings = validMethods.map { method =>
      binding[Node, Codec, Effect, Context, Api](ref)(method, codec, api)
    }
    Expr.ofSeq(bindings)

  private def binding[Node: Type, Codec <: MessageCodec[Node]: Type, Effect[_]: Type, Context: Type, Api: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec], api: Expr[Api]): Expr[HandlerBinding[Node, Effect, Context]] =
    given Quotes = ref.q

    val argumentDecoders = generateArgumentDecoders[Node, Codec, Context](ref)(method, codec)
    val encodeResult = generateEncodeResult[Node, Codec, Effect, Context](ref)(method, codec)
    val call = generateCall[Effect, Context, Api](ref)(method, api)
    logMethod[Api](ref)(method)
    logCode(ref)("Argument decoders", argumentDecoders)
    logCode(ref)("Encode result", encodeResult)
    logCode(ref)("Call", call)
    '{
      HandlerBinding(
        ${ Expr(method.lift.rpcFunction) },
        $argumentDecoders,
        $encodeResult,
        $call,
        ${ Expr(MethodReflection.acceptsContext[Context](ref)(method)) }
      )
    }

  private def generateArgumentDecoders[Node: Type, Codec <: MessageCodec[Node]: Type, Context: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec]): Expr[Map[String, Option[Node] => Any]] =
    import ref.q.reflect.{Term, TypeRepr, asTerm}
    given Quotes = ref.q

    // Map multiple parameter lists to flat argument node list offsets
    val parameterListOffsets = method.parameters.map(_.size).foldLeft(Seq(0)) { (indices, size) =>
      indices :+ (indices.last + size)
    }
    val lastArgumentIndex = method.parameters.map(_.size).sum - 1

    // Create encoded non-existent value expression
    //  codec.encode(None)
    val encodeNoneCall = MethodReflection.call(
      ref.q,
      codec.asTerm,
      MessageCodec.encodeMethod,
      List(TypeRepr.of[None.type]),
      List(List('{ None }.asTerm))
    )

    // Create a map of method parameter names to functions decoding method argument node into a value
    //   Map(
    //     parameterNName -> ((argumentNode: Node) =>
    //       codec.decode[ParameterNType](argumentNode.getOrElse(codec.encode(None)))
    //     ...
    //   ): Map[String, Node => Any]
    val argumentDecoders = method.parameters.toList.zip(parameterListOffsets).flatMap((parameters, offset) =>
      parameters.toList.zipWithIndex.flatMap { (parameter, index) =>
        Option.when((offset + index) != lastArgumentIndex || !MethodReflection.acceptsContext[Context](ref)(method)) {
          '{
            ${ Expr(parameter.name) } -> (
              (argumentNode: Option[Node]) => ${
                // Decode an argument node if present or empty node if missing into a value
                val decodeArguments = List(List('{
                  argumentNode.getOrElse(${ encodeNoneCall.asExprOf[Node] })
                }.asTerm))
                MethodReflection.call(
                  ref.q,
                  codec.asTerm,
                  MessageCodec.decodeMethod,
                  List(parameter.dataType),
                  decodeArguments
                ).asExprOf[Any]
              }
            )
          }
        }
      }
    )
    '{ Map(${ Expr.ofSeq(argumentDecoders) }*) }

  private def generateEncodeResult[Node: Type, Codec <: MessageCodec[Node]: Type, Effect[_]: Type, Context: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec]): Expr[Any => (Node, Option[Context])] =
    import ref.q.reflect.asTerm
    given Quotes = ref.q

    // Create a result encoding function
    //   (result: Any) =>
    //     codec.encode[ResultType](result.asInstanceOf[ResultType]) -> Option.empty[Context]
    //       OR
    //   (result: Any) =>
    //     codec.encode[RpcResultResultType](result.asInstanceOf[ResultType].result) -> Some(
    //       result.asInstanceOf[ResultType].context
    //     )
    val resultType = MethodReflection.unwrapType[Effect](ref.q)(method.resultType).dealias
    MethodReflection.contextualResult[Context, RpcResult](ref.q)(resultType).map { contextualResultType =>
      contextualResultType.asType match
        case '[resultValueType] => '{
          (result: Any) => ${
            MethodReflection.call(
              ref.q,
              codec.asTerm,
              MessageCodec.encodeMethod,
              List(contextualResultType),
              List(List('{ result.asInstanceOf[RpcResult[resultValueType, Context]].result }.asTerm))
            ).asExprOf[Node]
          } -> Some(result.asInstanceOf[RpcResult[resultValueType, Context]].context)
        }
    }.getOrElse {
      resultType.asType match
        case '[resultValueType] => '{
          (result: Any) => ${
            MethodReflection.call(
              ref.q,
              codec.asTerm,
              MessageCodec.encodeMethod,
              List(resultType),
              List(List('{ result.asInstanceOf[resultValueType] }.asTerm))
            ).asExprOf[Node]
          } -> Option.empty[Context]
        }
    }

  private def generateCall[Effect[_]: Type, Context: Type, Api: Type](ref: ClassReflection)(
    method: ref.RefMethod,
    api: Expr[Api]
  ): Expr[(Seq[Any], Context) => Any] =
    import ref.q.reflect.{Term, TypeRepr, asTerm}
    given Quotes = ref.q

    // Map multiple parameter lists to flat argument node list offsets
    val parameterListOffsets = method.parameters.map(_.size).foldLeft(Seq(0)) { (indices, size) =>
      indices :+ (indices.last + size)
    }
    val lastArgumentIndex = method.parameters.map(_.size).sum - 1

    // Create API method call function
    //   (arguments: Seq[Any], requestContext: Context) => Any
    val resultType = MethodReflection.unwrapType[Effect](ref.q)(method.resultType).dealias
    resultType.asType match
      case '[resultValueType] =>
        '{ (arguments, requestContext) =>
          ${
            // Create the method argument lists by type coercing supplied arguments
            // List(List(
            //   arguments(N).asInstanceOf[Any]
            // )): List[List[ParameterXType]]
            val apiMethodArguments = method.parameters.toList.zip(parameterListOffsets).map((parameters, offset) =>
              parameters.toList.zipWithIndex.map { (parameter, index) =>
                val argumentIndex = offset + index
                if argumentIndex == lastArgumentIndex && MethodReflection.acceptsContext[Context](ref)(method) then
                  // Use supplied request context as a last argument if the method accepts context as its last parameter
                  'requestContext.asTerm
                else
                  // Coerce argument type
                  parameter.dataType.asType match
                    case '[parameterType] => '{
                      arguments(${ Expr(argumentIndex) }).asInstanceOf[parameterType]
                    }.asTerm
              }
            ).asInstanceOf[List[List[Term]]]

            // Call the API method and type coerce the result
            //   api.method(arguments*).asInstanceOf[Any]: Any
            // FIXME - coerce the result to a generic effect type
            //   .asInstanceOf[Effect[Any]]
            ref.q.reflect.Select.unique(api.asTerm, method.name).appliedToTypes(List.empty).appliedToArgss(
              apiMethodArguments.asInstanceOf[List[List[ref.q.reflect.Term]]]
            ).asExprOf[Any]
//            MethodReflection.call(ref.q, api.asTerm, method.name, List.empty, apiMethodArguments)
//              .asExprOf[Effect[resultValueType]]
          }
        }

  private def logMethod[Api: Type](ref: ClassReflection)(method: ref.RefMethod): Unit =
    import ref.q.reflect.{Printer, asTerm}

    MacroLogger.debug(s"\n${MethodReflection.methodSignature[Api](ref)(method)}")

  private def logCode(ref: ClassReflection)(name: String, expression: Expr[Any]): Unit =
    import ref.q.reflect.{Printer, asTerm}

    MacroLogger.debug(s"  $name:\n    ${expression.asTerm.show(using Printer.TreeShortCode)}\n")
