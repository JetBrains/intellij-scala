package org.jetbrains.plugins.scala
package codeInspection.arraySize

import lang.psi.impl.ScalaPsiElementFactory
import lang.psi.types.{ScDesignatorType, ScParameterizedType}
import com.intellij.codeInspection.{ProblemHighlightType, ProblemDescriptor, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import codeInspection.{AbstractFix, AbstractInspection}
import extensions._

/**
 * Pavel Fatin
 */

class ArraySizeCallInspection extends AbstractInspection {
  def actionFor(holder: ProblemsHolder) = {
    case reference @ ReferenceTarget(Member("size", "scala.collection.SeqLike")) &&
            Qualifier(ExpressionType(ScParameterizedType(ScDesignatorType(ClassQualifiedName("scala.Array")), _))) =>
      reference.depthFirst.toList.reverse.find(_.getText == "size").foreach { id =>
        holder.registerProblem(id, "Call to Array.size",
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING, new QuickFix(id))
      }
  }

  private class QuickFix(id: PsiElement) extends AbstractFix("Replace with \"length\"", id) {
    def doApplyFix(project: Project, descriptor: ProblemDescriptor) {
      val ref = ScalaPsiElementFactory.createIdentifier("length", id.getManager)
      id.replace(ref.getPsi)
    }
  }
}
