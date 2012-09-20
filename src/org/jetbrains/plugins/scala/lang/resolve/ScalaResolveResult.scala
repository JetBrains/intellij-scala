package org.jetbrains.plugins.scala
package lang
package resolve

import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types._
import com.intellij.psi.util.PsiTreeUtil
import psi.ScalaPsiUtil
import psi.impl.toplevel.synthetic.ScSyntheticClass
import psi.api.toplevel.typedef.ScObject
import psi.api.base.patterns.ScBindingPattern
import psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed, ImportUsed}
import extensions.{toPsiClassExt, toPsiNamedElementExt}
import psi.api.statements.params.ScClassParameter
import psi.api.toplevel.ScNamedElement

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
                         val isDynamic: Boolean = false) extends ResolveResult {
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
           isAccessible: Boolean = isAccessible, resultUndef: Option[ScUndefinedSubstitutor] = None, isDynamic: Boolean = isDynamic): ScalaResolveResult =
    new ScalaResolveResult(element, subst, importsUsed, nameShadow, implicitConversionClass, problems, boundClass,
      implicitFunction, implicitType, defaultParameterUsed, innerResolveResult, parentElement,
      isNamedParameter, fromType, tuplingUsed, isSetterFunction, isAssignment, notCheckedResolveResult,
      isAccessible, resultUndef, isDynamic = isDynamic)

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
   * 1 - java.lang
   * 2 - scala, scala.Predef
   * 3 - package local
   * 4 - wildcard import
   * 5 - import
   * 6 - val/var class parameter
   * 7 - other members and local values
   */
  def getPrecedence(place: PsiElement, placePackageName: => String): Int = {
    def getPrecedenceInner: Int = {
      def getPackagePrecedence(qualifier: String): Int = {
        if (qualifier == null) return 7
        val index: Int = qualifier.lastIndexOf('.')
        if (index == -1) return 3
        val q = qualifier.substring(0, index)
        if (q == "java.lang") return 1
        else if (q == "scala") return 2
        else if (q == placePackageName) return 7
        else return 3
      }
      def getClazzPrecedence(clazz: PsiClass): Int = {
        val qualifier = clazz.qualifiedName
        if (qualifier == null) return 7
        val index: Int = qualifier.lastIndexOf('.')
        if (index == -1) return 7
        val q = qualifier.substring(0, index)
        if (q == "java.lang") return 1
        else if (q == "scala") return 2
        else if (PsiTreeUtil.isContextAncestor(clazz.getContainingFile, place, true)) return 7
        else return 3
      }
      if (importsUsed.size == 0) {
        ScalaPsiUtil.nameContext(getActualElement) match {
          case synthetic: ScSyntheticClass => return 2 //like scala.Int
          case obj: ScObject if obj.isPackageObject =>
            val qualifier = obj.qualifiedName
            return getPackagePrecedence(qualifier)
          case pack: PsiPackage =>
            val qualifier = pack.getQualifiedName
            return getPackagePrecedence(qualifier)
          case clazz: PsiClass =>
            return getClazzPrecedence(clazz)
          case memb@(_: ScBindingPattern | _: PsiMember) => {
            val clazzStub = ScalaPsiUtil.getContextOfType(getActualElement, false, classOf[PsiClass])
            val clazz: PsiClass = clazzStub match {
              case clazz: PsiClass => clazz
              case _ => null
            }
            //val clazz = PsiTreeUtil.getParentOfType(result.getActualElement, classOf[PsiClass])
            if (clazz == null) return 7
            else {
              clazz.qualifiedName match {
                case "scala.Predef" => return 2
                case "scala.LowPriorityImplicits" => return 2
                case "scala" => return 2
                case _ =>
                  memb match {
                    case param: ScClassParameter if param.isEffectiveVal &&
                      !PsiTreeUtil.isContextAncestor(clazz, place, true) => return 6
                    case _ => return 7
                  }
              }
            }
          }
          case _ =>
        }
        return 7
      }
      val importsUsedSeq = importsUsed.toSeq
      val importUsed: ImportUsed = importsUsedSeq.apply(importsUsedSeq.length - 1)
      // TODO this conflates imported functions and imported implicit views. ScalaResolveResult should really store
      //      these separately.
      importUsed match {
        case _: ImportWildcardSelectorUsed => 4
        case _: ImportSelectorUsed => 5
        case ImportExprUsed(expr) =>
          if (expr.singleWildcard) 4
          else 5
      }
    }
    if (precedence == -1) {
      precedence = getPrecedenceInner
    }
    precedence
  }

}
