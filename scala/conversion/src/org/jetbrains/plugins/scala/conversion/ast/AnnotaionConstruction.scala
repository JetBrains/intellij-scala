package org.jetbrains.plugins.scala.conversion.ast

case class AnnotaionConstruction(inAnnotation: Boolean, attributes: Seq[(Option[IntermediateNode], Option[IntermediateNode])],
                                 name: Option[IntermediateNode]) extends IntermediateNode {
}


