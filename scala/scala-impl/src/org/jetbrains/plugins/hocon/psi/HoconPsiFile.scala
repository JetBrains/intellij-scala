package org.jetbrains.plugins.hocon.psi

import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.{FileViewProvider, PsiComment, PsiElement, PsiElementVisitor}
import org.jetbrains.plugins.hocon.CommonUtil.notWhiteSpaceSibling
import org.jetbrains.plugins.hocon.lang.HoconFileType
import org.jetbrains.plugins.hocon.parser.HoconElementType.HoconFileElementType

import scala.annotation.tailrec

class HoconPsiFile(provider: FileViewProvider) extends PsiFileImpl(HoconFileElementType, HoconFileElementType, provider) {
  def accept(visitor: PsiElementVisitor): Unit =
    visitor.visitFile(this)

  def getFileType: FileType =
    HoconFileType

  def toplevelEntries: HObjectEntries = {
    @tailrec
    def entriesInner(child: PsiElement): HObjectEntries = child match {
      case obj: HObject => obj.entries
      case ets: HObjectEntries => ets
      case comment: PsiComment =>
        val sibling = notWhiteSpaceSibling(comment)(_.getNextSibling)
        entriesInner(sibling)
    }


    entriesInner(getFirstChild)
  }

  def toplevelObject: Option[HObject] = getFirstChild match {
    case obj: HObject => Some(obj)
    case _ => None
  }

  def elementsAt(offset: Int): Iterator[PsiElement] = {
    val leaf = findElementAt(offset)
    Iterator.iterate(leaf)(_.getParent).takeWhile(e => e != null && (e ne this))
  }
}
