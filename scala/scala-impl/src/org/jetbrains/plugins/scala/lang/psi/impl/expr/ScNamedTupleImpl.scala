package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScNamedTuple, ScNamedTupleExprComponent}
import org.jetbrains.plugins.scala.lang.psi.types.api.{NamedTupleType, Singleton, StdTypes}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}

final class ScNamedTupleImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScNamedTuple {
  protected override def innerType: TypeResult = {
    implicit val project: Project = this.projectContext
    val stdTypes = StdTypes.instance(project)

    def transformComponent(comp: ScNamedTupleExprComponent): (ScType, ScType) = {
      val exprType = comp.expr match {
        case Some(expr) => expr.`type`().getOrNothing
        case None => stdTypes.Nothing
      }
      (comp.nameLiteralType.getOrNothing, exprType)
    }

    Right(components.map(transformComponent) match {
      case Seq() => stdTypes.Unit
      case components =>
        val expectedType = this.expectedType()
        val expectedComponents =
          expectedType match {
            case Some(NamedTupleType(comps)) => comps
            case _ => Seq.empty
          }

        val widenedComponents =
          components.zipAll(expectedComponents, null, null).collect {
            case (t@(_, _: ScLiteralType), (_, expectedType)) if expectedType.conforms(Singleton) => t
            case ((name, ty: ScLiteralType), _) => (name, ty.widen)
            case (other@(_, _), _) => other
          }

        NamedTupleType(widenedComponents)
    })
  }

  override def deleteChildInternal(child: ASTNode): Unit =
    ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)

  override def toString: String = "NamedTuple"
}
