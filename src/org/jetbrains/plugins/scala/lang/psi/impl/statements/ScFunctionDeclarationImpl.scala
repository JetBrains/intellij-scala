package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Unit}

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
                                         
