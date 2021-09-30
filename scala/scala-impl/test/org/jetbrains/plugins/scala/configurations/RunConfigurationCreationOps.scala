package org.jetbrains.plugins.scala.configurations

import com.intellij.execution.PsiLocation
import com.intellij.lang.Language
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{LocalFileSystem, VirtualFile}
import com.intellij.psi.{PsiElement, PsiManager}
import org.jetbrains.plugins.scala.base.ScalaSdkOwner
import org.jetbrains.plugins.scala.configurations.TestLocation.{CaretLocation, CaretLocation2}
import org.jetbrains.plugins.scala.extensions.inReadAction
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel
import org.jetbrains.plugins.scala.{Scala3Language, ScalaLanguage}

import java.io.File

trait RunConfigurationCreationOps extends ScalaSdkOwner {

  private def scalaLanguage: Language = {
    if (this.version.languageLevel >= ScalaLanguageLevel.Scala_3_0) Scala3Language.INSTANCE
    else ScalaLanguage.INSTANCE
  }

  protected final def createPsiLocation(
    caretLocation: CaretLocation,
    module: Module,
    srcDir: File,
  ): PsiLocation[PsiElement] = {
    val project = module.getProject
    val psiElement = findPsiElement(caretLocation, project, srcDir)
    new PsiLocation(project, module, psiElement)
  }

  protected final def findPsiElement(
    caretLocation: CaretLocation,
    project: Project,
    srcDir: File,
  ): PsiElement = {
    val ioFile = new File(srcDir, caretLocation.fileName)
    val vFile = LocalFileSystem.getInstance.refreshAndFindFileByIoFile(ioFile)

    val myManager = PsiManager.getInstance(project)

    inReadAction {
      val psiFile = myManager.findViewProvider(vFile).getPsi(scalaLanguage)
      val document = FileDocumentManager.getInstance().getDocument(vFile)
      val lineStartOffset = document.getLineStartOffset(caretLocation.line)
      psiFile.findElementAt(lineStartOffset + caretLocation.column)
    }
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