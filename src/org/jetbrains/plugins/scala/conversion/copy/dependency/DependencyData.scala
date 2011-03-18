package org.jetbrains.plugins.scala.conversion.copy.dependency

import java.awt.datatransfer.DataFlavor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData

/**
 * Pavel Fatin
 */

case class DependencyData(dependencies: Seq[Dependency]) extends TextBlockTransferableData with Cloneable {
  def setOffsets(offsets: Array[Int], index: Int) = 0

  def getOffsets(offsets: Array[Int], index: Int) = 0

  def getOffsetCount = 0

  def getFlavor = DependencyData.Flavor

  override def clone() = copy()
}

object DependencyData {
  lazy val Flavor = new DataFlavor(classOf[Dependency], "ScalaReferenceData")
}