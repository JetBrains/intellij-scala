package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCases
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScEnumCasesImpl

final class ScEnumCasesElementType extends ScStubElementType[ScEnumCases]("ScEnumCases") {
  override def createElement(node: ASTNode): ScEnumCases = new ScEnumCasesImpl(null, null, node)
}
