package org.jetbrains.plugins.scala
package lang
package surroundWith
package descriptors

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 *
 */
import com.intellij.lang.surroundWith.SurroundDescriptor

object ScalaSurroundDescriptors extends SurroundDescriptors {
  private lazy val SURROUND_DESCRIPTORS : Array[SurroundDescriptor] = Array(
    new ScalaExpressionSurroundDescriptor(),
    new ScalaDocCommentDataSurroundDescriptor(),
    new ScalaIgnoreErrorHighlightingSurroundDescriptor()
  )

  override def getSurroundDescriptors : Array[SurroundDescriptor] = SURROUND_DESCRIPTORS
}
