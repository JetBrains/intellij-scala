package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScEnumCases}

final class ScEnumCasesImpl(node: ASTNode,
                            debugName: String)
  extends ScalaPsiElementImpl(node)
    with ScEnumCases {

  override def toString: String =
    debugName + ifReadAllowed(declaredNames.mkString(": ", ", ", ""))("")

  override def declaredElements: Seq[ScEnumCase] =
    findChildrenByClassScala(classOf[ScEnumCase]).toSeq

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitEnumCases(this)
}
