package org.jetbrains.plugins.scala.worksheet.ui.printers.repl

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.CalledWithReadLock
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import scala.collection.immutable.Seq

sealed trait QueuedPsi

object QueuedPsi {

  case class QueuedPsiSeq(elements: Seq[PsiElement]) extends QueuedPsi
  /**
   * Used to join type definitions which have to go in one chunk to REPL instance
   *
   * @param typedefs companion class/trait + object (in any order)
   */
  case class RelatedTypeDefs(typedefs: Seq[ScTypeDefinition]) extends QueuedPsi

  def psiContentOffset(psi: PsiElement): Int = {
    val startPsi = psi.firstChildNotWhitespaceComment.getOrElse(psi)
    startPsi.startOffset
  }

  implicit final class QueuedPsiExt(private val target: QueuedPsi) extends AnyVal {

    def getElements: Seq[PsiElement] = target match {
      case QueuedPsiSeq(elements)    => elements
      case RelatedTypeDefs(typedefs) => typedefs
    }

    /** @return underlying psi(-s) is valid */
    @CalledWithReadLock
    def isValid: Boolean =
      getElements.forall(_.isValid)

    def textRange: TextRange =
      new TextRange(first.startOffset, last.endOffset)

    private def first: PsiElement = getElements.head
    private def last: PsiElement = getElements.last

    def firstElementOffset: Int = QueuedPsi.psiContentOffset(first)
    def lastElementOffset: Int = QueuedPsi.psiContentOffset(last)
  }
}