package org.jetbrains.plugins.scala.lang.resolve

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.ImplicitArgumentsClause
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction.CommonNames
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtension, ScFunction, ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.{ImplicitResult, ImplicitState, NoResult}
import org.jetbrains.plugins.scala.lang.psi.types.Signature.ExportedSigInfo
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceTypes
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectContextOwner}
import org.jetbrains.plugins.scala.util.HashBuilder._

import scala.annotation.tailrec

/**
 * Result of Scala reference resolution. Wraps a resolved PSI element with all metadata
 * accumulated during the resolution process: type substitutions, applicability information,
 * implicit conversion details, etc.
 *
 * @param element                      The resolved PSI element (method, val, class, type alias, etc.)
 *
 * @param substitutor                  Type parameter bindings accumulated during resolution.
 *                                     For qualified references like `foo.bar`, includes substitutions
 *                                     from the qualifier's type. Does NOT include constraints from
 *                                     argument type checking (those live in [[applicabilityConstraints]]).
 *
 * @param importsUsed                  Import statements that were used to reach this element.
 *                                     Consumed by "optimize imports" and unused import highlighting.
 *
 * @param renamed                      If the element was imported under an alias, the alias name.
 *                                     {{{import java.util.{List => JList}  // renamed = Some("JList")}}}
 *
 * @param problems                     Applicability problems found during type checking
 *                                     (e.g., TypeMismatch, MissedValueParameter, ExcessArgument).
 *                                     Empty means the candidate is applicable.
 *
 * @param implicitConversion           If this member was resolved through an implicit conversion,
 *                                     the resolve result of the conversion itself.
 *                                     {{{
 *                                     implicit class RichInt(n: Int) { def isEven: Boolean = ... }
 *                                     42.isEven  // isEven's SRR has implicitConversion = Some(SRR for RichInt)
 *                                     }}}
 *
 * @param implicitConversionResultType The type the qualifier was converted to via implicit conversion.
 *                                     In the example above, `implicitConversionResultType = Some(RichInt)`.
 *                                     Used by inspections to determine the actual receiver type.
 *
 * @param innerResolveResult           For sugared apply/update calls, the resolve result of the
 *                                     original object before `.apply`/`.update` expansion.
 *                                     {{{
 *                                     val m = Map.empty[String, Int]
 *                                     m("key")  // resolves to Map.apply; innerResolveResult = SRR for `m`
 *                                     }}}
 *
 * @param parentElement                For constructors: the class/type alias. For apply/unapply methods:
 *                                     the containing object or val.
 *                                     {{{
 *                                     new Foo(1)     // element = Foo.<init>, parentElement = Some(Foo)
 *                                     Foo(1)         // element = Foo.apply, parentElement = Some(Foo)
 *                                     }}}
 *
 * @param isNamedParameter             Whether this resolved to a named parameter in a method call.
 *                                     {{{foo(name = "bar")  // "name" reference has isNamedParameter = true}}}
 *
 * @param fromType                     The qualifier's type from which this member was accessed.
 *                                     For `foo.bar`, this is the type of `foo`.
 *
 * @param tuplingUsed                  Whether auto-tupling was needed to make the call applicable.
 *                                     {{{
 *                                     def f(t: (Int, Int)): Unit = ...
 *                                     f(1, 2)  // auto-tupled to f((1, 2)); tuplingUsed = true
 *                                     }}}
 *                                     @TODO: This is not entirely reliable as applicability checking is lazy, consumers that check
 *                                            ScMethodCall(ref: ScReferenceExpression, _) if ref.bind().exists(_.tuplingUsed)
 *                                            might get incorrect results, if for example, tupling is used in a later param clause.
 *                                            This should instead be migrated to MethodInvocationImpl and calculated in innerType
 *
 * @param isAssignment                 Whether this is an assignment context (e.g., `foo.bar = x`
 *                                     resolving the setter `bar_=`).
 *
 * @param isAccessible                 Whether the element is accessible from the reference site
 *                                     (visibility/access modifier check passed).
 *
 * @param applicabilityConstraints     Unsolved type inference constraints from applicability checking
 *                                     during overload resolution. Kept separate from [[substitutor]]
 *                                     because the substitutor feeds into reference type computation,
 *                                     while these constraints are only consumed by
 *                                     [[org.jetbrains.plugins.scala.lang.psi.implicits.ExtensionConversionData]]
 *                                     and type argument hint display.
 *
 * @param prefixCompletion             Used by code completion: whether this result needs a qualifying prefix.
 *
 * @param nameArgForDynamic            For `scala.Dynamic` dispatch, the method name string passed to
 *                                     `selectDynamic`/`applyDynamic`/`updateDynamic`.
 *                                     {{{
 *                                     x.foo  // if x extends Dynamic: nameArgForDynamic = Some("foo")
 *                                     }}}
 *
 * @param isForwardReference           Whether the reference points to a declaration that appears
 *                                     later in the source (forward reference).
 *
 * @param inferredType                 The inferred value type of this implicit candidate after type
 *                                     parameter inference. Set by [[org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector]]
 *                                     during implicit search.
 *                                     {{{
 *                                     implicit def ord[T: Numeric]: Ordering[T] = ...
 *                                     // when found for Ordering[Int]: inferredType = Some(Ordering[Int])
 *                                     }}}
 *
 * @param implicitArguments            Resolved implicit parameter clauses. Each clause is a sequence
 *                                     of resolve results for individual implicit parameters.
 *
 * @param implicitReason               Why implicit search succeeded or failed for this candidate
 *                                     (OkResult, TypeDoesntConformResult, DivergedImplicitResult, etc.).
 *
 * @param implicitSearchState          The implicit search state that produced this result.
 *                                     Contains the searched type, place, and recursion depth.
 *                                     Used for error reporting and further implicit resolution.
 *
 * @param unresolvedTypeParameters     Type parameters that couldn't be fully inferred during resolution.
 *
 * @param implicitScopeType            If this implicit was found via implicit scope search (not lexical scope),
 *                                     the type whose companion object provided it. Used for deduplication
 *                                     during implicit search.
 *
 * @param isExtensionCall              Whether this is a Scala 3 extension method invocation.
 *
 * @param extensionContext             The enclosing `extension` block at the call site, if any.
 *                                     Used to determine whether extension clauses should be dropped
 *                                     from the method's polymorphic type.
 *
 * @param intersectedReturnType        If this result was created from an intersected/merged signature
 *                                     (e.g., during linearization), the merged return type.
 *
 * @param matchClauseSubstitutor       Type narrowing substitutor accumulated from pattern match scrutinee
 *                                     during upward scope traversal.
 *                                     {{{
 *                                     x match { case s: String => s.length }
 *                                     // resolving `length` carries matchClauseSubstitutor with x := String
 *                                     }}}
 *                                     See [[https://www.scala-lang.org/files/archive/spec/2.13/08-pattern-matching.html#type-parameter-inference-in-patterns Type Inference in Patterns]]
 *
 * @param exportedInfo                 If resolved through a Scala 3 `export` statement, carries the
 *                                     export owner (extension or template body) and the qualifier type.
 *
 * @param isExtensionFromGiven         Whether this extension method was found inside a `given` instance
 *                                     during implicit search.
 */
class ScalaResolveResult(
  val element:                        PsiNamedElement,
  val substitutor:                    ScSubstitutor                = ScSubstitutor.empty,
  val importsUsed:                    Set[ImportUsed]              = Set.empty,
  val renamed:                        Option[String]               = None,
  val problems:                       Seq[ApplicabilityProblem]    = Seq.empty,
  val implicitConversion:             Option[ScalaResolveResult]   = None,
  val implicitConversionResultType:   Option[ScType]               = None,
  val innerResolveResult:             Option[ScalaResolveResult]   = None,
  val parentElement:                  Option[PsiNamedElement]      = None,
  val isNamedParameter:               Boolean                      = false,
  val fromType:                       Option[ScType]               = None,
  val tuplingUsed:                    Boolean                      = false,
  val isAssignment:                   Boolean                      = false,
  val isAccessible:                   Boolean                      = true,
  val applicabilityConstraints:       ConstraintSystem             = ConstraintSystem.empty,
  val prefixCompletion:               Boolean                      = false,
  val nameArgForDynamic:              Option[String]               = None,
  val isForwardReference:             Boolean                      = false,
  val inferredType:                   Option[ScType]               = None,
  val implicitArguments:              Seq[ImplicitArgumentsClause] = Seq.empty,
  val implicitReason:                 ImplicitResult               = NoResult,
  val implicitSearchState:            Option[ImplicitState]        = None,
  val unresolvedTypeParameters:       Option[Seq[TypeParameter]]   = None,
  val implicitScopeType:              Option[ScType]               = None,
  val isExtensionCall:                Boolean                      = false,
  val extensionContext:               Option[ScExtension]          = None,
  val intersectedReturnType:          Option[ScType]               = None,
  val matchClauseSubstitutor:         ScSubstitutor                = ScSubstitutor.empty,
  val exportedInfo:                   Option[ExportedSigInfo]      = None,
  val isExtensionFromGiven:           Boolean                      = false,
) extends ResolveResult
    with ProjectContextOwner {
  if (element == null) throw new NullPointerException("element is null")

  override implicit def projectContext: ProjectContext = element.getProject

  override def getElement: PsiNamedElement = element

  lazy val name: String = element.name

  /**
   * this is important to get precedence information
   *
   * @todo investigate what is this method for and add some more meaningful scaladoc<br>
   *       Some observed/inferred examples:
   *        - for resolved java class constructor it returns the original class reference
   *        - for resolved sugared apply method call (e.g. `Map()`) returns te containing object (or any type definition?)
   */
  def getActualElement: PsiNamedElement =
    parentElement.getOrElse(element)

  def isApplicable(withExpectedType: Boolean = false): Boolean =
    if (withExpectedType) problems.isEmpty
    else                  problems.forall(_ == ExpectedTypeMismatch)

  /**
   * If this element (function definition) was resolved, while processing export statements
   * inside an extension body, return said extension. This is important, because any attempt
   * to calculate type of this function has to rely on is being extension method or not, which is
   * now (with the introduction of exports in extensions) not as simple as just calling .extensionMethodOwner.
   */
  def exportedInExtension: Option[ScExtension] = exportedInfo.flatMap(_.exportedIn.getContext.asOptionOf[ScExtension])

  /**
   * Useful when typing a reference to [[ScFunction]].
   * See: [[MethodTypeProvider.polymorphicType()]] `dropExtensionClauses` parameter.
   */
  def shouldDropExtensionClauses: Boolean = element match {
    case fun: ScFunction =>
      isExtensionCall ||
        (extensionContext.nonEmpty && fun.extensionMethodOwner == extensionContext)
    case _ => false
  }

  override def isValidResult: Boolean = isAccessible && isApplicable()

  def isRenamed: Option[String] = renamed

  def implicitFunction: Option[PsiNamedElement] = implicitConversion.map(_.element)

  def isDynamic: Boolean = nameArgForDynamic.nonEmpty

  def isNotFoundImplicitParameter : Boolean = problems.size == 1 && problems.head.isInstanceOf[NotFoundImplicitParameter]
  // TODO Seems to be unreliable, so it's better to check whether ImplicitCollector.probableArgumentsFor(it).size > 1
  def isAmbiguousImplicitParameter: Boolean = problems.size == 1 && problems.head.isInstanceOf[AmbiguousImplicitParameters]

  def isImplicitParameterProblem: Boolean = isNotFoundImplicitParameter || isAmbiguousImplicitParameter

  def copy(
    subst:                          ScSubstitutor                = substitutor,
    problems:                       Seq[ApplicabilityProblem]    = problems,
    innerResolveResult:             Option[ScalaResolveResult]   = innerResolveResult,
    tuplingUsed:                    Boolean                      = tuplingUsed,
    isAssignment:                   Boolean                      = isAssignment,
    isAccessible:                   Boolean                      = isAccessible,
    applicabilityConstraints:       ConstraintSystem             = applicabilityConstraints,
    nameArgForDynamic:              Option[String]               = nameArgForDynamic,
    isForwardReference:             Boolean                      = isForwardReference,
    inferredType:                   Option[ScType]               = inferredType,
    importsUsed:                    Set[ImportUsed]              = importsUsed,
    implicitArguments:              Seq[ImplicitArgumentsClause] = implicitArguments,
    implicitReason:                 ImplicitResult               = implicitReason,
    implicitSearchState:            Option[ImplicitState]        = implicitSearchState,
    unresolvedTypeParameters:       Option[Seq[TypeParameter]]   = unresolvedTypeParameters,
    implicitScopeType:              Option[ScType]               = implicitScopeType,
    isExtensionCall:                Boolean                      = isExtensionCall,
    extensionContext:               Option[ScExtension]          = extensionContext,
    matchClauseSubstitutor:         ScSubstitutor                = matchClauseSubstitutor,
    intersectedReturnType:          Option[ScType]               = intersectedReturnType,
    exportedInfo:                   Option[ExportedSigInfo]      = exportedInfo,
    parentElement:                  Option[PsiNamedElement]      = parentElement,
    isExtensionFromGiven:           Boolean                      = isExtensionFromGiven
  ): ScalaResolveResult =
    new ScalaResolveResult(
      element,
      subst,
      importsUsed,
      renamed,
      problems,
      implicitConversion,
      implicitConversionResultType,
      innerResolveResult,
      parentElement,
      isNamedParameter,
      fromType,
      tuplingUsed,
      isAssignment,
      isAccessible,
      applicabilityConstraints,
      nameArgForDynamic              = nameArgForDynamic,
      isForwardReference             = isForwardReference,
      inferredType                   = inferredType,
      implicitArguments              = implicitArguments,
      implicitReason                 = implicitReason,
      implicitSearchState            = implicitSearchState,
      unresolvedTypeParameters       = unresolvedTypeParameters,
      implicitScopeType              = implicitScopeType,
      isExtensionCall                = isExtensionCall,
      extensionContext               = extensionContext,
      matchClauseSubstitutor         = matchClauseSubstitutor,
      intersectedReturnType          = intersectedReturnType,
      exportedInfo                   = exportedInfo,
      isExtensionFromGiven           = isExtensionFromGiven,
    )

  override def equals(other: Any): Boolean = other match {
    case rr: ScalaResolveResult =>
      (element eq rr.element) &&
        renamed == rr.renamed &&
        implicitFunction == rr.implicitFunction &&
        innerResolveResult == rr.innerResolveResult && {
          val substedImplicitScope = implicitScopeType.map(substitutor)
          val otherSubstedImplicitScope = rr.implicitScopeType.map(rr.substitutor)
          substedImplicitScope == otherSubstedImplicitScope
        } && exportedInfo == rr.exportedInfo
    case _ => false
  }

  override def hashCode: Int =
    element #+ innerResolveResult #+ renamed #+ implicitFunction #+ implicitScopeType

  override def toString: String =  {
    val name = element match {
      case named: ScNamedElement => named.name
      case it => it.toString
    }
    s"""$name [${problems.mkString(", ")}]"""
  }

  def nameInScope: String = isRenamed.getOrElse(name)

  lazy val qualifiedNameId: String = ScalaResolveResult.toStringRepresentation(this)

  private var precedence = -1

  private def containingPackageName(clazz: PsiClass): Option[String] = {
    val containingClass = clazz.containingClass
    containingClass match {
      case null =>
        //noinspection ScalaWrongMethodsUsage
        val fqn = clazz.qualifiedName
        Some(qualifier(fqn))
      case o: ScObject if o.isPackageObject =>
        Some(o.qualifiedName)
      case _ =>
        None
    }
  }

  private def qualifier(fqn: String): String =
    if (fqn == null) "" else {
      val lastDot = fqn.lastIndexOf('.')
      if (lastDot > 0)
        fqn.substring(0, lastDot)
      else
        ""
    }

  /**
    * See [[org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceTypes]]
    */
  def getPrecedence(
    place:            PsiElement,
    placePackageName: =>String,
    precedenceTypes:  PrecedenceTypes
  ): Int = {
    import precedenceTypes._

    def getPackagePrecedence(packageFqn: String): Int =
      defaultImportPrecedence(qualifier(packageFqn)).getOrElse(PACKAGE_LOCAL_PACKAGE)

    def getClazzPrecedence(clazz: PsiClass): Int = {
      val packageNameOpt = containingPackageName(clazz)
      packageNameOpt match {
        case None =>
          OTHER_MEMBERS //is local or inherited
        case Some(packageName) =>
          getMemberPrecedence(packageName, clazz)
      }
    }

    def getMemberPrecedence(packageName: String, member: PsiMember): Int =
      defaultImportPrecedence(packageName).getOrElse {
        //NOTE: we don't use `clazz.getContainingFile` here e.g. for this reason:
        // clazz might be an instance of ScTypeParameterImpl, which is instance of `PsiClassFake` (for whatever reason ¯\_(ツ)_/¯)
        // and it's `getContainingFile` returns DummyHolder
        // For optimization we may try:
        //  - explicitly checking whether it's a synthetic
        //  - do getContext.getContainingFile (getContainingFile might have optimized implementation which just reads from the cached field, containing class)
        val fileContext = ScalaPsiUtil.fileContext(member)
        val isPlaceFromTheSameUnit = PsiTreeUtil.isContextAncestor(fileContext, place, false)
        if (isPlaceFromTheSameUnit) OTHER_MEMBERS
        else if (packageName == placePackageName) SAME_PACKAGE
        else PACKAGING
      }

    def getPrecedenceInner: Int = {
      val actualElement = getActualElement
      if (importsUsed.isEmpty) {
        val nameContext = actualElement.nameContext
        nameContext match {
          case obj: ScObject if obj.isPackageObject =>
            val qualifier = obj.qualifiedName
            getPackagePrecedence(qualifier)
          case pack: PsiPackage =>
            val qualifier = pack.getQualifiedName
            getPackagePrecedence(qualifier)
          case clazz: PsiClass =>
            getClazzPrecedence(clazz)
          case member: PsiMember =>
            //TODO: unify this branch can be unified with `getClazzPrecedence` in 2022.3
            //  maybe we will need to review how qualifiedName caching is implemented for top-level ScMembers

            val container = PsiTreeUtil.getContextOfType(actualElement, false, classOf[PsiClass], classOf[ScPackaging], classOf[ScalaFile])
            val maybeContainingPackageOrPackageObjectName: Option[String] = container match {
              case o: ScObject if o.isPackageObject => Option(o.qualifiedName)
              case p: ScPackaging                   => Option(p.fullPackageName) //top level definition
              case _: ScalaFile                     => Some("") //top level definition in root package (no container `packaging` statement)
              case _                                => None
            }
            maybeContainingPackageOrPackageObjectName match {
              case Some(packageName) =>
                getMemberPrecedence(packageName, member)
              case _ =>
                container match {
                  case containingClass: PsiClass =>
                    val fqn = containingClass.qualifiedName
                    fqn match {
                      case "scala.LowPriorityImplicits" =>
                        defaultImportPrecedence("scala.Predef").getOrElse(OTHER_MEMBERS)
                      case _ =>
                        defaultImportPrecedence(fqn).getOrElse(OTHER_MEMBERS)
                    }
                  case _ =>
                    OTHER_MEMBERS
                }
            }
          case _ =>
            OTHER_MEMBERS
        }
      }
      else {
        val importsUsedSeq = importsUsed.toSeq
        val importUsed     = importsUsedSeq.last
        val importStmt     = importUsed.importExpr.map(_.getParent).filterByType[ScImportStmt]
        val isTopLevel     = importStmt.exists(_.getParent.is[ScPackaging, PsiFile])

        // TODO this conflates imported functions and imported implicit views. ScalaResolveResult should really store
        //      these separately.
        val isWildcard = importUsed match {
          case _: ImportWildcardSelectorUsed => true
          case ImportExprUsed(expr)          => expr.hasWildcardSelector
          case _                             => false
        }

        val isPackage = actualElement match {
          case _: PsiPackage => true
          case o: ScObject   => o.isPackageObject
          case _             => false
        }

        importPrecedence(place, isPackage, isWildcard, isTopLevel)
      }
    }

    if (precedence == -1) {
      precedence = getPrecedenceInner
    }
    precedence
  }

  @tailrec
  final def mostInnerResolveResult: ScalaResolveResult =
    innerResolveResult match {
      case Some(inner) => inner.mostInnerResolveResult
      case None        => this
    }

  //for name-based extractor
  def isEmpty: Boolean = false
  def get: ScalaResolveResult = this
  def _1: PsiNamedElement = element
  def _2: ScSubstitutor = substitutor
}

object ScalaResolveResult {
  def empty = new ScalaResolveResult(null, ScSubstitutor.empty, Set[ImportUsed]())

  def unapply(r: ScalaResolveResult): ScalaResolveResult = r

  object withActual {
    def unapply(r: ScalaResolveResult): Some[PsiNamedElement] = Some(r.getActualElement)
  }

  object ApplyMethodInnerResolve {
    def unapply(srr: ScalaResolveResult): Option[ScalaResolveResult] = {
      if (srr.name != CommonNames.Apply) None
      else {
        var current = srr.innerResolveResult
        while (current.exists(r => r.name == CommonNames.Apply && r.innerResolveResult.isDefined)) {
          current = current.flatMap(_.innerResolveResult)
        }
        current
      }
    }
  }

  val EMPTY_ARRAY = Array.empty[ScalaResolveResult]

  implicit val arrayFactory: ArrayFactory[ScalaResolveResult] = (count: Int) =>
    if (count == 0) EMPTY_ARRAY else new Array[ScalaResolveResult](count)

  implicit class ScalaResolveResultExt(private val resolveResult: ScalaResolveResult) extends AnyVal {

    def createLookupElement(qualifierType: Option[ScType] = None,
                            isClassName: Boolean = false,
                            isInImport: Boolean = false,
                            shouldImport: Boolean = false,
                            isInStableCodeReference: Boolean = false,
                            containingClass: Option[PsiClass] = None,
                            isLocalVariable: Boolean = false,
                            isInSimpleString: Boolean = false,
                            isInInterpolatedString: Boolean = false,
                            isInStableElementPattern: Boolean = false)(implicit context: Context): ScalaLookupItem = {
      val ScalaResolveResult(element, substitutor) = resolveResult
      if (!element.isValid) {
        throw new IllegalArgumentException(s"`$element` is supposed to be valid (please consider using ${classOf[com.intellij.openapi.application.ReadAction].getName})")
      }

      val isCurrentClassMember: Boolean = {
        val classExtractedFromType: Option[PsiClass] = {
          val fromType = resolveResult.fromType

          def isPredef = fromType.exists(_.presentableText(TypePresentationContext.emptyContext, Context.Empty) == "Predef.type")

          import resolveResult.projectContext
          val maybeType = qualifierType.orElse(fromType).map(_.widen)
          maybeType.getOrElse(api.Nothing) match {
            case qualType if !isPredef && resolveResult.importsUsed.isEmpty =>
              qualType.extractDesignated(expandAliases = true).flatMap {
                case clazz: PsiClass => Some(clazz)
                case Typeable(tp) => tp.extractClass
                case _ => None
              }
            case _ => None
          }
        }

        val clazz = classExtractedFromType.orElse(containingClass)
        clazz.exists { expectedClass =>
          element.nameContext match {
            case m: PsiMember =>
              m.containingClass match {
                //allow boldness only if current class is package object, not element available from package object
                case packageObject: ScObject if packageObject.isPackageObject && packageObject == expectedClass =>
                  containingClass.contains(packageObject)
                case clazz => clazz == expectedClass
              }
            case _ => false
          }
        }
      }

      val Setter = """(.*)_=""".r
      val isRenamed = resolveResult.isRenamed.filter(element.name != _)
      val (name, isAssignment) = isRenamed.getOrElse(element.name) match {
        case Setter(string) if !element.is[FakePsiMethod] => // if the element is a fake psi method, then the setter's already been generated from var
          (string, true)
        case string =>
          (string, false)
      }

      val result = new ScalaLookupItem(element, name, containingClass)
      result.isClassName = isClassName
      result.isNamedParameter = resolveResult.isNamedParameter
      result.isRenamed = isRenamed
      result.isUnderlined = resolveResult.implicitFunction.isDefined
      result.isAssignment = isAssignment
      result.isInImport = isInImport
      result.bold = isCurrentClassMember
      result.shouldImport = shouldImport
      result.isInStableCodeReference = isInStableCodeReference
      result.substitutor = substitutor
      result.prefixCompletion = resolveResult.prefixCompletion
      result.isLocalVariable = isLocalVariable
      result.isInSimpleString = isInSimpleString
      result.isInInterpolatedString = isInInterpolatedString
      result.isInStableElementPattern = isInStableElementPattern

      result
    }
  }

  private def toStringRepresentation(result: ScalaResolveResult): String = {
    def defaultForTypeAlias(t: ScTypeAlias): String = {
      if (t.getParent.isInstanceOf[ScTemplateBody] && t.containingClass != null) {
        "TypeAlias:" + t.containingClass.qualifiedName + "#" + t.name
      } else null
    }

    val actualElement = result.getActualElement
    val presentation = actualElement match {
      case _: ScTypeParam => null
      case c: ScObject => "Object:" + c.qualifiedName
      case c: PsiClass => "Class:" + c.qualifiedName
      case t: ScTypeAliasDefinition if t.typeParameters.isEmpty =>
        t.aliasedType match {
          case Right(tp) =>
            tp.extractClass match {
              case Some(_: ScObject) => defaultForTypeAlias(t)
              case Some(td: ScTypeDefinition) if td.typeParameters.isEmpty && ScalaPsiUtil.hasStablePath(td) =>
                "Class:" + td.qualifiedName
              case Some(c: PsiClass) if c.getTypeParameters.isEmpty => "Class:" + c.qualifiedName
              case _ => defaultForTypeAlias(t)
            }
          case _ => defaultForTypeAlias(t)
        }
      case t: ScTypeAlias => defaultForTypeAlias(t)
      case p: PsiPackage => "Package:" + p.getQualifiedName
      case _ => null
    }
    presentation
  }
}
