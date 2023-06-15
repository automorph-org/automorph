package automorph.client.meta

import automorph.RpcResult
import automorph.client.ClientBinding
import automorph.log.MacroLogger
import automorph.reflection.MethodReflection.functionToExpr
import automorph.reflection.{ClassReflection, MethodReflection}
import automorph.spi.MessageCodec
import scala.quoted.{Expr, Quotes, Type}

/**
 * RPC client API bindings generator.
 */
private[automorph] case object ClientBindings:

  /**
   * Generates client bindings for all valid public methods of an API type.
   *
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
   * @tparam Api
   *   API type
   * @return
   *   mapping of API method names to client function bindings
   */
  inline def generate[Node, Codec <: MessageCodec[Node], Effect[_], Context, Api <: AnyRef](
    codec: Codec
  ): Seq[ClientBinding[Node, Context]] =
    ${ bindingsMacro[Node, Codec, Effect, Context, Api]('codec) }

  private def bindingsMacro[
    Node: Type,
    Codec <: MessageCodec[Node]: Type,
    Effect[_]: Type,
    Context: Type,
    Api <: AnyRef: Type
  ](codec: Expr[Codec])(using quotes: Quotes): Expr[Seq[ClientBinding[Node, Context]]] =
    val ref = ClassReflection(quotes)

    // Detect and validate public methods in the API type
    val apiMethods = MethodReflection.apiMethods[Api, Effect](ref)
    val validMethods = apiMethods.flatMap(_.swap.toOption) match
      case Seq() => apiMethods.flatMap(_.toOption)
      case errors => ref.q.reflect.report
          .errorAndAbort(s"Failed to bind API methods:\n${errors.map(error => s"  $error").mkString("\n")}")

    // Generate bound API method bindings
    val bindings = validMethods.map { method =>
      binding[Node, Codec, Effect, Context, Api](ref)(method, codec)
    }
    Expr.ofSeq(bindings)

  private def binding[Node: Type, Codec <: MessageCodec[Node]: Type, Effect[_]: Type, Context: Type, Api: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec]): Expr[ClientBinding[Node, Context]] =
    given Quotes =
      ref.q

    val encodeArguments = generateArgumentEncoders[Node, Codec, Context](ref)(method, codec)
    val decodeResult = generateDecodeResult[Node, Codec, Effect, Context](ref)(method, codec)
    logBoundMethod[Api](ref)(method, encodeArguments, decodeResult)
    '{
      ClientBinding(
        ${ Expr(method.lift.rpcFunction) },
        $encodeArguments,
        $decodeResult,
        ${ Expr(MethodReflection.acceptsContext[Context](ref)(method)) },
      )
    }

  private def generateArgumentEncoders[Node: Type, Codec <: MessageCodec[Node]: Type, Context: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec]): Expr[Map[String, Any => Node]] =
    import ref.q.reflect.{Term, asTerm}
    given Quotes =
      ref.q

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
    //   ): Map[String, Any => Node]
    val argumentEncoders = method.parameters.toList.zip(parameterListOffsets).flatMap((parameters, offset) =>
      parameters.toList.zipWithIndex.flatMap { (parameter, index) =>
        Option.when(offset + index != lastArgumentIndex || !MethodReflection.acceptsContext[Context](ref)(method)) {
          parameter.dataType.asType match
            case '[parameterType] => '{
                ${ Expr(parameter.name) } -> ((argument: Any) => ${ MethodReflection.call(ref.q, codec.asTerm, MessageCodec.encodeMethod, List(parameter.dataType), List(List('{ argument.asInstanceOf[parameterType] }.asTerm))).asExprOf[Node] })
              }
        }
      }
    )
    '{ Map(${ Expr.ofSeq(argumentEncoders) }*) }

  private def generateDecodeResult[Node: Type, Codec <: MessageCodec[Node]: Type, Effect[_]: Type, Context: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec]): Expr[(Node, Context) => Any] =
    import ref.q.reflect.asTerm
    given Quotes =
      ref.q

    // Create a result decoding function
    //   (resultNode: Node, responseContext: Context) => codec.decode[ResultType](resultNode)
    //     OR
    //   (resultNode: Node, responseContext: Context) => RpcResult(
    //     codec.decode[RpcResultResultType](resultNode),
    //     responseContext
    //   )
    val resultType = MethodReflection.unwrapType[Effect](ref.q)(method.resultType).dealias
    MethodReflection.contextualResult[Context, RpcResult](ref.q)(resultType).map { contextualResultType =>
      '{ (resultNode: Node, responseContext: Context) =>
        RpcResult(
          ${
            MethodReflection.call(
              ref.q,
              codec.asTerm,
              MessageCodec.decodeMethod,
              List(contextualResultType),
              List(List('{ resultNode }.asTerm)),
            ).asExprOf[Any]
          },
          responseContext,
        )
      }
    }.getOrElse {
      '{ (resultNode: Node, _: Context) =>
        ${
          MethodReflection
            .call(ref.q, codec.asTerm, MessageCodec.decodeMethod, List(resultType), List(List('{ resultNode }.asTerm)))
            .asExprOf[Any]
        }
      }
    }

  private def logBoundMethod[Api: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, encodeArguments: Expr[Any], decodeResult: Expr[Any]): Unit =
    import ref.q.reflect.{Printer, asTerm}

    MacroLogger.debug(s"""${MethodReflection.methodSignature[Api](ref)(method)} =
      |  ${encodeArguments.asTerm.show(using Printer.TreeShortCode)}
      |  ${decodeResult.asTerm.show(using Printer.TreeShortCode)}
      |""".stripMargin)
