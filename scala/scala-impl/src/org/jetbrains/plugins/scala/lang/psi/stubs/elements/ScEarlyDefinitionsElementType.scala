package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.ScEarlyDefinitionsImpl

final class ScEarlyDefinitionsElementType
  extends ScStubElementType[ScEarlyDefinitions]("early definitions") {
  override def createElement(node: ASTNode): ScEarlyDefinitions = new ScEarlyDefinitionsImpl(node)
}
