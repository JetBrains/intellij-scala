package org.jetbrains.plugins.scala.lang.dfa.analysis

import com.intellij.codeInspection.{InspectionManager, LocalQuickFix, ProblemsHolder}
import com.intellij.psi.{PsiElement, PsiFile}

import scala.collection.mutable.ArrayBuffer


class MockProblemsHolder(file: PsiFile, inspectionManager: InspectionManager)
  extends ProblemsHolder(inspectionManager, file, false) {

  private val problems: ArrayBuffer[MockProblemDescriptor] = ArrayBuffer()

  def collectProblems: Seq[MockProblemDescriptor] = problems.toList

  override def registerProblem(psiElement: PsiElement, descriptionTemplate: String,
                               fixes: LocalQuickFix*): Unit = {
    problems += MockProblemDescriptor(psiElement, descriptionTemplate)
  }
}
