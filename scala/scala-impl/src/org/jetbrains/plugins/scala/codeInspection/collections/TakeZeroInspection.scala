package org.jetbrains.plugins.scala.codeInspection.collections

import com.intellij.codeInspection.{ProblemHighlightType, ProblemsHolder}
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.codeInspection.{PsiElementVisitorSimple, ScalaInspectionBundle}

import scala.collection.immutable.ArraySeq

class TakeZeroInspection extends OperationOnCollectionInspection {
  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = PsiElementVisitorSimple(holder) {
    case expr@_ `.take` arg if arg.textMatches("0") =>
      holder.registerProblem(expr, ScalaInspectionBundle.message("take.0.is.always.empty"), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    case _ =>
  }

  override def possibleSimplificationTypes: Seq[SimplificationType] = ArraySeq.empty
}
