package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScType}

/**
 * @author Alexander Podkhalyuzin
 */

class ScCompoundTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCompoundTypeElement {
  protected def innerType: TypeResult[ScType] = {
    val componentsTypes = components.map(_.`type`().getOrAny)
    val compoundType = refinement.map { r =>
      ScCompoundType.fromPsi(componentsTypes, r.holders, r.types)
    }.getOrElse(new ScCompoundType(componentsTypes))

    Success(compoundType)
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitCompoundTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => accept(s)
      case _ => super.accept(visitor)
    }
  }
}