package org.jetbrains.plugins.scala.lang.psi.impl.base
package types

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.Any
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScAndType, ScCompoundType, ScType}

class ScCompoundTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScCompoundTypeElement {
  override protected def innerType: TypeResult = {
    def withRefinement(des: Seq[ScType], default: ScType): ScType =
      refinement.map { r =>
        ScCompoundType.fromPsi(des, r.holders, r.types)
      }.getOrElse(default)

    val componentsTypes = components.map(_.`type`().getOrAny)

    val result =
      if (this.isInScala3File && components.nonEmpty) {
        val andType =
          if (componentsTypes.isEmpty)        Any
          else if (componentsTypes.size == 1) componentsTypes.head
          else                                componentsTypes.reduceRight(ScAndType.apply)

        withRefinement(Seq(andType), default = andType)
      } else
        withRefinement(componentsTypes, default = ScCompoundType(componentsTypes))

    Right(result)
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitCompoundTypeElement(this)
  }
}