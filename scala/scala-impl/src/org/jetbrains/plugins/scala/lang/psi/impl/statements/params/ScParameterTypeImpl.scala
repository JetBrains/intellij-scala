package org.jetbrains.plugins.scala
package lang
package psi
package impl
package statements
package params

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

class ScParameterTypeImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScParameterType {

  override def toString: String = "ParameterType"

  override def typeElement: ScTypeElement = findChildByClass(classOf[ScTypeElement])

}