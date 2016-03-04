package org.jetbrains.plugins.scala.editor.importOptimizer

/**
  * @author Nikolay.Tropin
  */
case class RangeInfo(namesAtRangeStart: Set[String],
                     importInfos: Seq[ImportInfo],
                     usedImportedNames: Set[String],
                     isLocal: Boolean)
