package org.jetbrains.plugins.hocon.psi

import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.{FileViewProvider, PsiElementVisitor}
import org.jetbrains.plugins.hocon.lang.HoconLanguageFileType
import org.jetbrains.plugins.hocon.parser.HoconElementType.HoconFileElementType

class HoconPsiFile(provider: FileViewProvider) extends PsiFileImpl(HoconFileElementType, HoconFileElementType, provider) {
  def accept(visitor: PsiElementVisitor): Unit =
    visitor.visitFile(this)

  def getFileType: FileType =
    HoconLanguageFileType

  def toplevelEntries = getFirstChild match {
    case obj: HObject => obj.entries
    case ets: HObjectEntries => ets
  }

  def toplevelObject = getFirstChild match {
    case obj: HObject => Some(obj)
    case _ => None
  }
}
