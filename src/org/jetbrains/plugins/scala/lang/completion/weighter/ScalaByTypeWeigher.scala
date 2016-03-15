package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.completion.{CompletionLocation, CompletionWeigher}
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.completion.{ScalaAfterNewCompletionUtil, ScalaCompletionUtil}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/**
  * Created by kate
  * on 1/25/16
  */
class ScalaByTypeWeigher extends CompletionWeigher {

  override def weigh(element: LookupElement, location: CompletionLocation): Comparable[_] = {
    import KindWeights._

    val position = ScalaCompletionUtil.positionFromParameters(location.getCompletionParameters)
    val context = location.getProcessingContext

    val isAfterNew = ScalaAfterNewCompletionUtil.isAfterNew(position, context)

    def inFunction(psiElement: PsiElement): Boolean =
      PsiTreeUtil.getParentOfType(psiElement, classOf[ScBlockExpr]) != null

    def isTypeDefiniton = ScalaCompletionUtil.isTypeDefiniton(position)

    def typedWeight =
      ScalaLookupItem.original(element) match {
        case s: ScalaLookupItem =>
          s.element match {
            case ta: ScTypeAlias if ta.isLocal => localType
            case ta: ScTypeAlias => typeDefinition
            case te: ScTypeDefinition if !te.isObject && (te.isLocal || inFunction(te)) => localType
            case te: ScTypeDefinition => typeDefinition
            case te: PsiClass => typeDefinition
            case _ => normal
          }
        case _ => null
      }

    ScalaLookupItem.original(element) match {
      case _ if isTypeDefiniton || isAfterNew => typedWeight
      case _ => null
    }
  }

  object KindWeights extends Enumeration {
    val normal, typeDefinition, localType, top = Value
  }
}
