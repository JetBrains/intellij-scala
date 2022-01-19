package org.jetbrains.plugins.scala.tasty

import dotty.tools.tasty.TastyFormat

class Node(val tag: Int, val names: Seq[String], children0: () => Seq[Node]) {
  lazy val children: Seq[Node] = children0()

  override def toString: String = toString(0)

  protected def toString(indent: Int): String =
    Iterator.fill(indent)(' ').mkString + TastyFormat.astTagToString(tag) + " " + names.mkString +
      children.map("\n" + _.toString(indent + 2)).mkString

  def name: String = names.head

  def hasFlag(flag: Int): Boolean = flags.contains(flag) // TODO hasModifierTag

  lazy val flags: Set[Int] = children.reverseIterator.map(_.tag).takeWhile(TastyFormat.isModifierTag).toSet // TODO modifierTags

  def isModifier: Boolean = TastyFormat.isModifierTag(tag)

  def isTypeTree: Boolean = TastyFormat.isTypeTreeTag(tag)

  def is(tags: Int*): Boolean = tags.contains(tag)

  // TODO use parameters in TreePrinter instead
  // TODO private setter
  var previousSibling: Option[Node] = None

  val nextSiblings: Iterator[Node] = Iterator.unfold(this)(_.nextSibling.map(x => (x, x)))

  // TODO can we use only previousSibling?
  var nextSibling: Option[Node] = None

  val prevSiblings: Iterator[Node] = Iterator.unfold(this)(_.previousSibling.map(x => (x, x)))

  def nodes: Iterator[Node] = new Node.BreadthFirstIterator(this)

  // TODO
  // var parent: Option[Node] = None

  var refName: Option[String] = None

  var value: Long = -1L
}

private object Node {

  // TODO use Product matches

  object Node1 {
    def unapply(node: Node): Option[Int] = Some(node.tag)
  }

  object Node2 {
    def unapply(node: Node): Option[(Int, Seq[String])] = Some((node.tag, node.names))
  }

  object Node3 {
    def unapply(node: Node): Option[(Int, Seq[String], Seq[Node])] = Some((node.tag, node.names, node.children))
  }

  object && {
    def unapply[T](obj: T): Some[(T, T)] = Some((obj, obj))
  }

  // TODO Remove when SourceFile annotation reading is integrated
  import scala.collection.mutable
  class BreadthFirstIterator(element: Node) extends Iterator[Node] {
    private val queue: mutable.Queue[Node] =
      if (element != null) mutable.Queue(element)
      else mutable.Queue.empty

    override def hasNext: Boolean = queue.nonEmpty

    override def next(): Node = {
      val element = queue.dequeue()
      pushChildren(element)
      element
    }

    private def pushChildren(element: Node): Unit = {
      var child = element.children.headOption
      while (child.nonEmpty) {
        queue.enqueue(child.get)
        child = child.get.nextSibling
      }
    }
  }
}