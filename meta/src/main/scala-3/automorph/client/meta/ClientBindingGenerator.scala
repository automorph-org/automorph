package automorph.client.meta

import automorph.RpcResult
import automorph.client.ClientBinding
import automorph.log.MacroLogger
import automorph.reflection.ApiReflection.functionToExpr
import automorph.reflection.{ApiReflection, ClassReflection}
import automorph.spi.MessageCodec
import scala.quoted.{Expr, Quotes, Type}

/** RPC client API bindings generator. */
private[automorph] object ClientBindingGenerator:

  /**
   * Generates client bindings for all valid public methods of an API type.
   *
   * @param codec
   *   message codec plugin
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
   *   mapping of API method names to client function bindings
   */
  inline def generate[Value, Codec <: MessageCodec[Value], Effect[_], Context, Api <: AnyRef](
    codec: Codec
  ): Seq[ClientBinding[Value, Context]] =
    ${ generateMacro[Value, Codec, Effect, Context, Api]('codec) }

  private def generateMacro[
    Value: Type,
    Codec <: MessageCodec[Value],
    Effect[_]: Type,
    Context: Type,
    Api <: AnyRef: Type,
  ](codec: Expr[Codec])(using quotes: Quotes): Expr[Seq[ClientBinding[Value, Context]]] =
    val ref = ClassReflection(quotes)

    // Detect and validate public methods in the API type
    val apiMethods = ApiReflection.apiMethods[Api, Effect](ref)
    val validMethods = apiMethods.flatMap(_.swap.toOption) match
      case Seq() => apiMethods.flatMap(_.toOption)
      case errors => ref.q.reflect.report
          .errorAndAbort(s"Failed to bind API methods:\n${errors.map(error => s"  $error").mkString("\n")}")

    // Generate bound API method bindings
    val bindings = validMethods.map { method =>
      generateBinding[Value, Codec, Effect, Context, Api](ref)(method, codec)
    }
    Expr.ofSeq(bindings)

  private def generateBinding[Value: Type, Codec <: MessageCodec[Value], Effect[_]: Type, Context: Type, Api: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec]): Expr[ClientBinding[Value, Context]] =
    given Quotes =
      ref.q

    val encodeArguments = generateArgumentEncoders[Value, Codec, Context](ref)(method, codec)
    val decodeResult = generateDecodeResult[Value, Codec, Effect, Context](ref)(method, codec)
    logBoundMethod[Api](ref)(method, encodeArguments, decodeResult)
    '{
      ClientBinding(
        ${ Expr(method.lift.rpcFunction) },
        $encodeArguments,
        $decodeResult,
        ${ Expr(ApiReflection.acceptsContext[Context](ref)(method)) },
      )
    }

  private def generateArgumentEncoders[Value: Type, Codec <: MessageCodec[Value], Context: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec]): Expr[Map[String, Any => Value]] =
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
    //   ): Map[String, Any => Value]
    val argumentEncoders = method.parameters.toList.zip(parameterListOffsets).flatMap((parameters, offset) =>
      parameters.toList.zipWithIndex.flatMap { (parameter, index) =>
        Option.when(offset + index != lastArgumentIndex || !ApiReflection.acceptsContext[Context](ref)(method)) {
          parameter.dataType.asType match
            case '[parameterType] => '{
                ${ Expr(parameter.name) } -> ((argument: Any) =>
                  ${
                    ApiReflection.call(
                      ref.q,
                      codec.asTerm,
                      MessageCodec.encodeMethod,
                      List(parameter.dataType),
                      List(List('{ argument.asInstanceOf[parameterType] }.asTerm)),
                    ).asExprOf[Value]
                  }
                )
              }
        }
      }
    )
    '{ Map(${ Expr.ofSeq(argumentEncoders) }*) }

  private def generateDecodeResult[Value: Type, Codec <: MessageCodec[Value], Effect[_]: Type, Context: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, codec: Expr[Codec]): Expr[(Value, Context) => Any] =
    import ref.q.reflect.asTerm
    given Quotes =
      ref.q

    // Create a result decoding function
    //   (resultValue: Value, responseContext: Context) => codec.decode[ResultType](resultValue)
    //     OR
    //   (resultValue: Value, responseContext: Context) => RpcResult(
    //     codec.decode[RpcResultResultType](resultValue),
    //     responseContext
    //   )
    val resultType = ApiReflection.unwrapType[Effect](ref.q)(method.resultType).dealias
    ApiReflection.contextualResult[Context, RpcResult](ref.q)(resultType).map { contextualResultType =>
      '{ (resultValue: Value, responseContext: Context) =>
        RpcResult(
          ${
            ApiReflection.call(
              ref.q,
              codec.asTerm,
              MessageCodec.decodeMethod,
              List(contextualResultType),
              List(List('{ resultValue }.asTerm)),
            ).asExprOf[Any]
          },
          responseContext,
        )
      }
    }.getOrElse {
      '{ (resultValue: Value, _: Context) =>
        ${
          ApiReflection
            .call(ref.q, codec.asTerm, MessageCodec.decodeMethod, List(resultType), List(List('{ resultValue }.asTerm)))
            .asExprOf[Any]
        }
      }
    }

  private def logBoundMethod[Api: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, encodeArguments: Expr[Any], decodeResult: Expr[Any]): Unit =
    import ref.q.reflect.{Printer, asTerm}

    MacroLogger.debug(s"""${ApiReflection.methodSignature[Api](ref)(method)} =
      |  ${encodeArguments.asTerm.show(using Printer.TreeShortCode)}
      |  ${decodeResult.asTerm.show(using Printer.TreeShortCode)}
      |""".stripMargin)
