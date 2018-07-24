package org.jetbrains.plugins.scala.lang.psi.impl.statements


import com.intellij.lang.ASTNode
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.{PsiElementExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.types.api.Any
import org.jetbrains.plugins.scala.lang.psi.types.result._

/**
 * @author Jason Zaugg
 */
class ScMacroDefinitionImpl private (stub: ScFunctionStub, node: ASTNode)
  extends ScFunctionImpl(stub, ScalaElementTypes.MACRO_DEFINITION, node) with ScMacroDefinition {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScFunctionStub) = this(stub, null)

  override protected def shouldProcessParameters(lastParent: PsiElement): Boolean =
    super.shouldProcessParameters(lastParent) || macroImplReference.contains(lastParent)

  override def toString: String = "ScMacroDefinition: " + ifReadAllowed(name)("")

  override protected def returnTypeInner: TypeResult = returnTypeElement match {
    case Some(rte: ScTypeElement) => rte.`type`()
    case None => Right(Any) // TODO look up type from the macro impl.
  }

  //todo: stub for macro definition should also contain reference
  def macroImplReference: Option[ScStableCodeReferenceElement] =
    byPsiOrStub(findChild(classOf[ScStableCodeReferenceElement]))(_ => None)

  override def hasAssign: Boolean = true

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitMacroDefinition(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitMacroDefinition(this)
      case _ => super.accept(visitor)
    }
  }

}
