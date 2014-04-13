package intellijhocon.psi

import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.{PsiElementVisitor, FileViewProvider}
import com.intellij.openapi.fileTypes.FileType
import intellijhocon.HoconLanguageFileType
import intellijhocon.HoconElementType.HoconFileElementType

class HoconPsiFile(provider: FileViewProvider) extends PsiFileImpl(HoconFileElementType, HoconFileElementType, provider) {
  def accept(visitor: PsiElementVisitor): Unit =
    visitor.visitFile(this)

  def getFileType: FileType =
    HoconLanguageFileType
}
