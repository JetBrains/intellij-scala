package org.jetbrains.plugins.scala.externalHighlighters

import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import org.jetbrains.jps.incremental.scala.Client.PosInfo
import org.jetbrains.plugins.scala.externalHighlighters.ExternalHighlighting.Pos

/**
 * All information that needed for highlighting.
 *
 * Note: potentially this class can be a sealed ADT.
 */
final case class ExternalHighlighting(highlightType: HighlightInfoType,
                                      message: String,
                                      from: Pos,
                                      to: Pos)

object ExternalHighlighting {
  
  sealed trait Pos
  
  object Pos {
    final case class LineColumn(line: Int, column: Int) extends Pos
    final case class Offset(offset: Int) extends Pos
    
    def fromPosInfo(posInfo: PosInfo): Option[Pos] =
      Option(posInfo).collect {
        // TODO The cases should be in different order. But the highlighting works incorrect with offset by some reason
        case PosInfo(Some(line), Some(column), _) => LineColumn(line.toInt, column.toInt)
        case PosInfo(_, _, Some(offset)) => Offset(offset.toInt)
      }
  }
}