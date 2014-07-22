package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import stubs.ScFunctionStub
import com.intellij.lang.ASTNode

import api.statements._
import types.result.{Success, TypingContext, TypeResult}
import types.{Unit, ScType}
import com.intellij.psi.PsiElementVisitor
import api.ScalaElementVisitor

/**
* @author Alexander Podkhalyuzin
*/

class ScFunctionDeclarationImpl extends ScFunctionImpl with ScFunctionDeclaration {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitFunctionDeclaration(this)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScFunctionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScFunctionDeclaration: " + name

  def returnTypeInner: TypeResult[ScType] = {
    typeElement match {
      case Some(t) => t.getType(TypingContext.empty)
      case None => Success(Unit, Some(this))
    }
  }
}
                                         
