package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType

final class ScEnumCaseImpl(stub: ScTemplateDefinitionStub[ScEnumCase],
                           nodeType: ScTemplateDefinitionElementType[ScEnumCase],
                           node: ASTNode,
                           debugName: String)
  extends ScTypeDefinitionImpl(stub, nodeType, node, debugName)
    with ScEnumCase {

  import lexer.ScalaTokenTypes.kCASE

  //noinspection TypeAnnotation
  override protected def targetTokenType = kCASE

  //noinspection TypeAnnotation
  override protected def baseIcon = icons.Icons.CLASS; // TODO add an icon
}
