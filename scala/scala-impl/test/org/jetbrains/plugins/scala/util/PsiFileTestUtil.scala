package org.jetbrains.plugins.scala.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.psi.{PsiDocumentManager, PsiFile, PsiManager}
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert.{assertNotNull, assertTrue}

import java.nio.charset.StandardCharsets
import java.nio.file.{Path, Paths}

object PsiFileTestUtil {
  def addFileToProject(fileName: String, text: String, project: Project): PsiFile =
    addFileToProject(Paths.get(fileName), text, project)

  def addFileToProject(path: Path, text: String, project: Project): PsiFile = {

    val fileName = path.getFileName.toString

    def dirNames(path: Path): Seq[String] =
      Iterator.tabulate(path.getNameCount - 1)(path.getName(_).toString).toSeq // last name in path is file itself

    def createDir(parent: VirtualFile, name: String): VirtualFile = parent.createChildDirectory(null, name)

    def createVFile(path: Path): VirtualFile = {
      val sourceRoot = LightPlatformTestCase.getSourceRoot
      val dir = dirNames(path).foldLeft(sourceRoot)(createDir)
      val vFile = dir.createChildData(null, fileName)
      vFile
    }

    inWriteAction {
      val vFile: VirtualFile = createVFile(path)

      VfsUtil.saveText(vFile, text)
      val psiFile = PsiManager.getInstance(project).findFile(vFile)
      assertNotNull("Can't create PsiFile for '" + fileName + "'. Unknown file type most probably.", vFile)
      assertTrue(psiFile.isPhysical)
      vFile.setCharset(StandardCharsets.UTF_8)
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      psiFile
    }
  }
}
