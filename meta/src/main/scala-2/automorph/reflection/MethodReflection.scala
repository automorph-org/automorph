package automorph.reflection

import automorph.RpcFunction
import scala.annotation.nowarn
import scala.reflect.macros.blackbox

/** Method introspection. */
private[automorph] case object MethodReflection {

  /**
   * Creates RPC function quoted tree converter.
   *
   * @param ref
   *   reflection
   * @tparam C
   *   macro context type
   * @return
   *   method quoted tree converter
   */
  def functionLiftable[C <: blackbox.Context](ref: ClassReflection[C]): ref.c.universe.Liftable[RpcFunction] =
    new ref.c.universe.Liftable[RpcFunction] {

      import ref.c.universe.{Liftable, Quasiquote, Tree}

      @nowarn("msg=used")
      implicit val parameterLiftable: Liftable[RpcFunction.Parameter] = (v: RpcFunction.Parameter) => q"""
        automorph.RpcFunction.Parameter(
          ${v.name},
          ${v.`type`}
        )
      """

      override def apply(v: RpcFunction): Tree =
        q"""
        automorph.RpcFunction(
          ${v.name},
          Seq(..${v.parameters}),
          ${v.resultType},
          ${v.documentation}
        )
      """
    }

  /**
   * Detects valid API methods in an API type.
   *
   * @param ref
   *   reflection
   * @tparam C
   *   macro context type
   * @tparam Api
   *   API type
   * @tparam Effect
   *   effect type
   * @return
   *   valid method descriptors or error messages by method name
   */
  def apiMethods[C <: blackbox.Context, Api: ref.c.WeakTypeTag, Effect: ref.c.WeakTypeTag](
    ref: ClassReflection[C]
  ): Seq[Either[String, ref.RefMethod]] = {
    // Omit base data type methods
    val rootMethodNames = Seq(ref.c.weakTypeOf[AnyRef], ref.c.weakTypeOf[Product]).flatMap { baseType =>
      ref.methods(baseType).filter(_.public).map(_.name)
    }.toSet
    val methods = ref.methods(ref.c.weakTypeOf[Api]).filter(_.public).filter { method =>
      !rootMethodNames.contains(method.name)
    }

    // Validate methods
    val methodNameCount = methods.groupBy(_.name).view.mapValues(_.size).toMap
    methods.map(method => validateApiMethod[C, Api, Effect](ref)(method, methodNameCount))
  }

  /**
   * Determines whether a method is a valid API method.
   *
   * @param ref
   *   reflection context
   * @param method
   *   method
   * @param methodNameCount
   *   method name count
   * @tparam C
   *   macro context type
   * @tparam Api
   *   API type
   * @tparam Effect
   *   effect type
   * @return
   *   valid API method or an error message
   */
  private def validateApiMethod[C <: blackbox.Context, Api: ref.c.WeakTypeTag, Effect: ref.c.WeakTypeTag](
    ref: ClassReflection[C]
  )(method: ref.RefMethod, methodNameCount: Map[String, Int]): Either[String, ref.RefMethod] = {
    // No type parameters
    val signature = methodSignature[C, Api](ref)(method)
    if (method.typeParameters.nonEmpty) {
      Left(s"Bound API method must not use type parameters: $signature")
    } else {
      // Callable at runtime
      if (!method.available) {
        Left(s"Bound API method must be callable at runtime: $signature")
      } else {
        // Returns the effect type
        val effectTypeConstructor = ref.c.weakTypeOf[Effect].dealias.typeConstructor
        val matchingResultType = method.resultType.typeArgs.nonEmpty &&
          method.resultType.dealias.typeConstructor <:< effectTypeConstructor

        // FIXME - determine concrete result type constructor instead of an abstract one
        if (!matchingResultType && false) {
          Left(s"Bound API method must return ${effectTypeConstructor.typeSymbol.fullName}: $signature")
        } else {
          if (methodNameCount.getOrElse(method.name, 0) > 1) {
            Left(s"Bound API method must not have overloaded alternatives: $signature")
          } else {
            Right(method)
          }
        }
      }
    }
  }

  /**
   * Creates a method signature.
   *
   * @param ref
   *   reflection context
   * @param method
   *   method descriptor
   * @tparam C
   *   macro context type
   * @tparam Api
   *   API type
   * @return
   *   method description
   */
  def methodSignature[C <: blackbox.Context, Api: ref.c.WeakTypeTag](ref: ClassReflection[C])(
    method: ref.RefMethod
  ): String =
    s"${ref.c.weakTypeOf[Api].typeSymbol.fullName}.${method.lift}"

  /**
   * Determines whether a method accepts request context as its last parameter.
   *
   * @param ref
   *   reflection context
   * @param method
   *   method descriptor
   * @tparam C
   *   macro context type
   * @tparam Context
   *   RPC message context type
   * @return
   *   true if the method accept request context as its last parameter, false otherwise
   */
  def acceptsContext[C <: blackbox.Context, Context: ref.c.WeakTypeTag](
    ref: ClassReflection[C]
  )(method: ref.RefMethod): Boolean = {
    Seq(ref.c.weakTypeOf[Context])
    method.parameters.flatten.lastOption.exists { parameter =>
      // FIXME - fix generic parameter type detection
      parameter.contextual
//      parameter.contextual && parameter.dataType =:= ref.c.weakTypeOf[Context]
    }
  }

  /**
   * Extracts result type wrapped in a contextual type.
   *
   * @param c
   *   macro context
   * @param someType
   *   wrapped type
   * @tparam C
   *   macro context type
   * @tparam Context
   *   RPC message context type
   * @tparam RpcResult
   *   contextual result type
   * @return
   *   contextual result type if applicable
   */
  @nowarn("msg=check")
  def contextualResult[C <: blackbox.Context, Context: c.WeakTypeTag, RpcResult: c.WeakTypeTag](
    c: C
  )(someType: c.Type): Option[c.Type] = {
    import c.universe.TypeRef
    Seq(c.weakTypeOf[Context])

    someType.dealias match {
      case typeRef: TypeRef
        // FIXME - fix generic parameter type detection
        if typeRef.typeConstructor <:< c.weakTypeOf[RpcResult].typeConstructor && typeRef.typeArgs.size == 2
         => Some(typeRef.typeArgs(0))
//        if typeRef.typeConstructor <:< c.weakTypeOf[RpcResult].typeConstructor && typeRef.typeArgs.size == 2 &&
//        typeRef.typeArgs(1) =:= c.weakTypeOf[Context] => Some(typeRef.typeArgs(0))
      case _ => None
    }
  }

  /**
   * Extracts type wrapped in a wrapper type.
   *
   * @param c
   *   macro context
   * @param someType
   *   wrapped type
   * @tparam C
   *   macro context type
   * @return
   *   wrapped type
   */
  @nowarn("msg=check")
  def unwrapType[C <: blackbox.Context, Wrapper: c.WeakTypeTag](c: C)(someType: c.Type): c.Type = {
    import c.universe.TypeRef

    val wrapperType = resultType(c)(c.weakTypeOf[Wrapper])
    val (wrapperTypeConstructor, wrapperTypeParameterIndex) = wrapperType match {
      case typeRef: TypeRef =>
        val expandedType = if (typeRef.typeArgs.nonEmpty) typeRef else typeRef.etaExpand.resultType.dealias
        if (expandedType.typeArgs.nonEmpty) {
          // Find constructor and first type parameter index for an applied type
          (
            expandedType.typeConstructor,
            expandedType.typeArgs.indexWhere {
              case typeRef: TypeRef => typeRef.typeSymbol.isAbstract &&
                !(typeRef =:= c.typeOf[Any] || typeRef <:< c.typeOf[AnyRef] || typeRef <:< c.typeOf[AnyVal])
              case _ => false
            },
          )
        } else {
          // Assume type reference to be a single parameter type constructor
          (expandedType, 0)
        }
      // Keep any other types wrapped
      case _ => (wrapperType, -1)
    }
    if (
      wrapperTypeParameterIndex >= 0 && (someType.dealias.typeConstructor <:< wrapperTypeConstructor) ||
      someType.dealias.typeConstructor.typeSymbol.name == wrapperTypeConstructor.typeSymbol.name
    ) { someType.dealias.typeArgs(wrapperTypeParameterIndex) }
    else someType
  }

  /**
   * Determines result type if the specified type is a lambda type.
   *
   * @param c
   *   macro context
   * @param someType
   *   some type
   * @tparam C
   *   macro context type
   * @return
   *   result type
   */
  @nowarn("msg=check")
  private def resultType[C <: blackbox.Context](c: C)(someType: c.Type): c.Type =
    someType.dealias match {
      case polyType: c.universe.PolyType => polyType.resultType.dealias
      case _ => someType.dealias
    }
}
