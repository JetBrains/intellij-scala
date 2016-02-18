package org.jetbrains.plugins.scala.conversion.ast

/**
  * Created by Kate Ustyuzhanina
  * on 10/26/15
  */
case class MethodConstruction(modifiers: IntermediateNode, name: String, typeParams: Seq[IntermediateNode],
                              params: IntermediateNode, body: Option[IntermediateNode],
                              retType: IntermediateNode) extends IntermediateNode


trait Constructor

case class ConstructorSimply(modifiers: IntermediateNode, typeParams: Seq[IntermediateNode],
                             params: IntermediateNode, body: Option[IntermediateNode]) extends IntermediateNode
case class PrimaryConstruction(params: Seq[(String, IntermediateNode, Boolean)], superCall: IntermediateNode,
                               body: Seq[IntermediateNode],  modifiers: IntermediateNode)
  extends IntermediateNode with Constructor



