package org.jetbrains.plugins.scala.tasty

import dotty.tools.tasty.TastyFormat

// TODO custom extractors
case class Node(tag: Int, names: Seq[String], children: Seq[Node]) {
  override def toString: String = toString(0)

  protected def toString(indent: Int): String =
    Iterator.fill(indent)(' ').mkString + TastyFormat.astTagToString(tag) + " " + names.mkString +
      children.map("\n" + _.toString(indent + 2)).mkString

  def name: String = names.head

  def hasFlag(flag: Int): Boolean = children.exists(_.tag == flag) // TODO optimize

  // TODO flags

  def isModifier: Boolean = TastyFormat.isModifierTag(tag)

  def isTypeTree: Boolean = TastyFormat.isTypeTreeTag(tag)

  def is(tags: Int*): Boolean = tags.contains(tag)
}
