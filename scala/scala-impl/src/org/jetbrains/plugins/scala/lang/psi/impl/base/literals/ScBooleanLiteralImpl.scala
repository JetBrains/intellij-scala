package org.jetbrains.plugins.scala.lang.psi.impl.base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, literals}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

import java.lang.{Boolean => JBoolean}

final class ScBooleanLiteralImpl(node: ASTNode,
                                 override val toString: String)
  extends ScLiteralImplBase(node, toString)
    with literals.ScBooleanLiteral {

  override protected def wrappedValue(value: JBoolean): ScLiteral.Value[JBoolean] =
    ScBooleanLiteralImpl.Value(value)

  override protected def fallbackType: ScType = api.Boolean

  override def getValue: JBoolean = {
    import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes._
    getNode.getFirstChildNode.getElementType match {
      case `kTRUE` => JBoolean.TRUE
      case `kFALSE` => JBoolean.FALSE
    }
  }
}

object ScBooleanLiteralImpl {

  final case class Value(override val value: JBoolean) extends ScLiteral.Value(value) {

    override def wideType(implicit project: Project): ScType = api.Boolean
  }
}
