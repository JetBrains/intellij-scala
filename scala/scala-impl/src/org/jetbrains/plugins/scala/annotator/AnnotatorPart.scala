package org.jetbrains.plugins.scala.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement

import scala.reflect.ClassTag

/**
  * Pavel Fatin
  */
abstract class AnnotatorPart[T <: ScalaPsiElement : ClassTag] {
  def annotate(element: T,
               holder: AnnotationHolder,
               typeAware: Boolean): Unit
}
