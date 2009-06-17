package org.jetbrains.plugins.scala.lang.psi.impl.base

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl





import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import psi.stubs.ScPrimaryConstructorStub;
import com.intellij.psi._

import org.jetbrains.annotations._

import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._
import org.jetbrains.plugins.scala.lang.psi.impl.statements._

/**
* @author Alexander Podkhalyuzin
* Date: 07.03.2008
*/

class ScPrimaryConstructorImpl extends ScalaStubBasedElementImpl[ScPrimaryConstructor] with ScPrimaryConstructor {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScPrimaryConstructorStub) = {this(); setStub(stub); setNode(null)}

  override def hasAnnotation: Boolean = {
    return !(getNode.getFirstChildNode.getFirstChildNode == null)
  }

  //todo rewrite me!
  override def hasModifier: Boolean = false

  def getClassNameText: String = {
    return getNode.getTreeParent.getPsi.asInstanceOf[ScTypeDefinition].getName
  }

  override def toString: String = "PrimaryConstructor"
}