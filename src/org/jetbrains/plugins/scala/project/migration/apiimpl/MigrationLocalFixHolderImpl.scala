package org.jetbrains.plugins.scala.project.migration.apiimpl

import com.intellij.codeInspection.{CommonProblemDescriptor, ProblemsHolder, QuickFix}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.codeInspection.AbstractFixOnPsiElement
import org.jetbrains.plugins.scala.project.migration.api.MigrationLocalFixHolder

/**
  * User: Dmitry.Naydanov
  * Date: 27.09.16.
  */
class MigrationLocalFixHolderImpl(private val holder: ProblemsHolder) extends MigrationLocalFixHolder {
  override def registerFix(psiElement: PsiElement, action: (Project) => Unit): Unit = {
    holder.registerProblem(psiElement, "", new AbstractFixOnPsiElement[PsiElement]("Azaza", psiElement) {
      override def doApplyFix(project: Project): Unit = action(project)
    })
  }
  
  def getHolder: ProblemsHolder = holder
  
  def getFixes: Array[QuickFix[_ <: CommonProblemDescriptor]] = getHolder.getResultsArray.flatMap(d => d.getFixes)
}
