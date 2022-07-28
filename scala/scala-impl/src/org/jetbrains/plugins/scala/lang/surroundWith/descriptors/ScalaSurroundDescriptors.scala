package org.jetbrains.plugins.scala
package lang
package surroundWith
package descriptors

import com.intellij.lang.surroundWith.SurroundDescriptor

object ScalaSurroundDescriptors extends SurroundDescriptors {
  private lazy val SURROUND_DESCRIPTORS : Array[SurroundDescriptor] = Array(
    new ScalaExpressionSurroundDescriptor(),
    new ScalaDocCommentDataSurroundDescriptor(),
    new ScalaIgnoreErrorHighlightingSurroundDescriptor()
  )

  override def getSurroundDescriptors : Array[SurroundDescriptor] = SURROUND_DESCRIPTORS
}
