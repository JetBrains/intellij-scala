package org.jetbrains.plugins.scala.conversion.ast

/**
  * Created by Kate Ustyuzhanina
  * on 10/27/15
  */
case class AnnotaionConstruction(inAnnotation: Boolean, attributes: Seq[(Option[IntermediateNode], Option[IntermediateNode])],
                                 name: Option[IntermediateNode]) extends IntermediateNode {
}


