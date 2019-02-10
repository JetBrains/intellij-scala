package org.jetbrains.plugins.scala
package lang
package refactoring

import java.awt.datatransfer.DataFlavor

import com.intellij.codeInsight.editorActions.TextBlockTransferableData
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.dependency.Dependency
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference

abstract class AssociationsData(val associations: Array[AssociationsData.Association],
                                private val companion: AssociationsData.Companion[_]) extends TextBlockTransferableData {

  import AssociationsData._

  override final def getOffsetCount: Int = 2 * associations.length

  override final def getOffsets(offsets: Array[Int], startIndex: Int): Int =
    this (offsets, startIndex) {
      case (array, index) => array(index) = associations(index).range
    }

  override final def setOffsets(offsets: Array[Int], startIndex: Int): Int =
    this (offsets, startIndex) {
      case (array, index) => associations(index).range = array(index)
    }

  override final def getFlavor: DataFlavor = companion.flavor

  private def apply(offsets: Array[Int], startIndex: Int)
                   (action: (OffsetsArray, Int) => Unit) = {
    val array = new OffsetsArray(offsets, startIndex)

    for {
      index <- associations.indices
    } action(array, index)

    startIndex + getOffsetCount
  }
}

object AssociationsData {

  case class Association(kind: dependency.DependencyKind,
                         path: dependency.Path,
                         var range: TextRange) {

    def isSatisfiedIn(element: PsiElement): Boolean = element match {
      case reference: ScReference =>
        Dependency.dependencyFor(reference).exists {
          case Dependency(`kind`, _, `path`) => true
          case _ => false
        }
      case _ => false
    }
  }

  abstract class Companion[D <: AssociationsData](representationClass: Class[D],
                                                  humanPresentableName: String) {
    lazy val flavor = new DataFlavor(representationClass, humanPresentableName)
  }

  private class OffsetsArray(private val offsets: (Array[Int], Int)) extends AnyVal {

    def apply(index: Int): TextRange = {
      val (offsets, offset) = startIndex(index)

      TextRange.create(
        offsets(offset),
        offsets(offset + 1)
      )
    }

    def update(index: Int, range: TextRange): Unit = {
      val (offsets, offset) = startIndex(index)

      offsets(offset) = range.getStartOffset
      offsets(offset + 1) = range.getEndOffset
    }

    private def startIndex(index: Int) = {
      val (offsets, startIndex) = this.offsets
      (offsets, startIndex + 2 * index)
    }
  }

}
