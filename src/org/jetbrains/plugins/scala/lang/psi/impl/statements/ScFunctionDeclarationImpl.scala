package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, Unit}

/**
* @author Alexander Podkhalyuzin
*/

class ScFunctionDeclarationImpl private (stub: StubElement[ScFunction], nodeType: IElementType, node: ASTNode)
  extends ScFunctionImpl(stub, nodeType, node) with ScFunctionDeclaration {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitFunctionDeclaration(this)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this(null, null, node)}
  def this(stub: ScFunctionStub) = {this(stub, ScalaElementTypes.FUNCTION_DECLARATION, null)}

  override def toString: String = "ScFunctionDeclaration: " + name

  def returnTypeInner: TypeResult[ScType] = {
    typeElement match {
      case Some(t) => t.getType(TypingContext.empty)
      case None => Success(Unit, Some(this))
    }
  }
}
                                         
