package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection


import com.intellij.CommonBundle
import com.intellij.ide.util.PackageUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ProjectFileIndex, ProjectRootManager}
import com.intellij.openapi.ui.Messages
import com.intellij.psi._
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.{MoveClassesOrPackagesProcessor, SingleSourceRootMoveDestination}
import com.intellij.refactoring.util.RefactoringMessageUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.move.ScalaMoveUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */

class ScalaMoveToPackageQuickFix(myFile: ScalaFile, packQualName: String)
      extends AbstractFixOnPsiElement(ScalaMoveToPackageQuickFix.hint(myFile.name, packQualName), myFile) {

  override protected def doApplyFix(file: ScalaFile)
                                   (implicit project: Project): Unit = {
    val packageName = packQualName
    val fileIndex: ProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
    val currentModule: Module = fileIndex.getModuleForFile(file.getVirtualFile)
    val directory = PackageUtil.findOrCreateDirectoryForPackage(currentModule, packageName, null, true)

    if (directory == null) {
      return
    }
    val error = RefactoringMessageUtil.checkCanCreateFile(directory, file.name)
    if (error != null) {
      Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle, Messages.getErrorIcon)
      return
    }
    ScalaMoveUtil.saveMoveDestination(file, directory)
    val processor = new MoveClassesOrPackagesProcessor(
      project,
      Array[PsiElement](file.typeDefinitions.head),
      new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(directory)), directory), false,
      false,
      null)

    invokeLater {
      //shouldn't be started inside write action
      processor.run()
    }
  }

  override def getFamilyName: String = "Move File To Package"
}

object ScalaMoveToPackageQuickFix {
  def hint(fileName: String, packageName: String): String = {
    val packageText = if (packageName.nonEmpty) s"Package $packageName" else "Default Package"
    s"Move File $fileName To $packageText"
  }
}