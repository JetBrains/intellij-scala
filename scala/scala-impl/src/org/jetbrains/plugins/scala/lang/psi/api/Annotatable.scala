package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.lang.annotation.AnnotationHolder

trait Annotatable {
  def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {}
}
