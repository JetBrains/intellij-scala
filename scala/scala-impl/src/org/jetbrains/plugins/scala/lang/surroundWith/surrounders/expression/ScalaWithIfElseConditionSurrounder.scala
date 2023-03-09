package org.jetbrains.plugins.scala.lang.surroundWith.surrounders.expression

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeExt, api}

class ScalaWithIfElseConditionSurrounder extends ScalaWithIfConditionSurrounderBase {
  override def getTemplateAsString(elements: Array[PsiElement]): String =
    "if (" + super.getTemplateAsString(elements) + ") {} else {}"

  //noinspection ScalaExtractStringToBundle,DialogTitleCapitalization
  override def getTemplateDescription: String = "if (expr) {...} else {...}"

  override def isApplicable(elements: Array[PsiElement]): Boolean = {
    if (elements.length != 1) return false
    elements(0) match {
      case x: ScExpression if x.getTypeIgnoreBaseType.getOrAny.
        conforms(api.Boolean(x.getProject)) => true
      case _ => false
    }
  }
}
