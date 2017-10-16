package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult}

/**
* @author Alexander Podkhalyuzin
*/

class ScFunctionDeclarationImpl private (stub: ScFunctionStub, node: ASTNode)
  extends ScFunctionImpl(stub, ScalaElementTypes.FUNCTION_DECLARATION, node) with ScFunctionDeclaration {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScFunctionStub) = this(stub, null)

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitFunctionDeclaration(this)
      case _ => super.accept(visitor)
    }
  }

  override def toString: String = "ScFunctionDeclaration: " + ifReadAllowed(name)("")

  def returnTypeInner: TypeResult[ScType] = {
    typeElement match {
      case Some(t) => t.getType()
      case None => Success(Unit, Some(this))
    }
  }
}
                                         
