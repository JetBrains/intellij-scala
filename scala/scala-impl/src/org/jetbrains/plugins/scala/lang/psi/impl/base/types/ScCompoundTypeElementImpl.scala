package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types.ScCompoundType
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScCompoundTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCompoundTypeElement {
  override protected def innerType: TypeResult = {
    val componentsTypes = components.map(_.`type`().getOrAny)
    val compoundType = refinement.map { r =>
      ScCompoundType.fromPsi(componentsTypes, r.holders, r.types)
    }.getOrElse(ScCompoundType(componentsTypes))

    Right(compoundType)
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitCompoundTypeElement(this)
  }
}