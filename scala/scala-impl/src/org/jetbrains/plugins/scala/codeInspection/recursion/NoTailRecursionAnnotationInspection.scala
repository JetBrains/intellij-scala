package org.jetbrains.plugins.scala
package codeInspection.recursion

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{RecursionType, ScAnnotationsHolder, ScFunctionDefinition}

/**
 * Pavel Fatin
 */

class NoTailRecursionAnnotationInspection extends AbstractInspection("No tail recursion annotation") {
  override def actionFor(implicit holder: ProblemsHolder): PartialFunction[PsiElement, Unit] = {
    case f: ScFunctionDefinition if f.canBeTailRecursive && !f.hasTailRecursionAnnotation &&
            f.recursionType == RecursionType.TailRecursion =>
      holder.registerProblem(f.nameId, getDisplayName, new AddAnnotationQuickFix(f))
  }

  class AddAnnotationQuickFix(holder: ScAnnotationsHolder)
          extends AbstractFixOnPsiElement("Add @tailrec annotation", holder) {

    override protected def doApplyFix(element: ScAnnotationsHolder)
                                     (implicit project: Project): Unit = {
      element.addAnnotation("scala.annotation.tailrec")
    }
  }
}
