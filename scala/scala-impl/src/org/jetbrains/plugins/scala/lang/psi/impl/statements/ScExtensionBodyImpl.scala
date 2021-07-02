package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScFunctionDefinitionFactory
import org.jetbrains.plugins.scala.extensions.StubBasedExt
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.{EXTENSION_BODY, FUNCTION_DEFINITION}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtensionBody, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtensionBodyStub

final class ScExtensionBodyImpl private (stub: ScExtensionBodyStub, node: ASTNode)
    extends ScalaStubBasedElementImpl(stub, EXTENSION_BODY, node)
    with ScExtensionBody {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScExtensionBodyStub) = this(stub, null)

  override def toString: String = "ScExtensionBody"

  override def functions: Seq[ScFunctionDefinition] =
    this.stubOrPsiChildren(FUNCTION_DEFINITION, ScFunctionDefinitionFactory).toSeq
}
