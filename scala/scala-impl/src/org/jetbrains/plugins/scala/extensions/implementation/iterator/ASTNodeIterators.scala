package org.jetbrains.plugins.scala.extensions.implementation.iterator

import com.intellij.lang.ASTNode

class ASTNodeTreeNextIterator(node: ASTNode) extends Iterator[ASTNode] {
  private var current: ASTNode = if (node == null) null else node.getTreeNext

  override def hasNext: Boolean = current != null

  override def next(): ASTNode = {
    val result = current
    current = current.getTreeNext
    result
  }
}

class ASTNodeTreePrevIterator(node: ASTNode) extends Iterator[ASTNode] {
  private var current: ASTNode = if (node == null) null else node.getTreePrev

  override def hasNext: Boolean = current != null

  override def next(): ASTNode = {
    val result = current
    current = current.getTreePrev
    result
  }
}