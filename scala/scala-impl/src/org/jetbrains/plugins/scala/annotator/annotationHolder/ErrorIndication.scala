package org.jetbrains.plugins.scala
package annotator
package annotationHolder

trait ErrorIndication extends ScalaAnnotationHolder {
  def hadError: Boolean
}
