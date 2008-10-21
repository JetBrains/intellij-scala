package org.jetbrains.plugins.scala.lang.psi.stubs.elements.wrappers
import com.intellij.psi.tree.{TokenSet, IElementType}
import com.intellij.lang.ASTNode
import com.intellij.psi.impl.source.tree.{TreeElement, TreeElementVisitor}

/**
 * @author ilyas
 */

object DummyASTNode extends TreeElement {
  def getText: String = null
  def removeRange(firstNodeToRemove: ASTNode, firstNodeToKeep: ASTNode): Unit = {

  }
  def replaceChild(oldChild: ASTNode, newChild: ASTNode): Unit = {

  }

  override def toString: String = "Dummy AST node"

  def addChild(child: ASTNode): Unit = {

  }
  def textContains(c: Char): Boolean = false
  def replaceAllChildrenToChildrenOf(anotherParent: ASTNode): Unit = {

  }
  def addChild(child: ASTNode, anchorBefore: ASTNode): Unit = {

  }
  def getElementType: IElementType = null
  def getTextLength: Int = 42
  def getChildren(filter: TokenSet) = Array[ASTNode]()
  def addLeaf(leafType: IElementType, leafText: CharSequence, anchorBefore: ASTNode): Unit = {

  }
  def removeChild(child: ASTNode): Unit = {

  }
  def addChildren(firstChild: ASTNode, firstChildToNotAdd: ASTNode, anchorBefore: ASTNode): Unit = {

  }
  def findChildByType(typesSet: TokenSet) = null
  def findChildByType(elem: IElementType) = null
  def findChildByType(typesSet: TokenSet, anchor: ASTNode) = null
  def findLeafElementAt(offset: Int) = null
  def textToCharArray = new Array[Char](42)
  def getLastChildNode = null
  def getFirstChildNode = null
  def hc: Int = 42
  def getPsi = null
  def acceptTree(visitor: TreeElementVisitor) {
  }
}