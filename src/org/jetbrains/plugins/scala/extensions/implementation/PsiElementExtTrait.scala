package org.jetbrains.plugins.scala.extensions.implementation

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.implementation.iterator._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

/**
 * Pavel Fatin
 */

trait PsiElementExtTrait extends Any {
  protected def repr: PsiElement

  def text: String = repr.getText
  def firstChild: Option[PsiElement] = Option(repr.getFirstChild)
  def lastChild: Option[PsiElement] = Option(repr.getLastChild)
  def elementAt(offset: Int): Option[PsiElement] = Option(repr.findElementAt(offset))
  def referenceAt(offset: Int): Option[PsiReference] = Option(repr.findReferenceAt(offset))
  def parent: Option[PsiElement] = Option(repr.getParent)
  def parents: Iterator[PsiElement] = new ParentsIterator(repr)
  def withParents: Iterator[PsiElement] = new ParentsIterator(repr, strict = false)

  def containingFile: Option[PsiFile] = Option(repr.getContainingFile)

  def containingVirtualFile: Option[VirtualFile] = containingFile.flatMap { file =>
    Option(file.getVirtualFile)
  }

  def containingScalaFile: Option[ScalaFile] = containingFile.collect {
    case file: ScalaFile => file
  }

  def parentsInFile: Iterator[PsiElement] = {
    repr match {
      case _: PsiFile | _: PsiDirectory => Iterator.empty
      case _ => parents.takeWhile(!_.isInstanceOf[PsiFile])
    }
  }

  def withParentsInFile: Iterator[PsiElement] = Iterator(repr) ++ parentsInFile

  def contexts: Iterator[PsiElement] = new ContextsIterator(repr)

  def withContexts: Iterator[PsiElement] = new ContextsIterator(repr, strict = false)

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

  def getPrevSiblingCondition(condition: PsiElement => Boolean, strict: Boolean = true): Option[PsiElement] = {
    if (!strict && condition(repr)) return Some(repr)
    var prev: PsiElement = PsiTreeUtil.prevLeaf(repr)
    while (prev != null && !condition(prev)) {
      prev = prev.getPrevSibling
    }
    Option(prev)
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
  def prevSibling: Option[PsiElement] = Option(repr.getPrevSibling)

  def nextSibling: Option[PsiElement] = Option(repr.getNextSibling)

  def prevSiblings: Iterator[PsiElement] = new PrevSiblignsIterator(repr)

  def nextSiblings: Iterator[PsiElement] = new NextSiblignsIterator(repr)

  // Element + Prev. siblings
  def prevElements: Iterator[PsiElement] = new PrevElementsIterator(repr)

  // Element + Next siblings
  def nextElements: Iterator[PsiElement] = new NextElementsIterator(repr)

  def children: Iterator[PsiElement] = new ChildrenIterator(repr)

  def isAncestorOf(e: PsiElement): Boolean = PsiTreeUtil.isAncestor(repr, e, true)

  def depthFirst: Iterator[PsiElement] = depthFirst(DefaultPredicate)

  def depthFirst(predicate: PsiElement => Boolean): Iterator[PsiElement] =
    new DepthFirstIterator(repr, predicate)

  def breadthFirst: Iterator[PsiElement] = breadthFirst(DefaultPredicate)

  def breadthFirst(predicate: PsiElement => Boolean): Iterator[PsiElement] =
    new BreadthFirstIterator(repr, predicate)

  def scopes: Iterator[PsiElement] = contexts.filter(ScalaPsiUtil.isScope)
}
