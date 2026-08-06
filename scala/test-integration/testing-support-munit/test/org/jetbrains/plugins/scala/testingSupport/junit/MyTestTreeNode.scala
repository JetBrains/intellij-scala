package org.jetbrains.plugins.scala.testingSupport.junit

import com.intellij.execution.testframework.sm.runner.SMTestProxy
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo.Magnitude
import com.intellij.execution.testframework.{AbstractTestProxy, TestProxyRoot}

import scala.jdk.CollectionConverters.ListHasAsScala

private case class MyTestTreeNode(
  presentation: String,
  name: String,
  status: Magnitude,
  children: Seq[MyTestTreeNode]
)

private object MyTestTreeNode {

  private val DefaultStatus = Magnitude.COMPLETE_INDEX

  def apply(presentation: String, name: String): MyTestTreeNode =
    new MyTestTreeNode(presentation, name, DefaultStatus, Nil)

  def apply(presentation: String, name: String, status: Magnitude): MyTestTreeNode =
    new MyTestTreeNode(presentation, name, status, Nil)

  def apply(presentation: String, name: String, children: Seq[MyTestTreeNode]): MyTestTreeNode =
    new MyTestTreeNode(presentation, name, DefaultStatus, children)

  def apply(samePresentationAndName: String): MyTestTreeNode =
    new MyTestTreeNode(samePresentationAndName, samePresentationAndName, DefaultStatus, Nil)

  def apply(samePresentationAndName: String, status: Magnitude): MyTestTreeNode =
    new MyTestTreeNode(samePresentationAndName, samePresentationAndName, status, Nil)

  def apply(samePresentationAndName: String, status: Magnitude, children: Seq[MyTestTreeNode]): MyTestTreeNode =
    new MyTestTreeNode(samePresentationAndName, samePresentationAndName, status, children)

  def apply(samePresentationAndName: String, children: Seq[MyTestTreeNode]): MyTestTreeNode =
    new MyTestTreeNode(samePresentationAndName, samePresentationAndName, DefaultStatus, children)

  def fromTestProxy(node: AbstractTestProxy): MyTestTreeNode = {
    val presentation = node match {
      case x: TestProxyRoot => x.getPresentation
      case x: SMTestProxy => x.getPresentableName
      case _ => null
    }
    val magnitude = Magnitude.values()(node.getMagnitude)
    val children = node.getChildren.asScala.map(MyTestTreeNode.fromTestProxy).toSeq
    MyTestTreeNode(presentation, node.getName, magnitude, children)
  }
}