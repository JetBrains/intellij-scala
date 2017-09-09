package org.jetbrains.plugins.scala.util

import java.nio.file.{Path, Paths}

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{CharsetToolkit, VfsUtil, VirtualFile}
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert.{assertNotNull, assertTrue}

/**
  * Nikolay.Tropin
  * 14-Aug-17
  */
object PsiFileTestUtil {
  def addFileToProject(fileName: String, text: String, project: Project): PsiFile =
    addFileToProject(Paths.get(fileName), text, project)

  def addFileToProject(path: Path, text: String, project: Project): PsiFile = {
    def dirNames(path: Path): Seq[String] =
      (0 until path.getNameCount - 1)
        .map(i => path.getName(i).toString)

    val fileName = path.getFileName.toString

    def createDir(parent: VirtualFile, name: String): VirtualFile = parent.createChildDirectory(null, name)

    def createVFile(path: Path) = {
      val sourceRoot = LightPlatformTestCase.getSourceRoot
      val dir = dirNames(path).foldLeft(sourceRoot)(createDir)
      val vFile = dir.createChildData(null, fileName)
      vFile
    }

    inWriteAction {
      val vFile: VirtualFile = createVFile(path)

      VfsUtil.saveText(vFile, text)
      val psiFile = LightPlatformTestCase.getPsiManager.findFile(vFile)
      assertNotNull("Can't create PsiFile for '" + fileName + "'. Unknown file type most probably.", vFile)
      assertTrue(psiFile.isPhysical)
      vFile.setCharset(CharsetToolkit.UTF8_CHARSET)
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      psiFile
    }
  }
}
