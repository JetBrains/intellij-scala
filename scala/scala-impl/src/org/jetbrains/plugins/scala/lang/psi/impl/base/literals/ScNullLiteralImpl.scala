package org.jetbrains.plugins.scala.lang.psi.impl.base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, literals}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

final class ScNullLiteralImpl(node: ASTNode,
                              override val toString: String)
  extends ScLiteralImplBase(node, toString)
    with literals.ScNullLiteral {

  override protected def innerType: Right[Nothing, ScType] = Right {
    wrappedValue(getValue).wideType(getProject)
  }

  override protected def fallbackType: ScType = api.Null

  override protected def wrappedValue(value: Null): ScLiteral.Value[Null] = new ScLiteral.Value(value) {
    override def wideType(implicit project: Project): ScType = api.Null
  }

  override def getValue: Null = null
}
