package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScIdList}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScEnumCases

final class ScEnumCasesImpl(node: ASTNode,
                            debugName: String)
  extends ScalaPsiElementImpl(node)
    with ScEnumCases {

  import parser.ScalaElementType.IDENTIFIER_LIST

  override def toString: String =
    debugName + ifReadAllowed(declaredNames.mkString(": ", ", ", ""))("")

  override def declaredElements: Seq[ScFieldId] =
    findNotNullChildByType[ScIdList](IDENTIFIER_LIST).fieldIds

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit =
    visitor.visitEnumCases(this)
}
