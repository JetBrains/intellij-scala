package intellijhocon
package psi

import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.{PsiElementVisitor, FileViewProvider}
import com.intellij.openapi.fileTypes.FileType
import intellijhocon.parser.HoconElementType
import HoconElementType.HoconFileElementType
import intellijhocon.lang.HoconLanguageFileType

class HoconPsiFile(provider: FileViewProvider) extends PsiFileImpl(HoconFileElementType, HoconFileElementType, provider) {
  def accept(visitor: PsiElementVisitor): Unit =
    visitor.visitFile(this)

  def getFileType: FileType =
    HoconLanguageFileType
}
