package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, api}

/**
  * @author Alexander Podkhalyuzin
  *         Date: 22.02.2008
  */

class ScFunctionDefinitionImpl(stub: ScFunctionStub,
                               nodeType: ScFunctionElementType,
                               node: ASTNode)
  extends ScFunctionImpl(stub, nodeType, node)
    with ScFunctionDefinition {

  override protected def shouldProcessParameters(lastParent: PsiElement): Boolean =
    super.shouldProcessParameters(lastParent) || body.contains(lastParent)

  override def toString: String = "ScFunctionDefinition: " + ifReadAllowed(name)("")

  override protected def returnTypeInner: TypeResult = returnTypeElement match {
    case None if !hasAssign => Right(api.Unit)
    case None => body match {
      case Some(b) => b.`type`().map(ScLiteralType.widenRecursive)
      case _ => Right(api.Unit)
    }
    case Some(rte: ScTypeElement) => rte.`type`()
  }

  def body: Option[ScExpression] = byPsiOrStub(findChild(classOf[ScExpression]))(_.bodyExpression)

  override def hasAssign: Boolean = byStubOrPsi(_.hasAssign)(assignment.isDefined)

  def assignment = Option(findChildByType[PsiElement](ScalaTokenTypes.tASSIGN))

  override def getBody: FakePsiCodeBlock = body match {
    case Some(b) => new FakePsiCodeBlock(b) // Needed so that LineBreakpoint.canAddLineBreakpoint allows line breakpoints on one-line method definitions
    case None => null
  }

  override def accept(visitor: ScalaElementVisitor): Unit =
    visitor.visitFunctionDefinition(this)

  override def accept(visitor: PsiElementVisitor): Unit = visitor match {
    case scalaVisitor: ScalaElementVisitor => accept(scalaVisitor)
    case _ => super.accept(visitor)
  }
}
