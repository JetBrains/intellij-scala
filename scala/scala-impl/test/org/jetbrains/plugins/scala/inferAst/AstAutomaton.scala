package org.jetbrains.plugins.scala.inferAst

import org.jetbrains.plugins.scala.inferAst.AstAutomaton.StartExitActionProvider

import scala.collection.mutable

class AstAutomaton[A: StartExitActionProvider] {
  private var nextIdx = 0

  val start: Node = new Node(implicitly[StartExitActionProvider[A]].start)
  val exit: Node = new Node(implicitly[StartExitActionProvider[A]].exit)

  class Node(val action: A) {
    private var _to: Set[Node] = Set.empty
    private var _from: Set[Node] = Set.empty

    val idx: Int = {
      val idx = nextIdx
      nextIdx += 1
      idx
    }

    def to: Set[Node] = _to
    def from: Set[Node] = _from

    def ~> (to: Node): Node = {
      _to += to
      to._from += this
      to
    }

    def ~> (to: IterableOnce[Node]): Unit =
      to.iterator.foreach(~>)
  }

  def toGraphviz: String = {
    val processed = mutable.Set.empty[Node]
    val result = new StringBuilder
    result.append("digraph {\n")

    def printNode(node: Node): Unit =
      if (!processed.contains(node)) {
        processed += node

        result ++= s"  ${node.idx}[label=\"${node.action}\"]\n"

        for (to <- node.to)
          result ++= s"  ${node.idx} -> ${to.idx}\n"

        result += '\n'

        for (to <- node.to)
          printNode(to)
      }

    printNode(start)

    result.append("}\n")

    result.toString()
  }

  def merge(other: AstAutomaton[A]): Unit = {
    val processed = mutable.Map.empty[other.Node, this.Node]

    def process(node: other.Node): this.Node = {
      processed.get(node) match {
        case Some(result) => result
        case None =>
          val newNode = {
            if (node == other.start) start
            else if (node == other.exit) exit
            else new Node(node.action)
          }
          processed += node -> newNode

          for (to <- node.to)
            newNode ~> process(to)

          newNode
      }
    }

    process(other.start)
  }

  def forwardMinimized(): AstAutomaton[A] = {
    val startAction = implicitly[StartExitActionProvider[A]].start
    val exitAction = implicitly[StartExitActionProvider[A]].exit

    val result = new AstAutomaton
    val processed = mutable.Map.empty[Set[Node], result.Node]

    def process(nodes: Set[Node]): result.Node = {
      val action = nodes.head.action
      assert(nodes.forall(_.action == action))
      processed.get(nodes) match {
        case Some(found) => found
        case None =>
          val newNode =
            action match {
              case `startAction` => result.start
              case `exitAction` => result.exit
              case _ => new result.Node(action)
            }
          processed += nodes -> newNode

          (nodes.flatMap(_.to)).groupBy(_.action).foreach { case (_, nodes) =>
            newNode ~> process(nodes)
          }

          newNode
      }
    }

    process(Set(start))

    result
  }

  def backwardMinimized(): AstAutomaton[A] = {
    val startAction = implicitly[StartExitActionProvider[A]].start
    val exitAction = implicitly[StartExitActionProvider[A]].exit

    val result = new AstAutomaton
    val processed = mutable.Map.empty[Set[Node], result.Node]

    def process(nodes: Set[Node]): result.Node = {
      val action = nodes.head.action
      assert(nodes.forall(_.action == action))
      processed.get(nodes) match {
        case Some(found) => found
        case None =>
          val newNode =
            action match {
              case `startAction` => result.start
              case `exitAction` => result.exit
              case _ => new result.Node(action)
            }
          processed += nodes -> newNode

          nodes.flatMap(_.from).groupBy(_.action).foreach { case (_, nodes) =>
            process(nodes) ~> newNode
          }

          newNode
      }
    }

    process(Set(exit))

    result
  }
}

object AstAutomaton {
  trait StartExitActionProvider[A] {
    def start: A
    def exit: A
  }
}