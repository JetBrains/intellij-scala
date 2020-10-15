package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection

import com.intellij.CommonBundle
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootManager, SourceFolder}
import com.intellij.openapi.ui.Messages
import com.intellij.psi._
import com.intellij.refactoring.PackageWrapper
import com.intellij.refactoring.move.moveClassesOrPackages.{MoveClassesOrPackagesProcessor, MoveClassesOrPackagesUtil, SingleSourceRootMoveDestination}
import com.intellij.refactoring.util.RefactoringMessageUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.packageNameInspection.ScalaMoveToPackageQuickFix._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.refactoring.move.saveMoveDestination

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */
final class ScalaMoveToPackageQuickFix(myFile: ScalaFile, packageName: String)
      extends AbstractFixOnPsiElement(ScalaMoveToPackageQuickFix.hint(packageName), myFile) {

  override protected def doApplyFix(file: ScalaFile)
                                   (implicit project: Project): Unit = {
    var error: String = null
    var directory: PsiDirectory = null
    try {
      // TODO Support multiple source roots (more complicated because chooseDestinationPackage throws an exception instead of returning a directory in such a case.)
      // Specifically make sure that the package is compatible with an existing package prefix.
      for (module <- file.module;
           sourceFolder <- sourceFolderIn(module);
           packagePrefix = sourceFolder.getPackagePrefix if !packagePrefix.isEmpty;
           if !(packageName + ".").startsWith(packagePrefix + ".")) {
        Messages.showMessageDialog(project,
          ScalaInspectionBundle.message("move.file.to.package.package.prefix.error", s"'$packageName'", s"'${sourceFolder.getFile.getName}'", s"'$packagePrefix'"),
          CommonBundle.getErrorTitle, Messages.getErrorIcon)
        return
      }

      directory = MoveClassesOrPackagesUtil.chooseDestinationPackage(project, packageName, myFile.getContainingDirectory);
      if (directory == null) {
        return
      }
      error = RefactoringMessageUtil.checkCanCreateFile(directory, file.name)
    } catch {
      case e: IncorrectOperationException =>
        error = e.getLocalizedMessage
    }
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

private object ScalaMoveToPackageQuickFix {
  @Nls
  def hint(packageName: String): String = {
    if (packageName.isEmpty) ScalaInspectionBundle.message("move.file.to.default.package")
    else ScalaInspectionBundle.message("move.file.to.package.with.packagename", s"'$packageName'")
  }

  def sourceFolderIn(module: Module): Option[SourceFolder] =
    ModuleRootManager.getInstance(module).getContentEntries.flatMap(_.getSourceFolders) match {
      case Array(sourceFolder) => Some(sourceFolder)
      case _ => None
    }
}