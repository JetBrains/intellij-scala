package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.psi.PsiAnchor

/**
  * @author Nikolay.Tropin
  */
case class RangeInfo(firstPsi: PsiAnchor,
                     lastPsi: PsiAnchor,
                     importInfos: Seq[ImportInfo],
                     usedImportedNames: Set[String],
                     isLocal: Boolean) {

  def startOffset = firstPsi.getStartOffset

  def endOffset = lastPsi.getEndOffset
}
