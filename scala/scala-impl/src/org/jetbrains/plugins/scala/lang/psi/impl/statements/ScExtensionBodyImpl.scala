package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil.ScFunctionFactory
import org.jetbrains.plugins.scala.extensions.StubBasedExt
import org.jetbrains.plugins.scala.lang.TokenSets.FUNCTIONS
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType.EXTENSION_BODY
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScExtensionBody, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScExtensionBodyStub

final class ScExtensionBodyImpl private (stub: ScExtensionBodyStub, node: ASTNode)
    extends ScalaStubBasedElementImpl(stub, EXTENSION_BODY, node)
    with ScExtensionBody {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScExtensionBodyStub) = this(stub, null)

  override def toString: String = "ScExtensionBody"

  override def functions: Seq[ScFunction] =
    this.stubOrPsiChildren(FUNCTIONS, ScFunctionFactory).toSeq
}
