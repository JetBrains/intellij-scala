package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ArrayFactory
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScPackaging}
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector.{ImplicitResult, ImplicitState, NoResult}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceTypes
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectContextOwner}
import org.jetbrains.plugins.scala.util.HashBuilder._

/**
 * @param parentElement class for constructor or object/val of `apply/unapply` methods
 */
class ScalaResolveResult(
  val element:                  PsiNamedElement,
  val substitutor:              ScSubstitutor = ScSubstitutor.empty,
  val importsUsed:              Set[ImportUsed] = Set.empty,
  val renamed:                  Option[String] = None,
  val problems:                 Seq[ApplicabilityProblem] = Seq.empty,
  val implicitConversion:       Option[ScalaResolveResult] = None,
  val implicitType:             Option[ScType] = None,
  val defaultParameterUsed:     Boolean = false,
  val innerResolveResult:       Option[ScalaResolveResult] = None,
  val parentElement:            Option[PsiNamedElement] = None,
  val isNamedParameter:         Boolean = false,
  val fromType:                 Option[ScType] = None,
  val tuplingUsed:              Boolean = false,
  val isAssignment:             Boolean = false,
  val notCheckedResolveResult:  Boolean = false,
  val isAccessible:             Boolean = true,
  val resultUndef:              Option[ConstraintSystem] = None,
  val prefixCompletion:         Boolean = false,
  val nameArgForDynamic:        Option[String] = None, //argument to a dynamic call
  val isForwardReference:       Boolean = false,
  val implicitParameterType:    Option[ScType] = None,
  val implicitParameters:       Seq[ScalaResolveResult] = Seq.empty, // TODO Arguments and parameters should not be used inerchangeably
  val implicitReason:           ImplicitResult = NoResult,
  val implicitSearchState:      Option[ImplicitState] = None,
  val unresolvedTypeParameters: Option[Seq[TypeParameter]] = None,
  val implicitScopeObject:      Option[ScType] = None
) extends ResolveResult
    with ProjectContextOwner {
  if (element == null) throw new NullPointerException("element is null")

  override implicit def projectContext: ProjectContext = element.getProject

  override def getElement: PsiNamedElement = element

  lazy val name: String = element.name

  /**
   * this is important to get precedence information
   */
  def getActualElement: PsiNamedElement = {
    parentElement match {
      case Some(e) => e
      case None => element
    }
  }

  def isApplicable(withExpectedType: Boolean = false): Boolean =
    if (withExpectedType) problems.isEmpty
    else problems.forall(_ == ExpectedTypeMismatch)

  def isApplicableInternal(withExpectedType: Boolean): Boolean =
    innerResolveResult match {
      case Some(r) => r.isApplicable(withExpectedType)
      case None    => isApplicable(withExpectedType)
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
    subst:                    ScSubstitutor              = substitutor,
    problems:                 Seq[ApplicabilityProblem]  = problems,
    defaultParameterUsed:     Boolean                    = defaultParameterUsed,
    innerResolveResult:       Option[ScalaResolveResult] = innerResolveResult,
    tuplingUsed:              Boolean                    = tuplingUsed,
    isAssignment:             Boolean                    = isAssignment,
    notCheckedResolveResult:  Boolean                    = notCheckedResolveResult,
    isAccessible:             Boolean                    = isAccessible,
    resultUndef:              Option[ConstraintSystem]   = None,
    nameArgForDynamic:        Option[String]             = nameArgForDynamic,
    isForwardReference:       Boolean                    = isForwardReference,
    implicitParameterType:    Option[ScType]             = implicitParameterType,
    importsUsed:              Set[ImportUsed]            = importsUsed,
    implicitParameters:       Seq[ScalaResolveResult]    = implicitParameters,
    implicitReason:           ImplicitResult             = implicitReason,
    implicitSearchState:      Option[ImplicitState]      = implicitSearchState,
    unresolvedTypeParameters: Option[Seq[TypeParameter]] = unresolvedTypeParameters,
    implicitScopeObject:      Option[ScType]             = implicitScopeObject
  ): ScalaResolveResult =
    new ScalaResolveResult(
      element,
      subst,
      importsUsed,
      renamed,
      problems,
      implicitConversion,
      implicitType,
      defaultParameterUsed,
      innerResolveResult,
      parentElement,
      isNamedParameter,
      fromType,
      tuplingUsed,
      isAssignment,
      notCheckedResolveResult,
      isAccessible,
      resultUndef,
      nameArgForDynamic        = nameArgForDynamic,
      isForwardReference       = isForwardReference,
      implicitParameterType    = implicitParameterType,
      implicitParameters       = implicitParameters,
      implicitReason           = implicitReason,
      implicitSearchState      = implicitSearchState,
      unresolvedTypeParameters = unresolvedTypeParameters,
      implicitScopeObject      = implicitScopeObject
    )

  override def equals(other: Any): Boolean = other match {
    case rr: ScalaResolveResult =>
      (element eq rr.element) &&
        renamed == rr.renamed &&
        implicitFunction == rr.implicitFunction &&
        innerResolveResult == rr.innerResolveResult &&
        implicitScopeObject == rr.implicitScopeObject
    case _ => false
  }

  override def hashCode: Int =
    element #+ innerResolveResult #+ renamed #+ implicitFunction #+ implicitScopeObject

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
    if (clazz.containingClass != null)
      return None

    //noinspection ScalaWrongMethodsUsage
    Some(qualifier(clazz.getQualifiedName))
  }

  private def qualifier(fqn: String): String = {
    val lastDot = Option(fqn).map(_.lastIndexOf('.'))

    lastDot.filter(_ > 0).map(fqn.substring(0, _)).getOrElse("")
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

    def getClazzPrecedence(clazz: PsiClass): Int =
      containingPackageName(clazz) match {
        case None => OTHER_MEMBERS //is local or inherited
        case Some(pckg) =>
          defaultImportPrecedence(pckg).getOrElse {
            val sameFile = ScalaPsiUtil.fileContext(clazz) == ScalaPsiUtil.fileContext(place)

            if (sameFile) OTHER_MEMBERS
            else if (pckg == placePackageName) SAME_PACKAGE
            else PACKAGING
          }
      }

    def getPrecedenceInner: Int = {
      if (importsUsed.isEmpty) {
        ScalaPsiUtil.nameContext(getActualElement) match {
          case obj: ScObject if obj.isPackageObject =>
            val qualifier = obj.qualifiedName
            return getPackagePrecedence(qualifier)
          case pack: PsiPackage =>
            val qualifier = pack.getQualifiedName
            return getPackagePrecedence(qualifier)
          case clazz: PsiClass =>
            return getClazzPrecedence(clazz)
          case _: ScBindingPattern | _: PsiMember =>
            val clazzStub = ScalaPsiUtil.getContextOfType(getActualElement, false, classOf[PsiClass])

            val clazz: PsiClass = clazzStub match {
              case clazz: PsiClass => clazz
              case _               => null
            }

            //val clazz = PsiTreeUtil.getParentOfType(result.getActualElement, classOf[PsiClass])
            if (clazz == null) return OTHER_MEMBERS
            else {
              val fqn = clazz.qualifiedName
              fqn match {
                case "scala.LowPriorityImplicits" =>
                  return defaultImportPrecedence("scala.Predef").getOrElse(OTHER_MEMBERS)
                case _ =>
                  clazz match {
                    case o: ScObject if o.isPackageObject && !PsiTreeUtil.isContextAncestor(o, place, false) =>
                      var q = o.qualifiedName
                      val packageSuffix: String = ".`package`"
                      if (q.endsWith(packageSuffix)) q = q.substring(0, q.length - packageSuffix.length)
                      if (q == placePackageName) return OTHER_MEMBERS
                      else return PACKAGING
                    case _ => return defaultImportPrecedence(fqn).getOrElse(OTHER_MEMBERS)
                  }
              }
            }
          case _ =>
        }
        return OTHER_MEMBERS
      }

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

      val isPackage = getActualElement match {
        case _: PsiPackage => true
        case o: ScObject   => o.isPackageObject
        case _             => false
      }

      importPrecedence(place, isPackage, isWildcard, isTopLevel)
    }

    if (precedence == -1) {
      precedence = getPrecedenceInner
    }
    precedence
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
    def unapply(r: ScalaResolveResult): Option[PsiNamedElement] = Some(r.getActualElement)
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
                            isInInterpolatedString: Boolean = false): ScalaLookupItem = {
      val ScalaResolveResult(element, substitutor) = resolveResult
      if (!element.isValid) {
        throw new IllegalArgumentException(s"`$element` is supposed to be valid (please consider using ${classOf[com.intellij.openapi.application.ReadAction[_]].getName})")
      }

      val isCurrentClassMember: Boolean = {
        val extractedType: Option[PsiClass] = {
          val fromType = resolveResult.fromType

          def isPredef = fromType.exists(_.presentableText(TypePresentationContext.emptyContext) == "Predef.type")

          import resolveResult.projectContext
          qualifierType.orElse(fromType).getOrElse(api.Nothing) match {
            case qualType if !isPredef && resolveResult.importsUsed.isEmpty =>
              qualType.extractDesignated(expandAliases = false).flatMap {
                case clazz: PsiClass => Some(clazz)
                case Typeable(tp) => tp.extractClass
                case _ => None
              }
            case _ => None
          }
        }

        extractedType.orElse(containingClass).exists { expectedClass =>
          ScalaPsiUtil.nameContext(element) match {
            case m: PsiMember =>
              m.containingClass match {
                //allow boldness only if current class is package object, not element availiable from package object
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
        case Setter(string) if !element.isInstanceOf[FakePsiMethod] => // if the element is a fake psi method, then the setter's already been generated from var
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

      result
    }
  }

  private def toStringRepresentation(result: ScalaResolveResult): String = {
    def defaultForTypeAlias(t: ScTypeAlias): String = {
      if (t.getParent.isInstanceOf[ScTemplateBody] && t.containingClass != null) {
        "TypeAlias:" + t.containingClass.qualifiedName + "#" + t.name
      } else null
    }

    result.getActualElement match {
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
  }
}
