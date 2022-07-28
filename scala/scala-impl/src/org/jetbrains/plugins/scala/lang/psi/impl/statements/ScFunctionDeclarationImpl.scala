package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result._

final class ScFunctionDeclarationImpl private[psi](stub: ScFunctionStub[ScFunctionDeclaration],
                                                   nodeType: ScFunctionElementType[ScFunctionDeclaration],
                                                   node: ASTNode)
  extends ScFunctionImpl(stub, nodeType, node)
    with ScFunctionDeclaration {

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitFunctionDeclaration(this)

  override def toString: String = "ScFunctionDeclaration: " + ifReadAllowed(name)("")

  override def returnType: TypeResult = returnTypeElement match {
    case Some(t) => t.`type`()
    case None => Right(Unit)
  }
}
                                         
