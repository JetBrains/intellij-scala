package org.jetbrains.plugins.scala
package conversion.copy

import java.awt.datatransfer.DataFlavor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData

/**
 * Pavel Fatin
 */

case class Associations(associations: Seq[Association]) extends TextBlockTransferableData with Cloneable {
  def setOffsets(offsets: Array[Int], index: Int) = 0

  def getOffsets(offsets: Array[Int], index: Int) = 0

  def getOffsetCount = 0

  def getFlavor = Associations.Flavor

  override def clone() = copy()
}

object Associations {
  lazy val Flavor = new DataFlavor(classOf[Associations], "ScalaReferenceData")
}