package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import stubs.ScFunctionStub
import com.intellij.lang.ASTNode

import api.statements._
import types.{ScType}
import types.result.{Failure, TypingContext, TypeResult}

/**
* @author Alexander Podkhalyuzin
*/

class ScFunctionDeclarationImpl extends ScFunctionImpl with ScFunctionDeclaration {
  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScFunctionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScFunctionDeclaration"

  def returnType: TypeResult[ScType] = {
    typeElement match {
      case Some(t) => t.getType(TypingContext.empty)
      case None => Failure("No return type here", Some(this))
    }
    //todo: Scala Compiler Bug: NPE on file ScalaTracker.scala (should be checked with fresh stubs)
    //wrap(typeElement) flatMap (_.getType(TypingContext.empty))
  }
}

