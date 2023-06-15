package automorph.reflection

import scala.quoted.{quotes, Expr, Quotes, Type}

/**
 * Data type reflection tools.
 *
 * @param q
 *   quotation context
 */
private[automorph] final case class ClassReflection(q: Quotes):

  // All meta-programming data types are path-dependent on the compiler-generated reflection context
  import q.reflect.{Flags, MethodType, PolyType, Printer, Symbol, TypeRepr}

  private given Quotes =
    q

  final case class RefParameter(name: String, dataType: TypeRepr, contextual: Boolean):

    def lift: Parameter =
      Parameter(name, dataType.show(using Printer.TypeReprShortCode), contextual)

  final case class RefMethod(
    name: String,
    resultType: TypeRepr,
    parameters: Seq[Seq[RefParameter]],
    typeParameters: Seq[RefParameter],
    public: Boolean,
    available: Boolean,
    symbol: Symbol,
  ):

    def lift: Method =
      Method(
        name,
        resultType.show(using Printer.TypeReprShortCode),
        parameters.map(_.map(_.lift)),
        typeParameters.map(_.lift),
        public = public,
        available = available,
        symbol.docstring,
      )

  /**
   * Describes class methods within quoted context.
   *
   * @param classType
   *   class type representation
   * @return
   *   quoted class method descriptors
   */
  def methods(classType: TypeRepr): Seq[RefMethod] =
    classType.typeSymbol.methodMembers.filterNot(_.isClassConstructor).flatMap(method(classType, _))

  private def method(classType: TypeRepr, methodSymbol: Symbol): Option[RefMethod] =
    val (symbolType, typeParameters) = classType.memberType(methodSymbol) match
      case polyType: PolyType =>
        val typeParameters = polyType.paramNames.zip(polyType.paramBounds.indices).map { (name, index) =>
          RefParameter(name, polyType.param(index), false)
        }
        (polyType.resType, typeParameters)
      case otherType => (otherType, Seq.empty)
    symbolType match
      case methodType: MethodType =>
        val (parameters, resultType) = methodSignature(methodType)
        Some(RefMethod(
          methodSymbol.name,
          resultType,
          parameters,
          typeParameters,
          publicMethod(methodSymbol),
          availableMethod(methodSymbol),
          methodSymbol,
        ))
      case _ => None

  private def methodSignature(methodType: MethodType): (Seq[Seq[RefParameter]], TypeRepr) =
    val methodTypes = LazyList.iterate(Option(methodType)) {
      case Some(currentType) => currentType.resType match
          case resultType: MethodType => Some(resultType)
          case _ => None
      case _ => None
    }.takeWhile(_.isDefined).flatten
    val parameters = methodTypes.map { currentType =>
      currentType.paramNames.zip(currentType.paramTypes).map { (name, dataType) =>
        RefParameter(name, dataType, currentType.isImplicit)
      }
    }
    val resultType = methodTypes.last.resType
    (Seq(parameters*), resultType)

  private def publicMethod(methodSymbol: Symbol): Boolean =
    !matchesFlags(methodSymbol.flags, Seq(Flags.Private, Flags.PrivateLocal, Flags.Protected, Flags.Synthetic))

  private def availableMethod(methodSymbol: Symbol): Boolean =
    !matchesFlags(methodSymbol.flags, Seq(Flags.Erased, Flags.Inline, Flags.Invisible, Flags.Macro, Flags.Transparent))

  private def matchesFlags(flags: Flags, matchingFlags: Seq[Flags]): Boolean =
    matchingFlags.foldLeft(false)((result, current) => result | flags.is(current))
