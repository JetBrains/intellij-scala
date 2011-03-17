package org.jetbrains.plugins.scala.conversion.copy

import java.awt.datatransfer.DataFlavor
import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.psi.PsiElement

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

trait Dependency {
  def startOffset: Int

  def endOffset: Int

  def qClassName: String
}

class TypeDependency(val startOffset: Int, val endOffset: Int, val qClassName: String) extends Dependency with Cloneable {
  override def clone() = new TypeDependency(startOffset, endOffset, qClassName)
}

object TypeDependency {
  def apply(element: PsiElement, startOffset: Int, name: String) = {
    val range = element.getTextRange
    new TypeDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, name)
  }
}

class PrimaryConstructorDependency(val startOffset: Int, val endOffset: Int, val qClassName: String) extends Dependency with Cloneable {
  override def clone() = new PrimaryConstructorDependency(startOffset, endOffset, qClassName)
}

object PrimaryConstructorDependency {
  def apply(element: PsiElement, startOffset: Int, name: String) = {
    val range = element.getTextRange
    new PrimaryConstructorDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, name)
  }
}

