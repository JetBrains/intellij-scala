package org.jetbrains.plugins.scala.extensions.implementation

import iterator._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.lang.ASTNode
import com.intellij.psi._

/**
 * Pavel Fatin
 */

trait PsiElementExt {
  protected def repr: PsiElement

  def firstChild: Option[PsiElement] = {
    val child = repr.getFirstChild
    if (child == null) None else Some(child)
  }

  def lastChild: Option[PsiElement] = {
    val child = repr.getLastChild
    if (child == null) None else Some(child)
  }

  def elementAt(offset: Int): Option[PsiElement] = {
    val e = repr.findElementAt(offset)
    if (e == null) None else Some(e)
  }

  def referenceAt(offset: Int): Option[PsiReference] = {
    val e = repr.findReferenceAt(offset)
    if (e == null) None else Some(e)
  }

  def parent: Option[PsiElement] = {
    val p = repr.getParent
    if (p == null) None else Some(p)
  }

  def parents: Iterator[PsiElement] = new ParentsIterator(repr)

  def containingFile: Option[PsiFile] = {
    val f = repr.getContainingFile
    if (f == null) None else Some(f)
  }

  def parentsInFile: Iterator[PsiElement] =
    new ParentsIterator(repr).takeWhile(!_.isInstanceOf[PsiFile])

  def contexts: Iterator[PsiElement] = new ContextsIterator(repr)

  def getPrevSiblingNotWhitespace: PsiElement = {
    var prev: PsiElement = repr.getPrevSibling
    while (prev != null && (prev.isInstanceOf[PsiWhiteSpace] ||
            prev.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)) prev = prev.getPrevSibling
    prev
  }

  def getPrevSiblingNotWhitespaceComment: PsiElement = {
    var prev: PsiElement = repr.getPrevSibling
    while (prev != null && (prev.isInstanceOf[PsiWhiteSpace] ||
            prev.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE || prev.isInstanceOf[PsiComment]))
      prev = prev.getPrevSibling
    prev
  }

  def getNextSiblingNotWhitespace: PsiElement = {
    var next: PsiElement = repr.getNextSibling
    while (next != null && (next.isInstanceOf[PsiWhiteSpace] ||
            next.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)) next = next.getNextSibling
    next
  }

  def getNextSiblingNotWhitespaceComment: PsiElement = {
    var next: PsiElement = repr.getNextSibling
    while (next != null && (next.isInstanceOf[PsiWhiteSpace] ||
            next.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE || next.isInstanceOf[PsiComment]))
      next = next.getNextSibling
    next
  }

  def prevSibling: Option[PsiElement] = {
    val sibling = repr.getPrevSibling
    if (sibling == null) None else Some(sibling)
  }

  def nextSibling: Option[PsiElement] = {
    val sibling = repr.getNextSibling
    if (sibling == null) None else Some(sibling)
  }

  def prevSiblings: Iterator[PsiElement] = new PrevSiblignsIterator(repr)

  def nextSiblings: Iterator[PsiElement] = new NextSiblignsIterator(repr)

  // Element + Prev. siblings
  def prevElements: Iterator[PsiElement] = new PrevElementsIterator(repr)

  // Element + Next siblings
  def nextElements: Iterator[PsiElement] = new NextElementsIterator(repr)

  def children: Iterator[PsiElement] = new ChildrenIterator(repr)

  def isAncestorOf(e: PsiElement) = PsiTreeUtil.isAncestor(repr, e, true)

  def depthFirst: Iterator[PsiElement] = depthFirst(DefaultPredicate)

  def depthFirst(predicate: PsiElement => Boolean): Iterator[PsiElement] =
    new DepthFirstIterator(repr, predicate)

  def breadthFirst: Iterator[PsiElement] = depthFirst(DefaultPredicate)

  def breadthFirst(predicate: PsiElement => Boolean): Iterator[PsiElement] =
    new BreadthFirstIterator(repr, predicate)

  def isScope: Boolean = ScalaPsiUtil.isScope(repr)

  def scopes: Iterator[PsiElement] = contexts.filter(ScalaPsiUtil.isScope(_))

  def containingScalaFile: Option[ScalaFile] = repr.getContainingFile match {
    case sf: ScalaFile => Some(sf)
    case _ => None
  }
}
