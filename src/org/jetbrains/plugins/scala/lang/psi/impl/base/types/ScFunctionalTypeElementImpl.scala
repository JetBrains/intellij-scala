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

/**
 * @author ilyas, Alexander Podkhalyuzin
 */

class ScFunctionalTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScFunctionalTypeElement {
  override def desugarizedText: String = {
    val paramTypes = paramTypeElement match {
      case tup: ScTupleTypeElement => tup.components
      case par: ScParenthesisedTypeElement if par.typeElement.isEmpty => Seq.empty
      case other => Seq(other)
    }
    s"_root_.scala.Function${paramTypes.length}[${paramTypes.map(_.getText).mkString(",")}${if (paramTypes.isEmpty) "" else ", "}" +
            s"${returnTypeElement.map(_.getText).getOrElse("Any")}]"
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitFunctionalTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitFunctionalTypeElement(this)
      case _ => super.accept(visitor)
    }
  }
}