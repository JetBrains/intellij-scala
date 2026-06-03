package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScNamedTupleTypeComponent, ScNamedTupleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStringLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{NamedTupleType, StdTypes}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScNamedTupleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScNamedTupleTypeElement {
  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitNamedTupleTypeElement(this)
  }

  override protected def innerType: TypeResult = {
    def transformComponent(comp: ScNamedTupleTypeComponent): (ScType, ScType) = {
      implicit val project: Project = this.projectContext
      val stdTypes = StdTypes.instance(project)
      val nameType = comp.nameElement match {
        case Some(nameElement) => ScLiteralType(ScStringLiteralImpl.Value(nameElement.getText), psiElement = comp)
        case None => stdTypes.Nothing
      }
      val exprType = comp.typeElement match {
        case Some(expr) => expr.`type`().getOrNothing
        case None => stdTypes.Nothing
      }
      (nameType, exprType)
    }

    Right(
      NamedTupleType(components.map(transformComponent))
    )
  }
}
