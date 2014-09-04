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

class ScalaSurroundDescriptors extends SurroundDescriptors {
  private val SURROUND_DESCRIPTORS : Array[SurroundDescriptor] = Array.apply(
    new ScalaExpressionSurroundDescriptor(), new ScalaDocCommentDataSurroundDescriptor(), new ScalaIgnoreErrorHighlightingSurroundDescriptor()
  )

  override def getSurroundDescriptors : Array[SurroundDescriptor] = SURROUND_DESCRIPTORS
}
