package org.jetbrains.plugins.scala.annotator.annotationHolder

import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder

trait ErrorIndication extends ScalaAnnotationHolder {
  def hadError: Boolean
}
