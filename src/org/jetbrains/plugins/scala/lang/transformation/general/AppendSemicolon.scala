package org.jetbrains.plugins.scala.lang.transformation
package general

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{&&, NextSibling, Whitespace}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._

/**
  * @author Pavel Fatin
  */
class AppendSemicolon extends AbstractTransformer {
  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit] = {
    case (e: ScBlockStatement) && NextSibling(Whitespace(s)) if s.contains("\n") =>
      e.append(parseElement(";"))
  }
}
