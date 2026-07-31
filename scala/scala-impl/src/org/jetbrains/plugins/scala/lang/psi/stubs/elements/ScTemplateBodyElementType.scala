package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScTemplateBodyImpl

final class ScTemplateBodyElementType extends ScStubElementType[ScTemplateBody]("template body") {
  override def createElement(node: ASTNode): ScTemplateBody = new ScTemplateBodyImpl(node)
}
