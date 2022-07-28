package org.jetbrains.plugins.scala.lang.psi.impl.statements


import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.api.Any
import org.jetbrains.plugins.scala.lang.psi.types.result._

final class ScMacroDefinitionImpl private[psi](stub: ScFunctionStub[ScMacroDefinition],
                                               nodeType: ScFunctionElementType[ScMacroDefinition],
                                               node: ASTNode)
  extends ScFunctionImpl(stub, nodeType, node)
    with ScMacroDefinition {

  override protected def shouldProcessParameters(lastParent: PsiElement): Boolean =
    super.shouldProcessParameters(lastParent) || macroImplReference.contains(lastParent)

  override def toString: String = "ScMacroDefinition: " + ifReadAllowed(name)("")

  override def returnType: TypeResult = returnTypeElement match {
    case Some(rte: ScTypeElement) => rte.`type`()
    case None => Right(Any) // TODO look up type from the macro impl.
  }

  //todo: stub for macro definition should also contain reference
  override def macroImplReference: Option[ScStableCodeReference] =
    byPsiOrStub(findChild[ScStableCodeReference])(_ => None)

  override def hasAssign: Boolean = true

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitMacroDefinition(this)
}
