package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCase
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType

final class ScEnumCaseImpl private[psi](stub: ScTemplateDefinitionStub[ScEnumCase],
                                        nodeType: ScTemplateDefinitionElementType[ScEnumCase],
                                        node: ASTNode)
  extends ScTypeDefinitionImpl(stub, nodeType, node, lexer.ScalaTokenTypes.kCASE)
    with ScEnumCase {

  import extensions._

  override def toString: String = "ScEnumCase" + ifReadAllowed {
    declaredNames.commaSeparated(new Model.Val(": ", ""))
  }("")

  override def declaredElements: Seq[PsiNamedElement] = Seq(this)

  //noinspection TypeAnnotation
  override protected def baseIcon = icons.Icons.CLASS; // TODO add an icon

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitEnumCase(this)
}
