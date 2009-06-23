package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.psi.stubs.StubElement
import stubs.elements.wrappers.DummyASTNode
import stubs.ScFunctionStub
import com.intellij.lang.ASTNode

import psi.ScalaPsiElementImpl
import api.statements._
import types.{ScType, ScFunctionType, Nothing}

/** 
* @author Alexander Podkhalyuzin
* Date: 22.02.2008
* Time: 9:49:08
*/

class ScFunctionDeclarationImpl extends ScFunctionImpl with ScFunctionDeclaration {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScFunctionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScFunctionDeclaration"

  def returnType: ScType = {
    typeElement match {
      case Some(te) => te.getType
      case None => Nothing //todo use base function in case one is present
    }
  }

  override def calcType = super[ScFunctionImpl].calcType
}

