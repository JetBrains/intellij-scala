package org.jetbrains.plugins.scala.lang.completion
package postfix
package templates
package selector

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelectorBase
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.{Condition, Conditions}
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.api.ExtractClass
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}
import org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression.ScalaExpressionSurrounder

import java.{util => ju}

sealed abstract class AncestorSelector(condition: Condition[PsiElement])
  extends PostfixTemplateExpressionSelectorBase(condition) {

  override final protected def getFilters(offset: Int): Condition[PsiElement] =
    Conditions.and(super.getFilters(offset), getPsiErrorFilter)

  override final def getNonFilteredExpressions(
    context: PsiElement,
    document: Document,
    offset: Int
  ): ju.List[PsiElement] = {
    val expression = PsiTreeUtil.getParentOfType(context, classOf[ScExpression], false)
    if (expression != null) {
      val withParentsMatchingRange =
        expression
          .withParentsInFile
          .takeWhile(isAcceptable)
          .takeWhile(_.getTextRange.getEndOffset <= offset)

      val result = new ju.ArrayList[PsiElement]
      withParentsMatchingRange.foreach(result.add)
      result
    }
    else ju.Collections.emptyList()
  }

  protected def isAcceptable(current: PsiElement): Boolean = current != null
}

object AncestorSelector {

  final case class SelectAllAncestors(private val condition: Condition[PsiElement] = AnyExpression) extends AncestorSelector(condition)

  object SelectAllAncestors {
    def apply(surrounder: ScalaExpressionSurrounder): SelectAllAncestors =
      new SelectAllAncestors(surrounder.isApplicable(_: PsiElement))
  }

  final case class SelectTopmostAncestors(private val condition: Condition[PsiElement] = BooleanExpression) extends AncestorSelector(condition) {
    override protected def isAcceptable(current: PsiElement): Boolean = current.is[ScExpression]
  }

  object SelectTopmostAncestors {
    def apply(surrounder: ScalaExpressionSurrounder): SelectTopmostAncestors =
      new SelectTopmostAncestors(surrounder.isApplicable(_: PsiElement))
  }

  val AnyExpression: Condition[PsiElement] = (_: PsiElement).is[ScExpression]

  val AnyRefExpression: Condition[PsiElement] = expressionTypeCondition {
    case (expression, scType) => scType.conforms(api.AnyRef(expression))
  }

  val BooleanExpression: Condition[PsiElement] = expressionTypeCondition {
    case (expression, scType) => scType.conforms(api.Boolean(expression))
  }

  def isSameOrInheritor(fqns: String*): Condition[PsiElement] = expressionTypeCondition {
    case (expression, ExtractClass(clazz)) =>
      val elementScope = expression.elementScope
      fqns.flatMap(elementScope.getCachedClass)
        .exists(clazz.sameOrInheritor)
    case _ => false
  }

  private[this] def expressionTypeCondition(isValid: (ScExpression, ScType) => Boolean): Condition[PsiElement] = {
    case expression: ScExpression => expression.getTypeIgnoreBaseType.exists {
      isValid(expression, _)
    }
    case _ => false
  }
}