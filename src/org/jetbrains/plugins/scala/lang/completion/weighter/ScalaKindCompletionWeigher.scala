package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

/**
  * Created by kate
  * on 1/18/16
  */
class ScalaKindCompletionWeigher extends CompletionWeigher {
  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    if (!ScalaProjectSettings.getInstance(location.getProject).isScalaPriority) return null

    val position = location.getCompletionParameters.getPosition
    val originalPosition = location.getCompletionParameters.getOriginalPosition

    val isTypeDefiniton = Option(PsiTreeUtil.getParentOfType(position, classOf[ScTypeElement])).isDefined

    import KindWeights._

    def inCurrentClassDef(inMember: PsiMember): Boolean = {
      val cclass = inMember.getContainingClass
      (cclass != null) && ((position != null && PsiTreeUtil.isContextAncestor(cclass, position, false)) ||
        (originalPosition != null && PsiTreeUtil.isContextAncestor(cclass, originalPosition, false)))
    }

    def handleMember(inMember: PsiMember, position: PsiElement): KindWeights.Value = {
      val cclass = inMember.getContainingClass
      val noClass = cclass == null

      val inCurrentClass = inCurrentClassDef(inMember)
      if (inCurrentClass) {
        inMember match {
          case f: ScValue if !f.isLocal => currentClassField
          case f: ScVariable if !f.isLocal => currentClassField
          case f: PsiField => currentClassField
          case m: PsiMethod => currentClassMethod
          case _ => currentClassMember
        }
      } else {
        inMember match {
          case f: ScFunction if noClass => localFunc
          case f: ScValue if !f.isLocal => field
          case f: ScVariable if !f.isLocal => currentClassField
          case f: PsiField => field
          case m: PsiMethod => method
          case _ => member
        }
      }
    }

    def weight = ScalaLookupItem.original(element) match {
      case s: ScalaLookupItem if s.isLocalVariable => KindWeights.local
      case s: ScalaLookupItem =>
        s.element match {
          case p: ScParameter => KindWeights.local
          case patt: ScBindingPattern =>
            val context = ScalaPsiUtil.nameContext(patt)
            context match {
              case m: PsiMember => handleMember(m, position)
              case _ => null
            }
          case m: PsiMember => handleMember(m, position)
          case _ => null
        }
      case _ => null
    }

    def typedWeight =
      ScalaLookupItem.original(element) match {
        case s: ScalaLookupItem =>
          s.element match {
            case ta: ScTypeAlias if ta.isLocal => localType
            case ta: ScTypeAlias if inCurrentClassDef(ta) => typeDefinition
            case te: ScTypeDefinition if !te.isObject && (te.isLocal || inFunction(te)) => localType
            case te: ScTypeDefinition if inCurrentClassDef(te) && !te.isObject => typeDefinition
            case _ => weight
          }
        case _ => null
      }

    if (isTypeDefiniton) typedWeight
    else weight
  }

  def inFunction(psiElement: PsiElement): Boolean =
    PsiTreeUtil.getParentOfType(psiElement, classOf[ScBlockExpr]) != null

  object KindWeights extends Enumeration {
    val member, method, field,
    currentClassMember, currentClassMethod, currentClassField, localFunc, local,
    typeDefinition, currentClassType, localType = Value
  }

}