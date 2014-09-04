package org.jetbrains.plugins.scala
package codeInspection.arraySize

import com.intellij.codeInspection.{ProblemDescriptor, ProblemHighlightType, ProblemsHolder}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.{AbstractFix, AbstractInspection}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScDesignatorType, ScParameterizedType}

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
