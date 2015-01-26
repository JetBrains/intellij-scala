package org.jetbrains.plugins.hocon.psi

import com.intellij.psi.{PsiFileFactory, PsiManager}
import org.jetbrains.plugins.hocon.lang.HoconFileType

object HoconPsiElementFactory {
  private val Dummy = "dummy."

  def createString(contents: String, manager: PsiManager): HString = {
    val text = s"k = $contents"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject)
            .createFileFromText(Dummy + HoconFileType.DefaultExtension, HoconFileType, text)
    dummyFile.getFirstChild.getFirstChild.getFirstChild.getLastChild.asInstanceOf[HString]
  }

}
