package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType

final class ScEnumImpl(stub: ScTemplateDefinitionStub[ScTypeDefinition],
                       nodeType: ScTemplateDefinitionElementType[ScTypeDefinition],
                       node: ASTNode)
  extends ScTypeDefinitionImpl(stub, nodeType, node, ScalaTokenType.Enum) {

  override def toString: String = "ScEnum" + extensions.ifReadAllowed(": " + name)("")

  //noinspection TypeAnnotation
  override protected def baseIcon = icons.Icons.CLASS; // TODO add an icon
}
