package org.jetbrains.plugins.scala.codeInspection.packageNameInspection


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

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */

class ScalaMoveToPackageQuickFix(file: ScalaFile, pack: PsiPackage) extends LocalQuickFix {
  def applyFix(project: Project, descriptor: ProblemDescriptor): Unit = {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return
    val packageName = pack.getQualifiedName
    val directory = PackageUtil.findOrCreateDirectoryForPackage(project, packageName, null, true)

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

  def getName: String = "Move File " + file.getName + " To Package " + pack.getQualifiedName

  def getFamilyName: String = "Mope File To Package"
}