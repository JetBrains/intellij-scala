package org.jetbrains.plugins.scala.refactoring.move.directory

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.{JavaPsiFacade, PsiDirectory, PsiPackage}
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor
import com.intellij.refactoring.rename.RenamePsiPackageProcessor
import com.intellij.testFramework.{PlatformTestUtil, PsiTestUtil}
import junit.framework.TestCase.{assertNotNull, assertTrue}
import org.jetbrains.plugins.scala.extensions.inWriteCommandAction
import org.jetbrains.plugins.scala.refactoring.move.ScalaMoveTestBase
import org.jetbrains.plugins.scala.refactoring.move.directory.ScalaMoveDirectoryWithClassesTestBase.{MovePackageTestAction, PackageTestAction, RenamePackageTestAction}

import scala.util.chaining.scalaUtilChainingOps

// Based on [[com.intellij.java.refactoring.MovePackageAsDirectoryTest]]
abstract class ScalaMoveDirectoryWithClassesTestBase extends ScalaMoveTestBase {
  override protected def getTestDataRoot: String = super.getTestDataRoot + "directory/"

  override protected def configureModuleSources(module: Module, rootDir: VirtualFile): Unit = {
    val isMultipleSourceRoots = rootDir.getChildren
      .forall(child => child.isDirectory && child.getName.startsWith("src"))

    if (isMultipleSourceRoots) {
      PsiTestUtil.addContentRoot(module, rootDir)
      rootDir.getChildren.foreach(PsiTestUtil.addSourceRoot(module, _))
    } else {
      super.configureModuleSources(module, rootDir)
    }
  }

  protected def doTest(testAction: PackageTestAction): Unit = try {
    testAction.run()

    implicit val project: Project = getProject
    inWriteCommandAction {
      PostprocessReformattingAspect.getInstance(project).doPostponedFormatting()
    }

    FileDocumentManager.getInstance().saveAllDocuments()
    PlatformTestUtil.assertDirectoriesEqual(getRootAfter, getRootBefore)
  } catch {
    case ex: Throwable =>
      // print folders to conveniently navigate to them from failed test console
      System.err.println(s"Folder before path: $getRootBefore")
      System.err.println(s"Folder after path: $getRootAfter")
      throw ex
  }

  protected def doRenamePackageTest(sourcePackageName: String, newPackageName: String): Unit =
    doTest(new RenamePackageTestAction(sourcePackageName, newPackageName)(getProject))

  protected def doMovePackageTest(sourcePackageName: String = "pack1", targetPkgName: String = "targetPack"): Unit =
    doTest(new MovePackageTestAction(sourcePackageName, targetPkgName)(getProject))
}

object ScalaMoveDirectoryWithClassesTestBase {
  abstract class PackageTestAction(implicit project: Project) {
    protected def findPackage(name: String): PsiPackage = {
      val psiFacade = JavaPsiFacade.getInstance(project)
      psiFacade.findPackage(name)
        .tap(assertNotNull)
    }

    def run(): Unit
  }

  class RenamePackageTestAction(sourcePackageName: String, newPackageName: String)(implicit project: Project) extends PackageTestAction {
    override def run(): Unit = {
      val sourcePackage = findPackage(sourcePackageName)
      val processor = RenamePsiPackageProcessor.createRenameMoveProcessor(newPackageName, sourcePackage, false, false)
      processor.run()
      FileDocumentManager.getInstance().saveAllDocuments()
    }
  }

  class MovePackageTestAction(sourcePackageName: String, targetPackageName: String)(implicit project: Project) extends PackageTestAction {
    override def run(): Unit = {
      val sourceDirs = getSortedPackageDirs(sourcePackageName)
      assertTrue(sourceDirs.nonEmpty)
      val sourceDir = sourceDirs.head

      val targetDirs = getSortedPackageDirs(targetPackageName)
      assertTrue(targetDirs.nonEmpty)
      val targetDir = targetDirs.head

      val processor = new MoveDirectoryWithClassesProcessor(project, Array(sourceDir), targetDir, false, false, true, null)
      processor.run()

      FileDocumentManager.getInstance().saveAllDocuments()
    }

    private def getSortedPackageDirs(packageName: String): Array[PsiDirectory] = {
      val pkg = findPackage(packageName)
      val pkgDirs = pkg.getDirectories
      pkgDirs.sortBy(_.getVirtualFile.getPresentableUrl)
    }
  }
}
