package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.psi.PsiAnchor

/**
  * @author Nikolay.Tropin
  */
case class RangeInfo(firstPsi: PsiAnchor,
                     lastPsi: PsiAnchor,
                     importInfos: collection.Seq[ImportInfo],
                     usedImportedNames: collection.Set[String],
                     isLocal: Boolean) {

  def startOffset: Int = firstPsi.getStartOffset

  def endOffset: Int = lastPsi.getEndOffset
}
