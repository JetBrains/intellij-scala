package org.jetbrains.plugins.scala
package lang
package psi
package stubs
package elements
package wrappers
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.{TreeElement, TreeElementVisitor}
import com.intellij.psi.tree.{IElementType, TokenSet}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

/**
 * @author ilyas
 */

object DummyASTNode extends TreeElement(ScalaElementTypes.DUMMY_ELEMENT) {
  def getText: String = null
  def removeRange(firstNodeToRemove: ASTNode, firstNodeToKeep: ASTNode) {}
  def replaceChild(oldChild: ASTNode, newChild: ASTNode) {}
  override def toString: String = "Dummy AST node"
  def addChild(child: ASTNode) {}
  def textContains(c: Char): Boolean = false
  def replaceAllChildrenToChildrenOf(anotherParent: ASTNode) {}
  def addChild(child: ASTNode, anchorBefore: ASTNode) {}
  def getTextLength: Int = 42
  def getChildren(filter: TokenSet): Array[ASTNode] = Array[ASTNode]()
  def addLeaf(leafType: IElementType, leafText: CharSequence, anchorBefore: ASTNode) {}
  def removeChild(child: ASTNode) {}
  def addChildren(firstChild: ASTNode, firstChildToNotAdd: ASTNode, anchorBefore: ASTNode) {}
  def findChildByType(typesSet: TokenSet) = null
  def findChildByType(elem: IElementType) = null
  def findChildByType(elem: IElementType, anchor: ASTNode) = null
  def findChildByType(typesSet: TokenSet, anchor: ASTNode) = null
  def findLeafElementAt(offset: Int) = null
  def textToCharArray = new Array[Char](42)
  def getLastChildNode = null
  def getFirstChildNode = null
  def hc: Int = 42
  def getPsi = null
  def acceptTree(visitor: TreeElementVisitor) {}
  def getCachedLength: Int = 42
  def textMatches(buffer: CharSequence, start: Int): Int = -1
  def getNotCachedLength: Int = 42
  def getChars: CharSequence = textToCharArray.mkString
  def getPsi[T <: PsiElement](p1: Class[T]): T = null.asInstanceOf[T]

  def getInstanceForJava: DummyASTNode.type = this
}