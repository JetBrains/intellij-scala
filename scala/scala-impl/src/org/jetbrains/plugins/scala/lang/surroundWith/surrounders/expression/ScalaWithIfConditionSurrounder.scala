package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeExt, api}

object ScalaWithIfConditionSurrounder extends ScalaWithIfConditionSurrounderBase {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "if (" + super.getTemplateAsString(elements) + ") {}"

  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription: String = "if (expr) {...}"

  override def isApplicable(element: PsiElement): Boolean = element match {
    case expr: ScExpression => expr.getTypeIgnoreBaseType.getOrAny.conforms(api.Boolean(expr.projectContext))
    case _ => false
  }

  override def isApplicable(elements: Array[PsiElement]): Boolean =
    elements.length == 1 && isApplicable(elements.head)
}
