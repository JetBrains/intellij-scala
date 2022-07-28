package org.jetbrains.plugins.scala.lang.transformation
package references

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createReferenceExpressionFromText
import org.jetbrains.plugins.scala.project.ProjectContext

class PartiallyQualifySimpleReference extends AbstractTransformer {
  override protected def transformation(implicit project: ProjectContext): PartialFunction[PsiElement, Unit] = {
    case e: ScReferenceExpression
      if !e.getParent.isInstanceOf[ScReferenceExpression] && !e.getText.contains(".") =>

      e.bind().foreach { result =>
        val paths = targetFor(result).split("\\.").toVector

        if (paths.length > 1) {
          val reference = createReferenceExpressionFromText(paths.takeRight(2).mkString("."))(e.getManager)
          val context = e.getParent
          reference.context = context
          reference.child = context.getFirstChild
          if (reference.bind().exists(_.element == result.element)) {
            e.replace(reference)
          }
        }
      }
  }
}
