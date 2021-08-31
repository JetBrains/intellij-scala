package org.jetbrains.plugins.scala
package codeInspection
package packageNameInspection

import com.intellij.CommonBundle
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.{ModuleRootManager, SourceFolder}
import com.intellij.openapi.ui.Messages
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil
import com.intellij.refactoring.util.RefactoringMessageUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.codeInspection.packageNameInspection.ScalaMoveToPackageQuickFix._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.07.2009
 */
final class ScalaMoveToPackageQuickFix(_file: ScalaFile, packageName: String)
      extends AbstractFixOnPsiElement(ScalaMoveToPackageQuickFix.hint(packageName), _file) {

  override protected def doApplyFix(file: ScalaFile)
                                   (implicit project: Project): Unit = {
    val directory = try {
      // TODO Support multiple source roots (more complicated because chooseDestinationPackage throws an exception instead of returning a directory in such a case.)
      // Specifically make sure that the package name is compatible with an existing package prefix.
      for (module <- file.module;
           sourceFolder <- sourceFolderIn(module);
           packagePrefix = sourceFolder.getPackagePrefix if packagePrefix.nonEmpty
           if !(packageName + ".").startsWith(packagePrefix + ".")) {
        Messages.showMessageDialog(project,
          ScalaInspectionBundle.message("move.file.to.package.package.prefix.error", packageName, sourceFolder.getFile.getName, packagePrefix),
          CommonBundle.getErrorTitle, Messages.getErrorIcon)
        return
      }

      val directory = MoveClassesOrPackagesUtil.chooseDestinationPackage(project, packageName, file.getContainingDirectory);
      if (directory == null) {
        return
      }

      RefactoringMessageUtil.checkCanCreateFile(directory, file.name) match {
        case null => directory
        case error => throw new IncorrectOperationException(error)
      }
    } catch {
      case e: IncorrectOperationException =>
        Messages.showMessageDialog(project, e.getLocalizedMessage, CommonBundle.getErrorTitle, Messages.getErrorIcon)
        return
    }

    inWriteAction {
      file.getVirtualFile.move(project, directory.getVirtualFile)
    }
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
    ModuleRootManager.getInstance(module).getContentEntries.flatMap(_.getSourceFolders).distinctBy(_.getPackagePrefix) match {
      case Array(sourceFolder) => Some(sourceFolder)
      case _ => None
    }
}