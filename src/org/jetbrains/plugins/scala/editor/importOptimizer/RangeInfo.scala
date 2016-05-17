package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.psi.PsiAnchor

/**
  * @author Nikolay.Tropin
  */
case class RangeInfo(startPsi: PsiAnchor,
                     importInfos: Seq[ImportInfo],
                     usedImportedNames: Set[String],
                     isLocal: Boolean)
