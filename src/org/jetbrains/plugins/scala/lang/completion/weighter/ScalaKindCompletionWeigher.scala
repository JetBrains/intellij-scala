package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.{ScalaAfterNewCompletionUtil, ScalaCompletionUtil}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition

/**
  * Created by kate
  * lift fields before methods. threat class params as field.
  * on 1/18/16
  */
class ScalaKindCompletionWeigher extends CompletionWeigher {
  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    val position = ScalaCompletionUtil.positionFromParameters(location.getCompletionParameters)

    import KindWeights._

    def handleMember(inMember: PsiMember, position: PsiElement): KindWeights.Value = {
      val cclass = inMember.getContainingClass
      val noClass = cclass == null

      if (noClass) return normal
      inMember match {
        case _: ScValue => field
        case _: ScVariable => field
        case _: PsiField => field
        case _: PsiMethod => method
        case _ => member
      }
    }

    def weight =
      ScalaLookupItem.original(element) match {
        case s: ScalaLookupItem =>
          s.element match {
            case _: ScClassParameter => KindWeights.field
            case patt: ScTypedDefinition =>
              patt.nameContext match {
                case m: PsiMember => handleMember(m, position)
                case _ => null
              }
            case m: PsiMember => handleMember(m, position)
            case _ => null
          }
        case _ => null
      }

    val isTypedPostion = ScalaCompletionUtil.isTypeDefiniton(position)
    val isAfterNewPosition = ScalaAfterNewCompletionUtil.isAfterNew(position, location.getProcessingContext)

    ScalaLookupItem.original(element) match {
      case _ if isTypedPostion || isAfterNewPosition => null
      case _ => weight
    }
  }

  def inFunction(psiElement: PsiElement): Boolean =
    PsiTreeUtil.getParentOfType(psiElement, classOf[ScBlockExpr]) != null

  object KindWeights extends Enumeration {
    val normal, member, method, field = Value
  }

}
