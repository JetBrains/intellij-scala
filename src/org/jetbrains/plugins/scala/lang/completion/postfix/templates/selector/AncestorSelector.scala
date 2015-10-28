package org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector

import java.util

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions._
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlock, ScBlockExpr, ScExpression}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaRefactoringUtil

/**
 * @author Roman.Shein
 * @since 08.09.2015.
 */
class AncestorSelector(val condition: Condition[PsiElement], val selectorType: SelectorType = First) extends PostfixTemplateExpressionSelectorBase(condition) {

  override protected def getFilters (offset: Int): Condition[PsiElement] = {
    and(super.getFilters(offset), getPsiErrorFilter)
  }

  override def getNonFilteredExpressions(context: PsiElement, document: Document, offset: Int): util.List[PsiElement] = {
    ScalaPsiUtil.getParentOfType(context, classOf[ScExpression]) match {
      case element: ScExpression =>
        val result = ContainerUtil.newLinkedList[PsiElement](element)
        var current: PsiElement = element.getParent
        while (current != null && current.getTextRange.getEndOffset <= offset && (selectorType match {
          case All => true
          case Topmost => current.isInstanceOf[ScExpression]
          case First => false
        })) {
          result.add(current)
          current = current.getParent
        }
        result
      case _ => ContainerUtil.emptyList()
    }
  }
}