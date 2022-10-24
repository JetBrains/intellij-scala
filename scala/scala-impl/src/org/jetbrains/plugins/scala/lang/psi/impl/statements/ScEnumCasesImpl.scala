package org.jetbrains.plugins.scala.lang.psi.impl.statements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScEnumCases}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScEnumCasesStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScEnumCasesElementType

final class ScEnumCasesImpl(stub: ScEnumCasesStub,
                            nodeType: ScEnumCasesElementType.type,
                            node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node)
    with ScEnumCases {

  override def toString: String =
    "ScEnumCases" + ifReadAllowed(declaredNames.mkString(": ", ", ", ""))("")

  override def declaredElements: Seq[ScEnumCase] =
    findChildrenByClassScala(classOf[ScEnumCase]).toSeq

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitEnumCases(this)
}
