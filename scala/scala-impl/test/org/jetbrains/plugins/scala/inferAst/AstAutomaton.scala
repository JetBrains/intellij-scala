package org.jetbrains.plugins.scala.inferAst

import scala.collection.mutable

class AstAutomaton[A] {
  private var nextIdx = 0

  var starts: Set[Node] = Set.empty
  var exits: Set[Node] = Set.empty

  class Node(val action: A) {
    private var _to: Set[Node] = Set.empty
    private var _from: Set[Node] = Set.empty

    val idx: Int = {
      val idx = nextIdx
      nextIdx += 1
      idx
    }

    def isStart: Boolean = starts.contains(this)
    def isExit: Boolean = exits.contains(this)

    def markStart(): Unit = starts += this
    def markExit(): Unit = exits += this

    def to: Set[Node] = _to
    def from: Set[Node] = _from

    def ~> (to: Node): Node = {
      _to += to
      to._from += this
      to
    }

    def ~> (to: IterableOnce[Node]): Unit =
      to.iterator.foreach(~>)

    def tillHereAsAutomata: AstAutomaton[A] = {
      val result = new AstAutomaton[A]

      val mapping = result.copyFrom(nodesBetween(starts, Seq(this)))
      starts.flatMap(mapping.get).foreach(_.markStart())
      exits.flatMap(mapping.get).foreach(_.markExit())
      result
    }

    def remove(connectAdjacent: Boolean): Unit = {
      if (connectAdjacent) {
        _from.foreach(_._to ++= _to)
        _to.foreach(_._from ++= _from)
      }
      _to.foreach(_._from -= this)
      _from.foreach(_._to -= this)
      _to = Set.empty
      _from = Set.empty
      starts -= this
      exits -= this
    }

    override def toString: String = {
      val stringBuilder = new StringBuilder
      stringBuilder ++= s"Node($idx, $action"
      if (isStart) stringBuilder ++= ", start"
      if (isExit) stringBuilder ++= ", exit"
      stringBuilder ++= ")"
      stringBuilder.toString()
    }
  }

  override def clone(): AstAutomaton[A] = {
    val result = new AstAutomaton[A]
    val mapping = result.copyFrom(reachableNodes)
    starts.foreach(start => mapping(start).markStart())
    exits.foreach(exit => mapping(exit).markExit())
    result
  }

//  def isEmpty: Boolean = starts.forall(_.to.isEmpty)

  def removeAllInPlace(connectAdjacent: Boolean)(f: Node => Boolean): Unit = {
    reachableNodes.iterator.filter(f).foreach(_.remove(connectAdjacent))
  }

  def copyFrom(nodes: Iterable[AstAutomaton[A]#Node]): Map[AstAutomaton[A]#Node, Node] = {
    val mapping =
      nodes.map(node => node -> new this.Node(node.action)).toMap

    mapping.foreach { case (there, here) =>
      for (toThere <- there.to; toHere <- mapping.get(toThere)) {
        here ~> toHere
      }
    }
    mapping
  }

  def toGraphviz: String = {
    val processed = mutable.Set.empty[Node]
    val result = new StringBuilder
    result.append("digraph {\n")

    def printNode(node: Node): Unit =
      if (!processed.contains(node)) {
        processed += node

        val props = mutable.Buffer.empty[String]
        props += s"label=\"${node.action}\""
        if (node.isExit) {
          props += "shape=doubleoctagon"
        }

        result ++= s"  ${node.idx}[${props.mkString(", ")}]\n"

        for (to <- node.to)
          result ++= s"  ${node.idx} -> ${to.idx}\n"

        result += '\n'

        for (to <- node.to)
          printNode(to)
      }

    result.append("  start[label=\"Start\"]\n")
    for (start <- starts)
      result ++= s"  start -> ${start.idx}\n"
    starts.foreach(printNode)

    result.append("}\n")

    result.toString()
  }

  def foreachReachableForward()(f: Node => Boolean): Unit =
    foreachReachableForward(starts)(f)
  def foreachReachableForward(startNode: Node)(f: Node => Boolean): Unit =
    foreachReachableForward(List(startNode))(f)
  def foreachReachableForward(startNodes: Iterable[Node])(f: Node => Boolean): Unit = {
    val processed = mutable.Set.empty[Node]

    def process(node: Node): Unit =
      if (processed.add(node) && f(node)) {
        for (to <- node.to)
          process(to)
      }

    startNodes.foreach(process)
  }


  def foreachReachableBackward()(f: Node => Boolean): Unit =
    foreachReachableForward(starts)(f)
  def foreachReachableBackward(startNode: Node)(f: Node => Boolean): Unit =
    foreachReachableForward(List(startNode))(f)
  def foreachReachableBackward(startNodes: Iterable[Node])(f: Node => Boolean): Unit = {
    val processed = mutable.Set.empty[Node]

    def process(node: Node): Unit =
      if (processed.add(node) && f(node)) {
        for (from <- node.from)
          process(from)
      }

    startNodes.foreach(process)
  }

  def nodesBetween(start: Node, end: Node): Set[Node] =
    nodesBetween(List(start), List(end))

  def nodesBetween(starts: Iterable[Node], ends: Iterable[Node]): Set[Node] = {
    val result = Set.newBuilder[Node]

    val backReachable = mutable.Set.empty[Node]
    foreachReachableBackward(ends) { node =>
      backReachable.add(node)
      true
    }

    foreachReachableForward(starts) { node =>
      if (backReachable.contains(node)) {
        result += node
      }
      true
    }

    result.result()
  }

  def reachableNodes: Seq[Node] = {
    val result = Seq.newBuilder[Node]
    foreachReachableForward(){ node =>
      result += node
      true
    }
    result.result()
  }

  def merge(other: AstAutomaton[A]): Unit = {
    val processed = mutable.Map.empty[other.Node, this.Node]

    def process(node: other.Node): this.Node = {
      processed.get(node) match {
        case Some(result) => result
        case None =>
          val newNode = new Node(node.action)
          if (node.isStart) newNode.markStart()
          if (node.isExit) newNode.markExit()

          processed += node -> newNode

          for (to <- node.to)
            newNode ~> process(to)

          newNode
      }
    }

    other.starts.foreach(process)
  }

  def minimized: AstAutomaton[A] =
    backwardMinimized.forwardMinimized

  def forwardMinimized: AstAutomaton[A] = {
    val result = new AstAutomaton[A]
    val processed = mutable.Map.empty[Set[Node], result.Node]

    def process(nodes: Set[Node]): Seq[result.Node] = {
      nodes
        .groupBy(_.action)
        .iterator
        .flatMap {
          case (action, sameNodes) =>
            processed.get(sameNodes) match {
              case Some(found) => Seq(found)
              case None =>
                val newNode = new result.Node(action)
                if (sameNodes.exists(_.isExit)) newNode.markExit()
                processed += nodes -> newNode

                newNode ~> process(sameNodes.flatMap(_.to))
                Seq(newNode)
            }
        }
        .toSeq
    }

    process(starts).foreach(_.markStart())

    result
  }


  def backwardMinimized: AstAutomaton[A] = {
    val result = new AstAutomaton[A]
    val processed = mutable.Map.empty[Set[Node], result.Node]

    def process(nodes: Set[Node]): Seq[result.Node] = {
      nodes
        .groupBy(_.action)
        .iterator
        .flatMap {
          case (action, sameNodes) =>
            processed.get(sameNodes) match {
              case Some(found) => Seq(found)
              case None =>
                val newNode = new result.Node(action)
                if (sameNodes.exists(_.isStart)) newNode.markStart()
                processed += nodes -> newNode

                process(sameNodes.flatMap(_.from)).foreach(_ ~> newNode)
                Seq(newNode)
            }
        }
        .toSeq
    }

    process(exits).foreach(_.markExit())

    result
  }
}

object AstAutomaton {
  def empty[A]: AstAutomaton[A] = new AstAutomaton

  def squash[A](automatas: IterableOnce[AstAutomaton[A]]): AstAutomaton[A] =
    automatas.iterator.foldLeft(new AstAutomaton[A]) { (acc, automata) => acc.merge(automata); acc }

}