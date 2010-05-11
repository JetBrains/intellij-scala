package org.jetbrains.plugins.scala.lang.psi

import com.intellij.psi.PsiElement

/**
 * Pavel.Fatin, 11.05.2010
 */

class PsiElementMock(val name: String, children: PsiElementMock*) extends AbstractPsiElementMock {
  private var parent: PsiElement = _
  private var prevSibling: PsiElement = _
  private var nextSibling: PsiElement = _
  private var firstChild: PsiElement = children.firstOption.getOrElse(null)
  private var lastChild: PsiElement = children.lastOption.getOrElse(null)
  
  
  for(child <- children) { child.parent = this }
  
  if(!children.isEmpty) {
    for((a, b) <- children.zip(children.tail)) {
      a.nextSibling = b
      b.prevSibling = a
    }
  }

  override def getParent = parent

  override def getPrevSibling = prevSibling

  override def getNextSibling = nextSibling

  override def getChildren = children.toArray

  override def getFirstChild = firstChild

  override def getLastChild = lastChild
  
  override def toString = name
  
  def toText = if(children.isEmpty) name else name + "(" + children.mkString(", ") + ")"
}

object PsiElementMock {
  def apply(name: String, children: PsiElementMock*) = new PsiElementMock(name, children: _*)
}