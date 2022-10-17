package org.jetbrains.plugins.scala.conversion.ast

case class AnnotationConstruction(
  inAnnotation: Boolean,
  attributes: Seq[(Option[IntermediateNode], Option[IntermediateNode])],
  name: Option[IntermediateNode]
) extends IntermediateNode