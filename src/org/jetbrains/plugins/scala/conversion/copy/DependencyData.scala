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

sealed trait Dependency {
  def startOffset: Int

  def endOffset: Int
}

case class TypeDependency(startOffset: Int, endOffset: Int, className: String) extends Dependency with Cloneable {
  override def clone() = new TypeDependency(startOffset, endOffset, className)
}

object TypeDependency {
  def apply(element: PsiElement, startOffset: Int, className: String) = {
    val range = element.getTextRange
    new TypeDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className)
  }
}

case class PrimaryConstructorDependency(startOffset: Int, endOffset: Int, className: String) extends Dependency with Cloneable {
  override def clone() = new PrimaryConstructorDependency(startOffset, endOffset, className)
}

object PrimaryConstructorDependency {
  def apply(element: PsiElement, startOffset: Int, className: String) = {
    val range = element.getTextRange
    new PrimaryConstructorDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className)
  }
}

case class MemberDependency(startOffset: Int, endOffset: Int, className: String, memberName: String) extends Dependency with Cloneable {
  override def clone() = new MemberDependency(startOffset, endOffset, className, memberName)
}

object MemberDependency {
  def apply(element: PsiElement, startOffset: Int, className: String, memberName: String) = {
    val range = element.getTextRange
    new MemberDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className, memberName)
  }
}

case class ConversionDependency(startOffset: Int, endOffset: Int, className: String, memberName: String) extends Dependency with Cloneable {
  override def clone() = new ConversionDependency(startOffset, endOffset, className, memberName)
}

object ConversionDependency {
  def apply(element: PsiElement, startOffset: Int, className: String, memberName: String) = {
    val range = element.getTextRange
    new ConversionDependency(range.getStartOffset - startOffset, range.getEndOffset - startOffset, className, memberName)
  }
}


