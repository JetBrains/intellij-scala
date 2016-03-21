package org.jetbrains.plugins.scala.lang.completion.lookups

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiMethod
import org.jetbrains.plugins.scala.lang.psi.types.result.{TypingContext, TypingContextOwner}
import org.jetbrains.plugins.scala.lang.psi.types.{Nothing, ScType, ScTypeExt, ScalaType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

/**
 * @author Alefas
 * @since 19.03.12
 */
object LookupElementManager {
  def getKeywrodLookupElement(keyword: String, position: PsiElement): LookupElement = {
    ScalaKeywordLookupItem.getLookupElement(keyword, position)
  }

  def getLookupElement(resolveResult: ScalaResolveResult,
                       qualifierType: ScType = Nothing,
                       isClassName: Boolean = false,
                       isInImport: Boolean = false,
                       isOverloadedForClassName: Boolean = false,
                       shouldImport: Boolean = false,
                       isInStableCodeReference: Boolean = false,
                       containingClass: Option[PsiClass] = None,
                       isInSimpleString: Boolean = false,
                       isInInterpolatedString: Boolean = false): Seq[ScalaLookupItem] = {
    val element = resolveResult.element
    val substitutor = resolveResult.substitutor

    def isRenamed = resolveResult.isRenamed.filter(element.name != _)

    def isCurrentClassMember: Boolean = {
      def checkIsExpectedClassMember(expectedClassOption: Option[PsiClass]): Boolean = {
        expectedClassOption.exists { expectedClass =>
          ScalaPsiUtil.nameContext(element) match {
            case m: PsiMember if m.containingClass == expectedClass => true
            case _ => false
          }
        }
      }

      def usedImportForElement = resolveResult.importsUsed.nonEmpty
      def isPredef = resolveResult.fromType.exists(_.presentableText == "Predef.type")

      qualifierType match {
        case _ if !isPredef && !usedImportForElement =>
          ScalaType.extractDesignated(qualifierType, withoutAliases = false) match {
            case Some((named, _)) =>
              val clazz: Option[PsiClass] = named match {
                case cl: PsiClass => Some(cl)
                case tp: TypingContextOwner =>
                  tp.getType(TypingContext.empty).map(_.extractClass()(tp.typeSystem)).getOrElse(None)
                case _ => None
              }
              checkIsExpectedClassMember(clazz)
            case _ => false
          }
        case _ => checkIsExpectedClassMember(containingClass)
      }
    }

    def isDeprecated: Boolean = {
      element match {
        case doc: PsiDocCommentOwner if doc.isDeprecated => true
        case _ => false
      }
    }

    def getLookupElementInternal(isAssignment: Boolean, name: String): ScalaLookupItem = {
      val lookupItem: ScalaLookupItem = new ScalaLookupItem(element, name, containingClass)
      lookupItem.isClassName = isClassName
      lookupItem.isNamedParameter = resolveResult.isNamedParameter
      lookupItem.isDeprecated = isDeprecated
      lookupItem.isOverloadedForClassName = isOverloadedForClassName
      lookupItem.isRenamed = isRenamed
      lookupItem.isUnderlined = resolveResult.implicitFunction.isDefined
      lookupItem.isAssignment = isAssignment
      lookupItem.isInImport = isInImport
      lookupItem.bold = isCurrentClassMember
      lookupItem.shouldImport = shouldImport
      lookupItem.isInStableCodeReference = isInStableCodeReference
      lookupItem.substitutor = substitutor
      lookupItem.prefixCompletion = resolveResult.prefixCompletion
      lookupItem.isInSimpleString = isInSimpleString
      lookupItem
    }

    val name: String = isRenamed.getOrElse(element.name)
    val Setter = """(.*)_=""".r
    name match {
      case Setter(prefix) if !element.isInstanceOf[FakePsiMethod] => //if element is fake psi method, then this setter is already generated from var
        Seq(getLookupElementInternal(isAssignment = true, prefix), getLookupElementInternal(isAssignment = false, name))
      case _ => Seq(getLookupElementInternal(isAssignment = false, name))
    }
  }
}
