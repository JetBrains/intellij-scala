package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScGivenAlias
import org.jetbrains.plugins.scala.lang.psi.impl.statements.ScFunctionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScFunctionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScFunctionElementType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScGivenAliasImpl(stub: ScFunctionStub[ScGivenAlias],
                       nodeType: ScFunctionElementType[ScGivenAlias],
                       node: ASTNode)
  extends ScFunctionImpl(stub, nodeType, node)
    with ScGivenImpl
    with ScGivenAlias
{
  override def returnType: TypeResult = ???
}
