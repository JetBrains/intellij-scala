package org.jetbrains.plugins.scala.codeInspection

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInspection.{LocalQuickFix, ProblemDescriptor}
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

/**
 * Pavel Fatin
 */

abstract class AbstractFix(name: String, e: PsiElement) extends LocalQuickFix {
  def getName = name

  def getFamilyName = getName

  final def applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!e.isValid) return
    val file = e.getContainingFile
    if(file == null) return
    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return
    doApplyFix(project)
  }

  def doApplyFix(project: Project)
}