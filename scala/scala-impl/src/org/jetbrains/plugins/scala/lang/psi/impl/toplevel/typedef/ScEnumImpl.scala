package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScEnum
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType

final class ScEnumImpl(stub: ScTemplateDefinitionStub[ScEnum],
                       nodeType: ScTemplateDefinitionElementType[ScEnum],
                       node: ASTNode,
                       debugName: String)
  extends ScTypeDefinitionImpl(stub, nodeType, node, debugName)
    with ScEnum {

  import lexer.ScalaTokenType.EnumKeyword

  //noinspection TypeAnnotation
  override protected def targetTokenType = EnumKeyword

  //noinspection TypeAnnotation
  override protected def baseIcon = icons.Icons.CLASS; // TODO add an icon
}
