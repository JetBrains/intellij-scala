package org.jetbrains.plugins.hocon.psi

import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.{PsiComment, FileViewProvider, PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.hocon.lang.HoconFileType
import org.jetbrains.plugins.hocon.parser.HoconElementType.HoconFileElementType
import org.jetbrains.plugins.scala.extensions.PsiElementExt

class HoconPsiFile(provider: FileViewProvider) extends PsiFileImpl(HoconFileElementType, HoconFileElementType, provider) {
  def accept(visitor: PsiElementVisitor): Unit =
    visitor.visitFile(this)

  def getFileType: FileType =
    HoconFileType

  def toplevelEntries = {
    def entriesInner(child: PsiElement): HObjectEntries = child match {
      case obj: HObject => obj.entries
      case ets: HObjectEntries => ets
      case comment: PsiComment => entriesInner(comment.getNextSiblingNotWhitespace)
    }
    
    
    
    entriesInner(getFirstChild)
  }

  def toplevelObject = getFirstChild match {
    case obj: HObject => Some(obj)
    case _ => None
  }

  def elementsAt(offset: Int): Iterator[PsiElement] = {
    val leaf = findElementAt(offset)
    Iterator.iterate(leaf)(_.getParent).takeWhile(e => e != null && (e ne this))
  }
}
