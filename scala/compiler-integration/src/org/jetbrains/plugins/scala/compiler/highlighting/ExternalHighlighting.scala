package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.compiler.highlighting.ExternalHighlighting.RangeInfo

/**
 * All information that needed for highlighting.
 *
 * Note: potentially this class can be a sealed ADT.
 *
 * @param range can be None if a message was produce for a file, but no location was specified (for some reason)
 */
final case class ExternalHighlighting(highlightType: HighlightInfoType,
                                      message: String,
                                      rangeInfo: Option[RangeInfo])

object ExternalHighlighting {
  sealed trait RangeInfo

  object RangeInfo {
    final case class Range(problemStart: PosInfo, problemEnd: PosInfo) extends RangeInfo
    final case class Pointer(pointer: PosInfo) extends RangeInfo
  }
}