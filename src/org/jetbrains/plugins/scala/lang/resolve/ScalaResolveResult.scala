package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types._
import psi.ScalaPsiUtil
import psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScTypeDefinition, ScObject}
import psi.api.base.patterns.ScBindingPattern
import psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportUsed}
import extensions.{toPsiClassExt, toPsiNamedElementExt}
import psi.api.toplevel.ScNamedElement
import scala.annotation.tailrec
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import com.intellij.psi.util.PsiTreeUtil

object ScalaResolveResult {
  def empty = new ScalaResolveResult(null, ScSubstitutor.empty, Set[ImportUsed]())

  def unapply(r: ScalaResolveResult): Some[(PsiNamedElement, ScSubstitutor)] = Some(r.element, r.substitutor)
}

class ScalaResolveResult(val element: PsiNamedElement,
                         val substitutor: ScSubstitutor = ScSubstitutor.empty,
                         val importsUsed: collection.Set[ImportUsed] = collection.Set[ImportUsed](),
                         val nameShadow: Option[String] = None,
                         val implicitConversionClass: Option[PsiClass] = None,
                         val problems: Seq[ApplicabilityProblem] = Seq.empty,
                         val boundClass: PsiClass = null,
                         val implicitFunction: Option[PsiNamedElement] = None,
                         val implicitType: Option[ScType] = None,
                         val defaultParameterUsed: Boolean = false,
                         val innerResolveResult: Option[ScalaResolveResult] = None,
                         val parentElement: Option[PsiNamedElement] = None,
                         val isNamedParameter: Boolean = false,
                         val fromType: Option[ScType] = None,
                         val tuplingUsed: Boolean = false,
                         val isSetterFunction: Boolean = false,
                         val isAssignment: Boolean = false,
                         val notCheckedResolveResult: Boolean = false, 
                         val isAccessible: Boolean = true,
                         val resultUndef: Option[ScUndefinedSubstitutor] = None,
                         val prefixCompletion: Boolean = false,
                         val isDynamic: Boolean = false,
                         val isForwardReference: Boolean = false,
                         val implicitParameterType: Option[ScType] = None,
                         val implicitParameters: Seq[ScalaResolveResult] = Seq.empty) extends ResolveResult {
  if (element == null) throw new NullPointerException("element is null")

  def getElement = element
  
  lazy val name: String = element.name

  /**
   * this is important to get precedence information
   */
  def getActualElement = {
    parentElement match {
      case Some(e) => e
      case None => element
    }
  }

  def isApplicable: Boolean = problems.isEmpty

  def isApplicableInternal: Boolean = {
    innerResolveResult match {
      case Some(r) => r.isApplicable
      case None => isApplicable
    }
  }

  def isValidResult = isAccessible && isApplicable

  def isCyclicReference = false

  def isRenamed: Option[String] = nameShadow

  def copy(subst: ScSubstitutor = substitutor, problems: Seq[ApplicabilityProblem] = problems,
           defaultParameterUsed: Boolean = defaultParameterUsed,
           innerResolveResult: Option[ScalaResolveResult] = innerResolveResult,
           tuplingUsed: Boolean = tuplingUsed,
           isSetterFunction: Boolean = isSetterFunction,
           isAssignment: Boolean = isAssignment,
           notCheckedResolveResult: Boolean = notCheckedResolveResult, 
           isAccessible: Boolean = isAccessible, resultUndef: Option[ScUndefinedSubstitutor] = None,
           isDynamic: Boolean = isDynamic,
           isForwardReference: Boolean = isForwardReference,
           implicitParameterType: Option[ScType] = implicitParameterType,
           importsUsed: collection.Set[ImportUsed] = importsUsed,
           implicitParameters: Seq[ScalaResolveResult] = implicitParameters): ScalaResolveResult =
    new ScalaResolveResult(element, subst, importsUsed, nameShadow, implicitConversionClass, problems, boundClass,
      implicitFunction, implicitType, defaultParameterUsed, innerResolveResult, parentElement,
      isNamedParameter, fromType, tuplingUsed, isSetterFunction, isAssignment, notCheckedResolveResult,
      isAccessible, resultUndef, isDynamic = isDynamic, isForwardReference = isForwardReference,
      implicitParameterType = implicitParameterType, implicitParameters = implicitParameters)

  //In valid program we should not have two resolve results with the same element but different substitutor,
  // so factor by element
  override def equals(other: Any): Boolean = other match {
    case rr: ScalaResolveResult =>
      if (element ne rr.element) return false
      if (nameShadow != rr.nameShadow) return false
      innerResolveResult == rr.innerResolveResult
    case _ => false
  }

  override def hashCode: Int = element.hashCode + innerResolveResult.hashCode() * 31 + nameShadow.hashCode() * 31 * 31

  override def toString =  {
    val name = element match {
      case named: ScNamedElement => named.name
      case it => it.toString
    }
    s"""$name [${problems.mkString(", ")}]"""
  }

  private var precedence = -1

  /**
   * See [[org.jetbrains.plugins.scala.lang.resolve.processor.PrecedenceHelper.PrecedenceTypes]]
   */
  def getPrecedence(place: PsiElement, placePackageName: => String): Int = {
    import org.jetbrains.plugins.scala.lang.resolve.processor.PrecedenceHelper.PrecedenceTypes._
    def getPrecedenceInner: Int = {
      def getPackagePrecedence(qualifier: String): Int = {
        if (qualifier == null) return OTHER_MEMBERS
        val index: Int = qualifier.lastIndexOf('.')
        if (index == -1) return PACKAGE_LOCAL_PACKAGE
        val q = qualifier.substring(0, index)
        if (q == "java.lang") JAVA_LANG
        else if (q == "scala") SCALA
        else if (q == placePackageName) OTHER_MEMBERS
        else PACKAGE_LOCAL_PACKAGE
      }
      def getClazzPrecedence(clazz: PsiClass): Int = {
        @tailrec
        def getPackageName(element: PsiElement): String = {
          element match {
            case null => ""
            case o: ScObject if o.isPackageObject =>
              val qualifier = o.qualifiedName
              val packageSuffix: String = ".`package`"
              if (qualifier.endsWith(packageSuffix)) qualifier.substring(0, qualifier.length - packageSuffix.length) else qualifier
            case p: ScPackaging => p.fullPackageName
            case _ => getPackageName(element.getParent)
          }
        }
        val q = clazz match {
          case td: ScTypeDefinition =>
            if (td.containingClass != null) return OTHER_MEMBERS
            getPackageName(td)
          case p: PsiClass =>
            if (p.getContainingClass != null) return OTHER_MEMBERS
            val qualifier = p.getQualifiedName
            if (qualifier == null) return OTHER_MEMBERS
            val index: Int = qualifier.lastIndexOf('.')
            if (index == -1) return OTHER_MEMBERS
            qualifier.substring(0, index)
          case _ =>
        }
        if (q == "java.lang") JAVA_LANG
        else if (q == "scala") SCALA
        else if (q == placePackageName) OTHER_MEMBERS
        else PACKAGE_LOCAL
      }
      if (importsUsed.size == 0) {
        ScalaPsiUtil.nameContext(getActualElement) match {
          case synthetic: ScSyntheticClass => return SCALA //like scala.Int
          case obj: ScObject if obj.isPackageObject =>
            val qualifier = obj.qualifiedName
            return getPackagePrecedence(qualifier)
          case pack: PsiPackage =>
            val qualifier = pack.getQualifiedName
            return getPackagePrecedence(qualifier)
          case clazz: PsiClass =>
            return getClazzPrecedence(clazz)
          case memb@(_: ScBindingPattern | _: PsiMember) =>
            val clazzStub = ScalaPsiUtil.getContextOfType(getActualElement, false, classOf[PsiClass])
            val clazz: PsiClass = clazzStub match {
              case clazz: PsiClass => clazz
              case _ => null
            }
            //val clazz = PsiTreeUtil.getParentOfType(result.getActualElement, classOf[PsiClass])
            if (clazz == null) return OTHER_MEMBERS
            else {
              clazz.qualifiedName match {
                case "scala.Predef" => return SCALA_PREDEF
                case "scala.LowPriorityImplicits" => return SCALA_PREDEF
                case "scala" => return SCALA
                case _ =>
                  clazz match {
                    case o: ScObject if o.isPackageObject  && !PsiTreeUtil.isContextAncestor(o, place, false) =>
                      var q = o.qualifiedName
                      val packageSuffix: String = ".`package`"
                      if (q.endsWith(packageSuffix)) q = q.substring(0, q.length - packageSuffix.length)
                      if (q == placePackageName) return OTHER_MEMBERS
                      else return PACKAGE_LOCAL
                    case _ => return OTHER_MEMBERS
                  }
              }
            }
          case _ =>
        }
        return OTHER_MEMBERS
      }
      val importsUsedSeq = importsUsed.toSeq
      val importUsed: ImportUsed = importsUsedSeq.apply(importsUsedSeq.length - 1)
      // TODO this conflates imported functions and imported implicit views. ScalaResolveResult should really store
      //      these separately.
      importUsed match {
        case _: ImportWildcardSelectorUsed =>
          getActualElement match {
            case p: PsiPackage => WILDCARD_IMPORT_PACKAGE
            case o: ScObject if o.isPackageObject => WILDCARD_IMPORT_PACKAGE
            case _ => WILDCARD_IMPORT
          }
        case _: ImportSelectorUsed =>
          getActualElement match {
            case p: PsiPackage => IMPORT_PACKAGE
            case o: ScObject if o.isPackageObject => IMPORT_PACKAGE
            case _ => IMPORT
          }
        case ImportExprUsed(expr) =>
          if (expr.singleWildcard) {
            getActualElement match {
              case p: PsiPackage => WILDCARD_IMPORT_PACKAGE
              case o: ScObject if o.isPackageObject => WILDCARD_IMPORT_PACKAGE
              case _ => WILDCARD_IMPORT
            }
          } else {
            getActualElement match {
              case p: PsiPackage => IMPORT_PACKAGE
              case o: ScObject if o.isPackageObject => IMPORT_PACKAGE
              case _ => IMPORT
            }
          }
      }
    }
    if (precedence == -1) {
      precedence = getPrecedenceInner
    }
    precedence
  }

}
