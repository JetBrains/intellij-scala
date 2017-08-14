package org.jetbrains.plugins.scala.util

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.{CharsetToolkit, VfsUtil}
import com.intellij.psi.{PsiDocumentManager, PsiFile}
import com.intellij.testFramework.LightPlatformTestCase
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.junit.Assert.{assertNotNull, assertTrue}

/**
  * Nikolay.Tropin
  * 14-Aug-17
  */
object PsiFileTestUtil {
  def addFileToProject(fileName: String, text: String, project: Project): PsiFile = {
    inWriteAction {
      val vFile = LightPlatformTestCase.getSourceRoot.createChildData(null, fileName)
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
