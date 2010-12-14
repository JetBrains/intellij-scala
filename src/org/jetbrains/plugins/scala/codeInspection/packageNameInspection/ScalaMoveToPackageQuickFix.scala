package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection


import com.intellij.codeInsight.CodeInsightUtilBase
import com.intellij.ide.util.PackageUtil
import com.intellij.openapi.ui.Messages
import com.intellij.refactoring.move.moveClassesOrPackages.{MoveClassesOrPackagesProcessor, SingleSourceRootMoveDestination}
import com.intellij.psi._
import com.intellij.refactoring.PackageWrapper



import com.intellij.CommonBundle
import com.intellij.refactoring.util.RefactoringMessageUtil

import com.intellij.codeInspection.{ProblemDescriptor, LocalQuickFix}
import com.intellij.openapi.project.Project
import java.lang.String
import lang.psi.api.ScalaFile
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.{ProjectRootManager, ProjectFileIndex}

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */

class ScalaMoveToPackageQuickFix(file: ScalaFile, packQualName: String) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return
    val packageName = packQualName
    val fileIndex: ProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
    val currentModule: Module = fileIndex.getModuleForFile(file.getVirtualFile)
    val directory = PackageUtil.findOrCreateDirectoryForPackage(currentModule, packageName, null, true)

    if (directory == null) {
      return
    }
    val error = RefactoringMessageUtil.checkCanCreateFile(directory, file.getName)
    if (error != null) {
      Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle, Messages.getErrorIcon)
      return
    }
    new MoveClassesOrPackagesProcessor(
      project,
      Array[PsiElement](file.getClasses.apply(0)),
      new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(directory)), directory), false,
      false,
      null).run;
  }

  def getName: String = "Move File " + file.getName + " To Package " + packQualName

  def getFamilyName: String = "Move File To Package"
}