package org.jetbrains.plugins.scala.lang.psi

import api.ScalaFile
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
  
  def parents: Iterator[PsiElement] = new ParentsIterator(delegate)

  def containingFile: Option[PsiFile] = {
    val f = delegate.getContainingFile
    if (f == null) None else Some(f)
  }
  
  def parentsInFile: Iterator[PsiElement] = 
    new ParentsIterator(delegate).takeWhile(!_.isInstanceOf[PsiFile])
  
  def contexts: Iterator[PsiElement] = new ContextsIterator(delegate)

  def prevSibling: Option[PsiElement] = {
    val sibling = delegate.getPrevSibling
    if (sibling == null) None else Some(sibling)
  }

  def nextSibling: Option[PsiElement] = {
    val sibling = delegate.getNextSibling
    if (sibling == null) None else Some(sibling)
  }

  def prevSiblings: Iterator[PsiElement] = new PrevSiblignsIterator(delegate)

  def nextSiblings: Iterator[PsiElement] = new NextSiblignsIterator(delegate)

  def children: Iterator[PsiElement] = new ChildrenIterator(delegate)

  def isAncestorOf(e: PsiElement) = PsiTreeUtil.isAncestor(delegate, e, true)

  def depthFirst: Iterator[PsiElement] = depthFirst(DefaultPredicate)
  
  def depthFirst(predicate: PsiElement => Boolean): Iterator[PsiElement] =
    new DepthFirstIterator(delegate, predicate)

  def breadthFirst: Iterator[PsiElement] = depthFirst(DefaultPredicate)
  
  def breadthFirst(predicate: PsiElement => Boolean): Iterator[PsiElement] = 
    new BreadthFirstIterator(delegate, predicate)

  def isScope: Boolean = ScalaPsiUtil.isScope(delegate)

  def scopes: Iterator[PsiElement] = contexts.filter(ScalaPsiUtil.isScope(_))

  def containingScalaFile: Option[ScalaFile] = delegate.getContainingFile match {
    case sf: ScalaFile => Some(sf)
    case _ => None
  }
}
