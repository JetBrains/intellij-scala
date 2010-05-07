package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiReference, PsiFile, PsiElement}
import Stream._

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

  def parents: Stream[PsiElement] = RichPsiElement.parentsOf(delegate)
  
  def parentsInFile: Stream[PsiElement] = RichPsiElement.parentsInFileOf(delegate)

  def prevSibling: Option[PsiElement] = {
    val sibling = delegate.getPrevSibling
    if (sibling == null) None else Some(sibling)
  }

  def nextSibling: Option[PsiElement] = {
    val sibling = delegate.getNextSibling
    if (sibling == null) None else Some(sibling)
  }

  def prevSiblings: Stream[PsiElement] = RichPsiElement.prevSiblingsOf(delegate)

  def nextSiblings: Stream[PsiElement] = RichPsiElement.nextSiblingsOf(delegate)

  def children: Stream[PsiElement] = RichPsiElement.childrenOf(delegate)

  def parentOfType[T](aClass: Class[T]): Option[T] =
    RichPsiElement.findByType(aClass, RichPsiElement.parentsInFileOf(delegate))

  def childOfType[T](aClass: Class[T]): Option[T] =
    RichPsiElement.findByType(aClass, children)

  def prevSiblingOfType[T](aClass: Class[T]): Option[T] =
    RichPsiElement.findByType(aClass, prevSiblings)

  def nextSiblingOfType[T](aClass: Class[T]): Option[T] =
    RichPsiElement.findByType(aClass, nextSiblings)

  def isAncestorOf(e: PsiElement) = PsiTreeUtil.isAncestor(delegate, e, true)
  
  def elements: Stream[PsiElement] = RichPsiElement.elementsOf(delegate)
}

object RichPsiElement {
  def findByType[T](aClass: Class[T], es: Stream[PsiElement]): Option[T] =
    es.find(aClass.isInstance(_)).map(_.asInstanceOf[T])

  def parentsInFileOf(e: PsiElement): Stream[PsiElement] = parentsOf(e).takeWhile(!_.isInstanceOf[PsiFile]) 
  
  def parentsOf(e: PsiElement): Stream[PsiElement] = {
    val p = e.getParent
    if (p == null) Empty else p #:: parentsOf(p)
  }

  def prevSiblingsOf(e: PsiElement): Stream[PsiElement] = {
    val sibling = e.getPrevSibling
    if (sibling == null) Empty else sibling #:: prevSiblingsOf(sibling)
  }

  def nextSiblingsOf(e: PsiElement): Stream[PsiElement] = {
    val sibling = e.getNextSibling
    if (sibling == null) Empty else sibling #:: nextSiblingsOf(sibling)
  }
  
  def childrenOf(e: PsiElement): Stream[PsiElement] = {
    val child = e.getFirstChild
    if (child == null) Empty else child #:: RichPsiElement.nextSiblingsOf(child)
  }
  
  def elementsOf(e: PsiElement): Stream[PsiElement] = e #:: childrenOf(e).flatMap(elementsOf _)
}