package automorph.reflection

import scala.reflect.macros.blackbox

/**
 * Data type reflection tools.
 *
 * @tparam C
 *   macro context type
 * @param c
 *   macro context
 */
private[automorph] final case class ClassReflection[C <: blackbox.Context](c: C) {

  // All meta-programming data types are path-dependent on the compiler-generated reflection context
  import c.universe.*

  /**
   * Describes class methods within quoted context.
   *
   * @param classType
   *   class type representation
   * @return
   *   quoted class method descriptors
   */
  def methods(classType: Type): Seq[RefMethod] =
    classType.finalResultType.members.filter(_.isMethod).map(member => method(member.asMethod)).toSeq

  private def method(methodSymbol: MethodSymbol): RefMethod = {
    val typeParameters = methodSymbol.typeParams.map(_.asType).map { typeSymbol =>
      RefParameter(typeSymbol.name.toString, typeSymbol.toType.finalResultType, contextual = false)
    }
    val parameters = methodSymbol.paramLists.map(_.map { parameter =>
      // FIXME - fix generic parameter type detection
      RefParameter(parameter.name.toString, parameter.typeSignature, parameter.isImplicit)
    })
    RefMethod(
      methodSymbol.name.toString,
      methodSymbol.returnType.finalResultType,
      parameters,
      typeParameters,
      publicMethod(methodSymbol),
      availableMethod(methodSymbol),
      methodSymbol,
    )
  }

  private def publicMethod(methodSymbol: MethodSymbol): Boolean =
    methodSymbol.isPublic && !methodSymbol.isSynthetic && !methodSymbol.isConstructor

  private def availableMethod(methodSymbol: MethodSymbol): Boolean =
    !methodSymbol.isMacro

  sealed case class RefParameter(name: String, dataType: Type, contextual: Boolean) {

    def lift: Parameter =
      Parameter(name, show(dataType), contextual)
  }

  sealed case class RefMethod(
    name: String,
    resultType: Type,
    parameters: Seq[Seq[RefParameter]],
    typeParameters: Seq[RefParameter],
    public: Boolean,
    available: Boolean,
    symbol: Symbol,
  ) {

    def lift: Method =
      Method(
        name,
        show(resultType),
        parameters.map(_.map(_.lift)),
        typeParameters.map(_.lift),
        public = public,
        available = available,
        documentation = None,
      )
  }
}
