package automorph.reflection

import automorph.{RpcResult, RpcFunction}
import scala.quoted.{quotes, Expr, Quotes, ToExpr, Type}

/** Method introspection. */
private[automorph] case object MethodReflection:

  /**
   * Method RPC function quoted expression converter.
   *
   * @return
   *   method quoted expression converter
   */
  given functionToExpr: ToExpr[RpcFunction] =
    new ToExpr[RpcFunction]:

      given parameterToExpr: ToExpr[RpcFunction.Parameter] =
        new ToExpr[RpcFunction.Parameter]:

          override def apply(v: RpcFunction.Parameter)(using Quotes): Expr[RpcFunction.Parameter] =
            '{ RpcFunction.Parameter(${ Expr(v.name) }, ${ Expr(v.`type`) }) }

      override def apply(v: RpcFunction)(using Quotes): Expr[RpcFunction] =
        '{
          RpcFunction(
            ${ Expr(v.name) }, ${ Expr(v.parameters) }, ${ Expr(v.resultType) }, ${ Expr(v.documentation) }
          )
        }

  /**
   * Detects valid API methods in an API type.
   *
   * @param ref
   *   reflection
   * @tparam Api
   *   API type
   * @tparam Effect
   *   effect type
   * @return
   *   valid method descriptors or error messages by method name
   */
  def apiMethods[Api: Type, Effect[_]: Type](ref: ClassReflection): Seq[Either[String, ref.RefMethod]] =
    import ref.q.reflect.TypeRepr
    given Quotes =
      ref.q

    // Omit base data type methods
    val rootMethodNames = Seq(TypeRepr.of[AnyRef], TypeRepr.of[Product]).flatMap { baseType =>
      ref.methods(baseType).filter(_.public).map(_.name)
    }.toSet
    val methods = ref.methods(TypeRepr.of[Api]).filter(_.public).filter { method =>
      !rootMethodNames.contains(method.name)
    }

    // Validate methods
    val methodNameCount = methods.groupBy(_.name).view.mapValues(_.size).toMap
    methods.map(method => validateApiMethod[Api, Effect](ref)(method, methodNameCount))

  /**
   * Determines whether a method accepts request context as its last parameter.
   *
   * @param ref
   *   reflection context
   * @param method
   *   method descriptor
   * @tparam Context
   *   RPC message context type
   * @return
   *   true if the method accepts request context as its last parameter, false otherwise
   */
  def acceptsContext[Context: Type](ref: ClassReflection)(method: ref.RefMethod): Boolean =
    method.parameters.flatten.lastOption.exists { parameter =>
      parameter.contextual && parameter.dataType =:= ref.q.reflect.TypeRepr.of[Context]
    }

  /**
   * Extracts result type wrapped in a contextual type.
   *
   * @param q
   *   quotation context
   * @param someType
   *   wrapped type
   * @tparam Context
   *   RPC message context type
   * @tparam RpcResult
   *   contextual result type
   * @return
   *   contextual result type if applicable
   */
  def contextualResult[Context: Type, RpcResult[_, _]: Type](q: Quotes)(
    someType: q.reflect.TypeRepr
  ): Option[q.reflect.TypeRepr] =
    import q.reflect.{AppliedType, TypeRepr}

    someType.dealias match {
      case appliedType: AppliedType
        if appliedType.tycon <:< TypeRepr.of[RpcResult] && appliedType.args.size > 1 &&
        appliedType.args(1) =:= TypeRepr.of[Context] => Some(appliedType.args(0))
      case _ => None
    }

  /**
   * Extracts type wrapped in a wrapper type.
   *
   * @param q
   *   quotation context
   * @param someType
   *   wrapped type
   * @tparam Wrapper
   *   wrapper type
   * @return
   *   wrapped type
   */
  def unwrapType[Wrapper[_]: Type](q: Quotes)(someType: q.reflect.TypeRepr): q.reflect.TypeRepr =
    import q.reflect.{AppliedType, ParamRef, TypeRef, TypeRepr}

    val wrapperType = resultType(q)(TypeRepr.of[Wrapper])
    val (wrapperTypeConstructor, wrapperTypeParameterIndex) = wrapperType match
      // Find constructor and first type parameter index for an applied type
      case appliedType: AppliedType => (
          appliedType.tycon,
          appliedType.args.indexWhere {
            case _: ParamRef => true
            case _ => false
          },
        )
      // Assume type reference to be a single parameter type constructor
      case typeRef: TypeRef => (typeRef.dealias, 0)
      // Keep any other types wrapped
      case _ => (wrapperType, -1)
    if wrapperTypeParameterIndex >= 0 then
      someType.dealias match
        case appliedType: AppliedType if appliedType.tycon <:< wrapperTypeConstructor =>
          appliedType.args(wrapperTypeParameterIndex)
        case _ => someType
    else someType

  /**
   * Creates a method signature.
   *
   * @param ref
   *   reflection context
   * @param method
   *   method descriptor
   * @tparam Api
   *   API type
   * @return
   *   method description
   */
  def methodSignature[Api: Type](ref: ClassReflection)(method: ref.RefMethod): String =
    import ref.q.reflect.{Printer, TypeRepr}

    s"${TypeRepr.of[Api].show(using Printer.TypeReprCode)}.${method.lift}"

  /**
   * Creates a method call term.
   *
   * @param quotes
   *   quototation context
   * @param instance
   *   instance term
   * @param methodName
   *   method name
   * @param typeArguments
   *   method type argument types
   * @param arguments
   *   method argument terms
   * @return
   *   instance method call term
   */
  def call(
    quotes: Quotes,
    instance: quotes.reflect.Term,
    methodName: String,
    typeArguments: List[quotes.reflect.TypeRepr],
    arguments: List[List[quotes.reflect.Tree]],
  ): quotes.reflect.Term =
    quotes.reflect.Select.unique(instance, methodName).appliedToTypes(typeArguments)
      .appliedToArgss(arguments.asInstanceOf[List[List[quotes.reflect.Term]]])

  /**
   * Determines whether a method is a valid API method.
   *
   * @param ref
   *   reflection context
   * @param method
   *   method descriptor
   * @param methodNameCount
   *   method name count
   * @tparam Api
   *   API type
   * @tparam Effect
   *   effect type
   * @return
   *   valid API method or an error message
   */
  private def validateApiMethod[Api: Type, Effect[_]: Type](
    ref: ClassReflection
  )(method: ref.RefMethod, methodNameCount: Map[String, Int]): Either[String, ref.RefMethod] =
    import ref.q.reflect.{AppliedType, LambdaType, TypeRepr, TypeTree}

    // No type parameters
    val apiType = TypeTree.of[Api]
    val signature = methodSignature[Api](ref)(method)
    if method.typeParameters.nonEmpty then Left(s"Bound API method must not use type parameters: $signature")

    // Callable at runtime
    else if !method.available then Left(s"Bound API method must be callable at runtime: $signature")

    // Returns the effect type
    else
      val effectType = resultType(ref.q)(TypeRepr.of[Effect])
      val matchingResultType = effectType match
        case appliedEffectType: AppliedType => method.resultType.dealias match
            case resultType: AppliedType => resultType.tycon <:< appliedEffectType.tycon
            case _ => false
        case _ => true
      if !matchingResultType then
        Left(s"Bound API method must return ${effectType.show}: $signature")
      else
        if methodNameCount.getOrElse(method.name, 0) > 1 then
          Left(s"Bound API method must not have overloaded alternatives: $signature")
        else
          Right(method)

  /**
   * Determines result type if the specified type is a lambda type.
   *
   * @param q
   *   quotation context
   * @param someType
   *   some type
   * @return
   *   result type
   */
  private def resultType(q: Quotes)(someType: q.reflect.TypeRepr): q.reflect.TypeRepr =
    someType.dealias match
      case lambdaType: q.reflect.LambdaType => lambdaType.resType.dealias
      case _ => someType.dealias
