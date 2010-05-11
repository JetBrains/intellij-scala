package org.jetbrains.plugins.scala.lang.psi.iterator

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 09.05.2010
 */

/**
 * Depth-first state machine tree-traversal iterator implementation, 
 * faster than stack-based implementation, up to 2x faster than ScalaRecursiveElementVisitor.
 */
class DepthFirstIterator(element: PsiElement) extends Iterator[PsiElement] {
  var current = element
  var continuations = List[PsiElement]()

  def hasNext = current != null

  def next() = {
    val result = current
    current = nextFor(current)
    result
  }

  def nextFor(e: PsiElement): PsiElement = {
    if (e.eq(element)) return e.getFirstChild

    val child = e.getFirstChild
    val sibling = e.getNextSibling

    if (child != null) {
      if (sibling != null) {
        continuations = sibling :: continuations
      }
      return child
    }

    if (sibling != null) return sibling

    if (continuations.isEmpty) return null

    val result = continuations.head
    continuations = continuations.tail
    result
  }
}