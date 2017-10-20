package org.jetbrains.plugins.scala.conversion.ast

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 11/9/15
  */
case class MainConstruction() extends IntermediateNode {
  val children = new ArrayBuffer[IntermediateNode]()

  def addChild(child: IntermediateNode): children.type = children += child

  def addChildren(inChildren: Array[IntermediateNode]): children.type = children ++= inChildren
}
