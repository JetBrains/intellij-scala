package org.jetbrains.plugins.scala.tasty.reader

import dotty.tools.tasty.TastyBuffer.Addr
import dotty.tools.tasty.TastyFormat

class Node(val addr: Addr, val tag: Int, val names: Seq[String], children0: () => Seq[Node], val position: () => Option[Int]) {
  lazy val children: Seq[Node] = children0()

  def firstChild: Node = children.head

  override def toString: String = toString(0)

  protected def toString(indent: Int): String =
    Iterator.fill(indent)(' ').mkString + TastyFormat.astTagToString(tag) + " " + names.mkString +
      children.map("\n" + _.toString(indent + 2)).mkString

  def name: String = names.head

  def contains(modifierTag: Int): Boolean = modifierTags.contains(modifierTag)

  lazy val modifierTags: Set[Int] = children.reverseIterator.map(_.tag).takeWhile(TastyFormat.isModifierTag).toSet

  def isModifier: Boolean = TastyFormat.isModifierTag(tag)

  def isTypeTree: Boolean = TastyFormat.isTypeTreeTag(tag)

  def is(tags: Int*): Boolean = tags.contains(tag)

  // TODO use parameters in TreePrinter instead
  // TODO private setter
  var prevSibling: Option[Node] = None

  val nextSiblings: Iterator[Node] = Iterator.unfold(this)(_.nextSibling.map(x => (x, x)))

  // TODO can we use only previousSibling?
  var nextSibling: Option[Node] = None

  val prevSiblings: Iterator[Node] = Iterator.unfold(this)(_.prevSibling.map(x => (x, x)))

  var refTag: Option[Int] = None

  var refName: Option[String] = None

  var refPrivate: Boolean = false

  var value: Long = -1L

  var isSharedType: Boolean = false
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
}