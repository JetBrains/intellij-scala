package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.psi.{PsiAnchor, PsiWhiteSpace}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt

final case class ImportRangeInfo(
  firstPsi: PsiAnchor,
  lastPsi: PsiAnchor,
  importStmtWithInfos: Seq[(ScImportStmt, Seq[ImportInfo])],
  usedImportedNames: Set[String],
  isLocal: Boolean
) {
  val startOffset: Int = firstPsi.getStartOffset
  val endOffset: Int = lastPsi.getEndOffset

  lazy val startOffsetAccepted: Int = {
    val prevWhitespaceLength: Int = firstPsi.retrieve() match {
      case null => 0
      case last => last.getPrevSibling match {
        case ws: PsiWhiteSpace => ws.getTextLength
        case _ => 0
      }
    }
    startOffset - prevWhitespaceLength
  }
  lazy val endOffsetAccepted: Int = {
    val nextWhitespaceLength: Int = lastPsi.retrieve() match {
      case null => 0
      case last => last.getNextSibling match {
        case ws: PsiWhiteSpace => ws.getTextLength
        case _ => 0
      }
    }
    endOffset + nextWhitespaceLength
  }

  def rangeCanAccept(offset: Int): Boolean =
    startOffsetAccepted <= offset && offset <= endOffsetAccepted
}