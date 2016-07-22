package org.jetbrains.plugins.scala.lang.transformation
package references

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.parseElement

/**
  * @author Pavel Fatin
  */
class PartiallyQualifySimpleReference extends AbstractTransformer {
  def transformation(implicit project: Project): PartialFunction[PsiElement, Unit] = {
    case e: ScReferenceExpression
      if !e.getParent.isInstanceOf[ScReferenceExpression] && !e.text.contains(".") =>

      e.bind().foreach { result =>
        val paths = targetFor(result).split("\\.").toVector

        if (paths.length > 1) {
          val reference = parseElement(paths.takeRight(2).mkString("."), e.psiManager).asInstanceOf[ScReferenceExpression]
          reference.setContext(e.getParent, e.getParent.getFirstChild)
          if (reference.bind().exists(_.element == result.element)) {
            e.replace(reference)
          }
        }
      }
  }
}
