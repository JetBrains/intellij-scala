package org.jetbrains.plugins.scala.lang.surroundWith.descriptors;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 *
 */

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.lang.surroundWith.SurroundDescriptor;

import org.jetbrains.plugins.scala.lang.surroundWith.SurroundDescriptors;

class ScalaSurroundDescriptors extends SurroundDescriptors {
  private val SURROUND_DESCRIPTORS : Array[SurroundDescriptor] = Array.apply(
    new ScalaExpressionSurroundDescriptor()
  )

  override def getSurroundDescriptors : Array[SurroundDescriptor] = SURROUND_DESCRIPTORS
}
