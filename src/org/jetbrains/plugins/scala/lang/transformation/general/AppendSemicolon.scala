package org.jetbrains.plugins.scala.lang.transformation
package general

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.extensions.{&&, NextSibling, Whitespace}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockStatement
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createSemicolon

/**
  * @author Pavel Fatin
  */
class AppendSemicolon extends AbstractTransformer {
  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit] = {
    case (statement: ScBlockStatement) && NextSibling(Whitespace(s)) if s.contains("\n") =>
      implicit val manager = PsiManager.getInstance(project)
      statement.getParent.addAfter(createSemicolon, statement)
  }
}
