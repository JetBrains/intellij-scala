package org.jetbrains.plugins.scala.codeInspection.annotations

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractInspection
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScAnnotation

/**
 * User: Dmitry.Naydanov
 * Date: 30.09.15.
 */
class MultipleArgLists extends AbstractInspection("ScalaAnnotMultipleArgLists", "MultipleArgListsInAnnotation") {

  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Any] = {
    case annotation: ScAnnotation if annotation.constructor.arguments.length > 1 => 
      holder.registerProblem(annotation, 
        "Implementation limitation: multiple argument lists on annotations are currently not supported")
  }
}
