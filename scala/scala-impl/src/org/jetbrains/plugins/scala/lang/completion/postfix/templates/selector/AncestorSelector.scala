package org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector

import java.util

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Conditions._
import com.intellij.psi.PsiElement
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.SelectorType._
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaExpressionSurrounder

/**
  * @author Roman.Shein
  * @since 08.09.2015.
  */
class AncestorSelector(val condition: Condition[PsiElement], val selectorType: SelectorType = First) extends PostfixTemplateExpressionSelectorBase(condition) {

  override protected def getFilters(offset: Int): Condition[PsiElement] = {
    and(super.getFilters(offset), getPsiErrorFilter)
  }

  override def getNonFilteredExpressions(context: PsiElement, document: Document, offset: Int): util.List[PsiElement] = {
    context.parentOfType(classOf[ScExpression], strict = false) match {
      case Some(element: ScExpression) =>
        val result = ContainerUtil.newLinkedList[PsiElement](element)
        var current: PsiElement = element.getParent
        while (current != null && current.getTextRange != null && current.getTextRange.getEndOffset <= offset && (selectorType match {
          case All => true
          case Topmost => current.isInstanceOf[ScExpression]
          case First => false
        })) {
          if (result.getLast.getText == current.getText) {
            result.removeLast()
            result.add(current)
          } else {
            result.add(current)
          }
          current = current.getParent
        }
        result
      case _ => ContainerUtil.emptyList()
    }
  }
}

object AncestorSelector {
  def apply(surrounder: ScalaExpressionSurrounder, selectorType: SelectorType = First): AncestorSelector =
    new AncestorSelector(new Condition[PsiElement]{
      override def value(t: PsiElement): Boolean = surrounder.isApplicable(t)
    }, selectorType)
}