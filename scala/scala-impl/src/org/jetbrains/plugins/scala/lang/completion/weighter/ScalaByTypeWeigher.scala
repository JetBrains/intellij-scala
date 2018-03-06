package org.jetbrains.plugins.scala.lang.completion
package weighter

import com.intellij.codeInsight.lookup._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
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

    def inFunction(te: ScTypeDefinition): Boolean =
      PsiTreeUtil.getParentOfType(te, classOf[ScBlockExpr]) != null

    if (ScalaCompletionUtil.isTypeDefiniton(position) ||
      ScalaAfterNewCompletionUtil.afterNewPattern.accepts(position)) {
      ScalaLookupItem.original(element) match {
        case ScalaLookupItem(ta: ScTypeAlias) if ta.isLocal => localType
        case ScalaLookupItem(te: ScTypeDefinition) if !te.isObject && (te.isLocal || inFunction(te)) => localType
        case ScalaLookupItem(_: ScTypeAlias | _: PsiClass) => typeDefinition
        case ScalaLookupItem(_) => normal
        case _ => null
      }
    } else null
  }

  object KindWeights extends Enumeration {
    val top, localType, typeDefinition, normal = Value
  }

}
