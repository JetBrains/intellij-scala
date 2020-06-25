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
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.move.saveMoveDestination

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */
final class ScalaMoveToPackageQuickFix(myFile: ScalaFile, packQualName: String)
      extends AbstractFixOnPsiElement(ScalaMoveToPackageQuickFix.hint(myFile.name, packQualName), myFile) {

  override protected def doApplyFix(file: ScalaFile)
                                   (implicit project: Project): Unit = {
    val packageName = packQualName
    val fileIndex: ProjectFileIndex = ProjectRootManager.getInstance(project).getFileIndex
    val currentModule: Module = fileIndex.getModuleForFile(file.getVirtualFile)
    val directory = PackageUtil.findOrCreateDirectoryForPackage(currentModule, packageName, null, false)

    if (directory == null) {
      return
    }
    val error = RefactoringMessageUtil.checkCanCreateFile(directory, file.name)
    if (error != null) {
      Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle, Messages.getErrorIcon)
      return
    }
    saveMoveDestination(file, directory)
    val processor = new MoveClassesOrPackagesProcessor(
      project,
      Array[PsiElement](file.typeDefinitions.head),
      new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(directory)), directory), false,
      false,
      null)

    processor.run()
  }

  override def startInWriteAction(): Boolean = false

  override def getFamilyName: String = ScalaInspectionBundle.message("fimaly.name.move.file.to.package")
}

object ScalaMoveToPackageQuickFix {
  @Nls
  def hint(fileName: String, packageName: String): String = {
    if (packageName.isEmpty) ScalaInspectionBundle.message("move.file.to.default.package", fileName)
    else ScalaInspectionBundle.message("move.file.to.package.with.packagename", fileName, packageName)
  }
}