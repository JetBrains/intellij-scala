package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.api.Unit
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
  * @author Alexander Podkhalyuzin
  */
final class ScFunctionDeclarationImpl private[psi](stub: ScFunctionStub[ScFunctionDeclaration],
                                                   nodeType: ScFunctionElementType[ScFunctionDeclaration],
                                                   node: ASTNode)
  extends ScFunctionImpl(stub, nodeType, node)
    with ScFunctionDeclaration {

  override def accept(visitor: ScalaElementVisitor): Unit =
    visitor.visitFunctionDeclaration(this)

  override def accept(visitor: PsiElementVisitor): Unit = visitor match {
    case scalaVisitor: ScalaElementVisitor => accept(scalaVisitor)
    case _ => super.accept(visitor)
  }

  override def toString: String = "ScFunctionDeclaration: " + ifReadAllowed(name)("")

  override protected def returnTypeInner: TypeResult = returnTypeElement match {
    case Some(t) => t.`type`()
    case None => Right(Unit)
  }
}
                                         
