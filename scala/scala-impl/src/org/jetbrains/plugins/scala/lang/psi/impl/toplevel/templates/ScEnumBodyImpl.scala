package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package templates

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScEnumBody
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateBodyStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateBodyElementType

final class ScEnumBodyImpl private[psi](stub: ScTemplateBodyStub,
                                        nodeType: ScTemplateBodyElementType,
                                        node: ASTNode)
  extends ScTemplateBodyImpl(stub, nodeType, node)
    with ScEnumBody {

  override def toString: String = "ScEnumBody"

  override def cases: Seq[ScEnumCase] =
    getStubOrPsiChildren(
      ScalaElementType.ENUM_CASE_DEFINITION,
      JavaArrayFactoryUtil.ScEnumCaseFactory
    ).toSeq.filterNot(_.isLocal)
}
