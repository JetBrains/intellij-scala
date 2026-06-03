package org.jetbrains.plugins.scala.tasty.reader

import dotty.tools.tasty.TastyFormat

/**
 * Prints [[Node]] as a tree in text format, like this: {{{
 *   PACKAGE(151)
 *     TERMREFpkg 1 [<empty>]
 *     TYPEDEF(146) 2 [MyClass]
 *       TEMPLATE(123)
 *       ...
 * }}}
 *
 *
 * @param printAddress Example: {{{
 *                       0: PACKAGE(151)
 *                       3:   TERMREFpkg 1 [<empty>]
 *                       5:   TYPEDEF(146) 2 [MyClass]
 *                       ...
 *                     }}}
 *
 * @note It might happen that some sub-nodes are repeated in different places with different level of indentation.
 *       This is because our Tasty reader inlines "shared nodes" (it's just how we need it for our internal needs)
 */
class NodePrinter(
  printAddress: Boolean = false,
) {

  def print(node: Node): String =
    renderInner(node, indentSize = 0)

  private def renderInner(node: Node, indentSize: Int): String = {
    val indentStr = Iterator.fill(indentSize)(' ').mkString

    // By default, the compiler reserves 6 characters for the address when printing "Tasty" with a -Yprint-tasty compiler flag.
    // We do the same just in order the trees are similar, to compare them
    val addrText = if (printAddress) f"${node.addr.index}%6s: " else ""
    val tagText = TastyFormat.astTagToString(node.tag)
    val namesText = if (node.names.nonEmpty) " " + node.names.mkString(", ") else ""
    val refNameText = node.refName.fold("")(n => s" ($n)")

    val selfText = addrText + indentStr + tagText + namesText + refNameText
    val childrenText = node.children.map(child => "\n" + renderInner(child, indentSize + 2)).mkString

    selfText + childrenText
  }
}
