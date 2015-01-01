package org.jetbrains.plugins.scala
package codeInspection.recursion

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, AbstractInspection}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{RecursionType, ScAnnotationsHolder, ScFunctionDefinition}

/**
 * Pavel Fatin
 */

class NoTailRecursionAnnotationInspection extends AbstractInspection("No tail recursion annotation") {
  def actionFor(holder: ProblemsHolder) = {
    case f: ScFunctionDefinition if f.canBeTailRecursive && !f.hasTailRecursionAnnotation &&
            f.recursionType == RecursionType.TailRecursion =>
      holder.registerProblem(f.nameId, getDisplayName, new AddAnnotationQuickFix(f))
  }

  class AddAnnotationQuickFix(holder: ScAnnotationsHolder)
          extends AbstractFixOnPsiElement("Add @tailrec annotation", holder) {
    def doApplyFix(project: Project) {
      getElement.addAnnotation("scala.annotation.tailrec")
    }
  }
}
