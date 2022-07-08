package org.jetbrains.plugins.scala.lang
package completion
package postfix
package templates

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.completion.postfix.templates.selector.AncestorSelector.{AnyRefExpression, SelectTopmostAncestors}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScInfixExpr

sealed abstract class ScalaNullPostfixTemplate(name: String, character: Char) extends ScalaStringBasedPostfixTemplate(
  name,
  s"if (expr $character= null) {}",
  SelectTopmostAncestors(AnyRefExpression)
) {
  override def getTemplateString(element: PsiElement): String = {
    val (prefix, suffix) = element match {
      case _: ScInfixExpr => ("(", ")")
      case _ => ("", "")
    }
    "if (" + prefix + "$expr$" + suffix + " " + character + "= null) {$END$}"
  }
}

final class ScalaNotNullPostfixTemplate(alias: String = "notnull") extends ScalaNullPostfixTemplate(alias, '!')

final class ScalaIsNullPostfixTemplate extends ScalaNullPostfixTemplate("null", '=')
