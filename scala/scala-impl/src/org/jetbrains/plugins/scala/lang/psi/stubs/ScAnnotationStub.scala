package org.jetbrains.plugins.scala.lang.psi.stubs

import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationExpr}

trait ScAnnotationStub extends ScStubElement[ScAnnotation] {
  def name: Option[String]
  def typeElement: Option[ScTypeElement]
  def annotationText: String
  def annotationExpr: Option[ScAnnotationExpr]
}