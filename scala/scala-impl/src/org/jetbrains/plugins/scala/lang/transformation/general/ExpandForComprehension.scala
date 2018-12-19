package org.jetbrains.plugins.scala.lang.transformation
package general

import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.scala.extensions.{&&, ElementText, FirstChild, Parent, PrevSibling, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScInterpolatedStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, _}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaCode._
import org.jetbrains.plugins.scala.project.ProjectContext

/**
  * @author Pavel Fatin
  */
class ExpandForComprehension extends AbstractTransformer {
  def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e: ScForStatement =>
      //desugarRecursively(e)
      desugared(e).foreach(e.replace)
  }

  private def desugared(e: ScForStatement)(implicit project: ProjectContext): Option[PsiElement] =
    e.getDesugaredExpr(forDisplay = true)
}
