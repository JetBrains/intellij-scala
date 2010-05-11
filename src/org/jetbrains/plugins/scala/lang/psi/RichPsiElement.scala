package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiReference, PsiFile, PsiElement}
import iterator._

/**
 * Pavel.Fatin, 28.04.2010
 */

trait RichPsiElement {
  protected def delegate: PsiElement

  def firstChild: Option[PsiElement] = {
    val child = delegate.getFirstChild
    if (child == null) None else Some(child)
  }

  def lastChild: Option[PsiElement] = {
    val child = delegate.getLastChild
    if (child == null) None else Some(child)
  }

  def elementAt(offset: Int): Option[PsiElement] = {
    val e = delegate.findElementAt(offset)
    if (e == null) None else Some(e)
  }

  def referenceAt(offset: Int): Option[PsiReference] = {
    val e = delegate.findReferenceAt(offset)
    if (e == null) None else Some(e)
  }

  def parent: Option[PsiElement] = {
    val p = delegate.getParent
    if (p == null) None else Some(p)
  }

  def parents: Iterator[PsiElement] = RichPsiElement.parentsOf(delegate)

  def parentsInFile: Iterator[PsiElement] = RichPsiElement.parentsInFileOf(delegate)
  
  def contexts: Iterator[PsiElement] = new ContextsIterator(delegate)

  def prevSibling: Option[PsiElement] = {
    val sibling = delegate.getPrevSibling
    if (sibling == null) None else Some(sibling)
  }

  def nextSibling: Option[PsiElement] = {
    val sibling = delegate.getNextSibling
    if (sibling == null) None else Some(sibling)
  }

  def prevSiblings: Iterator[PsiElement] = new NextSiblignsIterator(delegate)

  def nextSiblings: Iterator[PsiElement] = new PrevSiblignsIterator(delegate)

  def children: Iterator[PsiElement] = new ChildrenIterator(delegate)

  def parentOfType[T](aClass: Class[T]): Option[T] =
    RichPsiElement.findByType(aClass, RichPsiElement.parentsInFileOf(delegate))

  def childOfType[T](aClass: Class[T]): Option[T] =
    RichPsiElement.findByType(aClass, children)

  def prevSiblingOfType[T](aClass: Class[T]): Option[T] =
    RichPsiElement.findByType(aClass, prevSiblings)

  def nextSiblingOfType[T](aClass: Class[T]): Option[T] =
    RichPsiElement.findByType(aClass, nextSiblings)

  def isAncestorOf(e: PsiElement) = PsiTreeUtil.isAncestor(delegate, e, true)

  def depthFirst: Iterator[PsiElement] = new DepthFirstIterator(delegate)

  def breadthFirst: Iterator[PsiElement] = new BreadthFirstIterator(delegate)
}

object RichPsiElement {
  def findByType[T](aClass: Class[T], es: Iterator[PsiElement]): Option[T] =
    es.find(aClass.isInstance(_)).map(_.asInstanceOf[T])

  def parentsInFileOf(e: PsiElement): Iterator[PsiElement] = parentsOf(e).takeWhile(!_.isInstanceOf[PsiFile])

  def parentsOf(e: PsiElement): Iterator[PsiElement] = new ParentsIterator(e)
}