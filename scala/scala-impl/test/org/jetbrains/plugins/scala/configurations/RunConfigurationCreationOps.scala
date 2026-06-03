package org.jetbrains.plugins.scala.configurations

import com.intellij.execution.PsiLocation
import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.{Module, ModuleManager}
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.psi.{PsiDirectory, PsiElement, PsiManager}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.configurations.RunConfigCreationLocation.{CaretLocation, CaretLocation2}
import org.jetbrains.plugins.scala.extensions.{PathExt, inReadAction}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager

import java.nio.file.Path

trait RunConfigurationCreationOps extends ScalaSdkOwner {

  private def scalaLanguage: Language = this.version.language

  protected final def createPsiLocation(
    caretLocation: CaretLocation,
    module: Module,
    srcDir: Path,
  ): PsiLocation[PsiElement] = {
    val project = module.getProject
    val psiElement = findPsiElement(caretLocation, project, srcDir)
    new PsiLocation(project, module, psiElement)
  }

  protected final def findPsiElement(
    caretLocation: CaretLocation,
    project: Project,
    srcDir: Path,
  ): PsiElement = {
    val vFile = findTestFile(srcDir, caretLocation.fileName)
    val myManager = PsiManager.getInstance(project)

    inReadAction {
      val psiFile = myManager.findViewProvider(vFile).getPsi(scalaLanguage)
      val document = FileDocumentManager.getInstance().getDocument(vFile)
      val lineStartOffset = document.getLineStartOffset(caretLocation.line)
      psiFile.findElementAt(lineStartOffset + caretLocation.column)
    }
  }

  protected def findPackageSingleDirectory(project: Project, packageName: String) = {
    val psiPackage = ScalaPsiManager.instance(project).getCachedPackage(packageName)
    psiPackage.map(_.getDirectories().head) match {
      case Some(dir) => dir
      case None =>
        throw new RuntimeException(s"Failed to create run configuration for test from package $packageName")
    }
  }

  protected def findModuleContentRootEnsureCreated(project: Project, moduleName: String): PsiDirectory = {
    val manager = ModuleManager.getInstance(project)
    val module = manager.findModuleByName(moduleName)
    val moduleRoot = ModuleRootManager.getInstance(module).getContentRoots.head
    PsiDirectoryFactory.getInstance(project).createDirectory(moduleRoot)
  }

  protected final def findTestFile(srcDir: Path, testFileName: String): VirtualFile = {
    val nioFile = srcDir / testFileName
    LocalFileSystem.getInstance.refreshAndFindFileByNioFile(nioFile)
  }

  protected final def findPsiElement(
    caretLocation: CaretLocation2,
    project: Project,
  ): PsiElement = {
    val myManager = PsiManager.getInstance(project)
    val vFile = caretLocation.virtualVile

    inReadAction {
      val psiFile = myManager.findViewProvider(vFile).getPsi(scalaLanguage)
      val document = FileDocumentManager.getInstance().getDocument(vFile)
      val lineStartOffset = document.getLineStartOffset(caretLocation.line)
      psiFile.findElementAt(lineStartOffset + caretLocation.column)
    }
  }

  protected final def findPsiFile(
    virtualFile: VirtualFile,
    project: Project,
  ): PsiElement = {
    val myManager = PsiManager.getInstance(project)
    inReadAction {
      myManager.findViewProvider(virtualFile).getPsi(scalaLanguage)
    }
  }
}
