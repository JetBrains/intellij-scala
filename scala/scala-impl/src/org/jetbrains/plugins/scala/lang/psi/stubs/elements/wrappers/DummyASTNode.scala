package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package wrappers

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.{LeafElement, TreeElement, TreeElementVisitor}
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType

object DummyASTNode extends TreeElement(new ScalaTokenType("Dummy Element")) {

  override def getText: String = null

  override def removeRange(firstNodeToRemove: ASTNode, firstNodeToKeep: ASTNode): Unit = {}

  override def replaceChild(oldChild: ASTNode, newChild: ASTNode): Unit = {}

  override def toString: String = "Dummy AST node"

  override def addChild(child: ASTNode): Unit = {}

  override def textContains(c: Char): Boolean = false

  override def replaceAllChildrenToChildrenOf(anotherParent: ASTNode): Unit = {}

  override def addChild(child: ASTNode, anchorBefore: ASTNode): Unit = {}

  override def getTextLength: Int = 42

  override def getChildren(filter: TokenSet): Array[ASTNode] = Array[ASTNode]()

  override def addLeaf(leafType: IElementType, leafText: CharSequence, anchorBefore: ASTNode): Unit = {}

  override def removeChild(child: ASTNode): Unit = {}

  override def addChildren(firstChild: ASTNode, firstChildToNotAdd: ASTNode, anchorBefore: ASTNode): Unit = {}

  override def findChildByType(typesSet: TokenSet): ASTNode = null

  override def findChildByType(elem: IElementType): ASTNode = null

  override def findChildByType(elem: IElementType, anchor: ASTNode): ASTNode = null

  override def findChildByType(typesSet: TokenSet, anchor: ASTNode): ASTNode = null

  override def findLeafElementAt(offset: Int): LeafElement = null

  override def textToCharArray = new Array[Char](42)

  override def getLastChildNode: TreeElement = null

  override def getFirstChildNode: TreeElement = null

  override def hc: Int = 42

  override def getPsi: PsiElement = null

  override def acceptTree(visitor: TreeElementVisitor): Unit = {}

  override def getCachedLength: Int = 42

  override def textMatches(buffer: CharSequence, start: Int): Int = -1

  def getNotCachedLength: Int = 42

  override def getChars: CharSequence = textToCharArray.mkString

  override def getPsi[T <: PsiElement](p1: Class[T]): T = null.asInstanceOf[T]
}