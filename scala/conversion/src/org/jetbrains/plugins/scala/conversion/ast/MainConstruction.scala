package org.jetbrains.plugins.scala.conversion.ast

import scala.collection.mutable.ArrayBuffer

case class MainConstruction() extends IntermediateNode {
  val children = new ArrayBuffer[IntermediateNode]()

  def addChild(child: IntermediateNode): children.type = children += child

  def addChildren(inChildren: Array[IntermediateNode]): children.type = children ++= inChildren
}
