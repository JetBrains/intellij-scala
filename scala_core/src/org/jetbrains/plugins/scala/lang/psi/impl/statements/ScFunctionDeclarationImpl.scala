package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.psi.stubs.StubElement
import stubs.elements.wrappers.DummyASTNode
import stubs.ScFunctionStub
import types.{ScFunctionType, Nothing}
import com.intellij.lang.ASTNode

import psi.ScalaPsiElementImpl
import api.statements._

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:08
*/

class ScFunctionDeclarationImpl(node: ASTNode) extends ScFunctionImpl(node) with ScFunctionDeclaration {
  def this(stub: ScFunctionStub) = {
    this(DummyASTNode)
    setStub(stub)
    setNode(node)
  }

  override def toString: String = "ScFunctionDeclaration"

  override def calcType = typeElement match {
    case Some(te) => _calcType(te.getType) 
    case None => Nothing //todo use base function in case one is present
  }
}

