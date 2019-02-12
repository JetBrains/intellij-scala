package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.lang.annotation.AnnotationHolder

trait Annotatable {
  // TODO Platform-agnostic API, something akin to "def errors: Seq[Error]", which
  // TODO depends neither on the IDEA's annotator classes, nor on the side effects.
  def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {}
}
