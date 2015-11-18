package org.jetbrains.plugins.scala.conversion.ast

import org.jetbrains.plugins.scala.conversion.PrettyPrinter

import scala.collection.mutable.ArrayBuffer

/**
  * Created by Kate Ustyuzhanina
  * on 11/9/15
  */
case class MainConstruction() extends IntermediateNode {
  override def print(printer: PrettyPrinter): PrettyPrinter = {
    children.foreach(_.print(printer))
    printer
  }

  val children = new ArrayBuffer[IntermediateNode]()

  def addChild(child: IntermediateNode) = children += child

  def addChildren(inChildren: Array[IntermediateNode]) = children ++= inChildren
}
