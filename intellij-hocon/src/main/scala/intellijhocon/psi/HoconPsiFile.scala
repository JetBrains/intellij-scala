package intellijhocon
package psi

import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.{FileViewProvider, PsiElementVisitor}
import intellijhocon.lang.HoconLanguageFileType
import intellijhocon.parser.HoconElementType.HoconFileElementType

class HoconPsiFile(provider: FileViewProvider) extends PsiFileImpl(HoconFileElementType, HoconFileElementType, provider) {
  def accept(visitor: PsiElementVisitor): Unit =
    visitor.visitFile(this)

  def getFileType: FileType =
    HoconLanguageFileType
}
