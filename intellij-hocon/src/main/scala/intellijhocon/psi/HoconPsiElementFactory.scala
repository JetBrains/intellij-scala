package intellijhocon
package psi

import com.intellij.psi.{PsiFileFactory, PsiManager}
import lang.HoconLanguageFileType

object HoconPsiElementFactory {
  private val Dummy = "dummy."

  def createString(contents: String, manager: PsiManager): HString = {
    val text = s"k = $contents"
    val dummyFile = PsiFileFactory.getInstance(manager.getProject)
            .createFileFromText(Dummy + HoconLanguageFileType.DefaultExtension, HoconLanguageFileType, text)
    dummyFile.getFirstChild.getFirstChild.getFirstChild.getLastChild.asInstanceOf[HString]
  }

}
