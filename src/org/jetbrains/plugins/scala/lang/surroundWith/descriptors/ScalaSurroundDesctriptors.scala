package org.jetbrains.plugins.scala.lang.surroundWith.descriptors;

/**
 * User: Dmitry.Krasilschikov
 * Date: 09.01.2007
 * Time: 16:54:29
 */

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.lang.surroundWith.SurroundDescriptor;

import org.jetbrains.plugins.scala.lang.surroundWith.SurroundDescriptors;
//import org.jetbrains.plugins.scala.lang.surroundWith.descriptors.ScalaExpressionSurroundDescriptor

class ScalaSurroundDescriptors extends SurroundDescriptors {
  private val SURROUND_DESCRIPTORS : Array[SurroundDescriptor] = Array.apply(
    new ScalaExpressionSurroundDescriptor()
  )

  override def getSurroundDescriptors : Array[SurroundDescriptor] = SURROUND_DESCRIPTORS
}
