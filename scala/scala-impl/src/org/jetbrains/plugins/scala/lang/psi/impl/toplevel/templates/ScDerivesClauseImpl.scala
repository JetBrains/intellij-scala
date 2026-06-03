package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates

import com.intellij.lang.ASTNode
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScDerivesClause
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScDerivesClauseOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScDerivesClauseStub

final class ScDerivesClauseImpl private(stub: ScDerivesClauseStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.DERIVES_CLAUSE, node) with ScDerivesClause {

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitDerivesClause(this)

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScDerivesClauseStub) = this(stub, null)

  override def toString: String = "DerivesClause"

  override def derivedReferences: Seq[ScReference] = findChildren[ScReference]

  override def owner: ScDerivesClauseOwner = PsiTreeUtil.getContextOfType(this, classOf[ScDerivesClauseOwner])
}
