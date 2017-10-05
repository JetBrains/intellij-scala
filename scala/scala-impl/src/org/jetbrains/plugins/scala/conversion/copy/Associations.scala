package org.jetbrains.plugins.scala
package conversion.copy

import java.awt.datatransfer.DataFlavor

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.util.TextRange

/**
 * Pavel Fatin
 */

case class Associations(associations: Seq[Association]) extends TextBlockTransferableData with Cloneable {
  def setOffsets(offsets: Array[Int], _index: Int): Int = {
    var index = _index
    for (association <- associations) {
      association.range = new TextRange(offsets(index), offsets(index + 1))
      index += 2
    }
    index
  }

  def getOffsets(offsets: Array[Int], _index: Int): Int = {
    var index = _index
    for (association <- associations) {
      offsets(index) = association.range.getStartOffset
      index += 1
      offsets(index) = association.range.getEndOffset
      index += 1
    }
    index
  }

  def getOffsetCount: Int = associations.length * 2

  def getFlavor = Associations.Flavor

  override def clone(): Associations = copy()
}

object Associations {
  lazy val Flavor = new DataFlavor(classOf[Associations], "ScalaReferenceData")
}