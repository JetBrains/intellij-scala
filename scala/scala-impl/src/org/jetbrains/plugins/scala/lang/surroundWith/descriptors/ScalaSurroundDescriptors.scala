package org.jetbrains.plugins.scala.lang.surroundWith.descriptors

import com.intellij.lang.surroundWith.SurroundDescriptor
import org.jetbrains.plugins.scala.lang.surroundWith.SurroundDescriptors

object ScalaSurroundDescriptors extends SurroundDescriptors {
  private lazy val SURROUND_DESCRIPTORS : Array[SurroundDescriptor] = Array(
    new ScalaExpressionSurroundDescriptor(),
    new ScalaDocCommentDataSurroundDescriptor(),
    new ScalaIgnoreErrorHighlightingSurroundDescriptor()
  )

  override def getSurroundDescriptors : Array[SurroundDescriptor] = SURROUND_DESCRIPTORS
}
