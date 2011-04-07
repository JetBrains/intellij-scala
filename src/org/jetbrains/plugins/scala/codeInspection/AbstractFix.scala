package org.jetbrains.plugins.scala.codeInspection

import com.intellij.openapi.project.Project
import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

abstract class AbstractFix(name: String, e: PsiElement) extends LocalQuickFix {
  def getName = name

  def getFamilyName = getName

  final def applyFix(project: Project, descriptor: ProblemDescriptor) {
    val file = e.getContainingFile
    if(file == null) return
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return
    doApplyFix(project, descriptor)
  }

  def doApplyFix(project: Project, descriptor: ProblemDescriptor)
}