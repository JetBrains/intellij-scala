package org.jetbrains.plugins.scala.lang.completion.weighter

import com.intellij.codeInsight.lookup.{LookupElement, LookupElementWeigher, WeighingContext}
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
class ScalaByTypeWeigher(position: PsiElement) extends LookupElementWeigher("scalaTypeCompletionWeigher") {

  override def weigh(element: LookupElement, context: WeighingContext): Comparable[_] = {
    import KindWeights._

    val isAfterNew = ScalaAfterNewCompletionUtil.afterNewPattern.accepts(position)

    def inFunction(psiElement: PsiElement): Boolean =
      PsiTreeUtil.getParentOfType(psiElement, classOf[ScBlockExpr]) != null

    def isTypeDefiniton = ScalaCompletionUtil.isTypeDefiniton(position)

    def typedWeight =
      ScalaLookupItem.original(element) match {
        case s: ScalaLookupItem =>
          s.element match {
            case ta: ScTypeAlias if ta.isLocal => localType
            case _: ScTypeAlias => typeDefinition
            case te: ScTypeDefinition if !te.isObject && (te.isLocal || inFunction(te)) => localType
            case _: ScTypeDefinition => typeDefinition
            case _: PsiClass => typeDefinition
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
    val top, localType, typeDefinition, normal = Value
  }

}
