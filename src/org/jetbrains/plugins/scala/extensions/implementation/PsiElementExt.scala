package org.jetbrains.plugins.scala.extensions.implementation

import iterator._
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiWhiteSpace, PsiFile, PsiReference, PsiElement}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes

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

  def getNextSiblingNotWhitespace: PsiElement = {
    var next: PsiElement = repr.getNextSibling
    while (next != null && (next.isInstanceOf[PsiWhiteSpace] ||
            next.getNode.getElementType == ScalaTokenTypes.tWHITE_SPACE_IN_LINE)) next = next.getNextSibling
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
  
  def wrapChildrenIn(container: PsiElement): PsiElement = {
    val elements = children.toList
    repr.deleteChildRange(repr.getFirstChild, repr.getLastChild)
    val wrapper = repr.add(container)
    if (elements.nonEmpty) wrapper.addRange(elements.head, elements.last)
    wrapper
  }

  def unwrapChildren(): Seq[PsiElement] = {
    val elements = children.toList
    val p = repr.getParent
    val newChildren = if (children.nonEmpty) {
      val first: PsiElement = p.addRangeAfter(elements.head, elements.last, repr)
      first :: new NextSiblignsIterator(first).take(elements.size).toList
    } else {
      Nil
    }
    repr.delete()
    newChildren
  }
  
  def deleteChildren(children: Seq[PsiElement]) {
    if (children.nonEmpty) repr.deleteChildRange(children.head, children.last)
  }
}
