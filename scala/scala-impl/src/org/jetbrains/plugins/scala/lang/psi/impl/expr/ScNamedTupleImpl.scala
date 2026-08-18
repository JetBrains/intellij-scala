package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScNamedTuple, ScNamedTupleExprComponent}
import org.jetbrains.plugins.scala.lang.psi.types.api.{NamedTupleType, StdTypes}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType, Widening}

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

        // The component types are inferred, so their literal types are widened unless the expected
        // component asks for a singleton, see Widening.widenInferred
        val widenedComponents =
          components.zipWithIndex.map {
            case ((name, ty: ScLiteralType), idx) =>
              (name, Widening.widenInferred(ty, expectedComponents.lift(idx).map(_._2)))
            case (other, _) => other
          }

        NamedTupleType(widenedComponents)
    })
  }

  override def deleteChildInternal(child: ASTNode): Unit =
    ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)

  override def toString: String = "NamedTuple"
}
