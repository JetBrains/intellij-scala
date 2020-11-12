package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateDerives
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDerivesStub

final class ScTemplateDerivesImpl private(stub: ScTemplateDerivesStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.TEMPLATE_DERIVES, node) with ScTemplateDerives {

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitTemplateDerives(this)

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTemplateDerivesStub) = this(stub, null)

  override def toString: String = "TemplateDerives"

  override def deriveReferences: Seq[ScReference] = findChildrenByClassScala(classOf[ScReference]).toSeq
}
