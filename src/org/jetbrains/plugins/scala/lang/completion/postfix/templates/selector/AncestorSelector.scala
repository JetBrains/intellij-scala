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
        //TODO reimplement it?
        if (selectorType == All) {
          ContainerUtil.list(ScalaRefactoringUtil.getExpressions(element):_*)
        } else {
          ContainerUtil.list(
            if (selectorType == Topmost) {
              var current = element
              while (current.getParent != null && (current.getParent match {
                case _: ScBlockExpr | _: ScBlock => false
                case expr: ScExpression => true
                case _ => false
              })) {
                current = current.getParent.asInstanceOf[ScExpression]
              }
              current
            } else {
              element
            }
          )
        }
      case _ => ContainerUtil.emptyList()
    }
  }
}