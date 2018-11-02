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

/**
  * @author ilyas
  */
object DummyASTNode extends TreeElement(new ScalaTokenType("Dummy Element")) {

  def getText: String = null

  def removeRange(firstNodeToRemove: ASTNode, firstNodeToKeep: ASTNode): Unit = {}

  def replaceChild(oldChild: ASTNode, newChild: ASTNode): Unit = {}

  override def toString: String = "Dummy AST node"

  def addChild(child: ASTNode): Unit = {}

  def textContains(c: Char): Boolean = false

  def replaceAllChildrenToChildrenOf(anotherParent: ASTNode): Unit = {}

  def addChild(child: ASTNode, anchorBefore: ASTNode): Unit = {}

  def getTextLength: Int = 42

  def getChildren(filter: TokenSet): Array[ASTNode] = Array[ASTNode]()

  def addLeaf(leafType: IElementType, leafText: CharSequence, anchorBefore: ASTNode): Unit = {}

  def removeChild(child: ASTNode): Unit = {}

  def addChildren(firstChild: ASTNode, firstChildToNotAdd: ASTNode, anchorBefore: ASTNode): Unit = {}

  def findChildByType(typesSet: TokenSet): ASTNode = null

  def findChildByType(elem: IElementType): ASTNode = null

  def findChildByType(elem: IElementType, anchor: ASTNode): ASTNode = null

  def findChildByType(typesSet: TokenSet, anchor: ASTNode): ASTNode = null

  def findLeafElementAt(offset: Int): LeafElement = null

  def textToCharArray = new Array[Char](42)

  def getLastChildNode: TreeElement = null

  def getFirstChildNode: TreeElement = null

  def hc: Int = 42

  def getPsi: PsiElement = null

  def acceptTree(visitor: TreeElementVisitor): Unit = {}

  def getCachedLength: Int = 42

  def textMatches(buffer: CharSequence, start: Int): Int = -1

  def getNotCachedLength: Int = 42

  def getChars: CharSequence = textToCharArray.mkString

  def getPsi[T <: PsiElement](p1: Class[T]): T = null.asInstanceOf[T]
}