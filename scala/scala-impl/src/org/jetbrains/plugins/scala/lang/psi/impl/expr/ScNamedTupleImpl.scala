package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScNamedTuple, ScNamedTupleExprComponent}
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, NamedTupleType, Singleton, StdTypes}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}

final class ScNamedTupleImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScNamedTuple {
  protected override def innerType(expectedType: Option[ScType]): TypeResult = {
    implicit val project: Project = this.projectContext
    val stdTypes = StdTypes.instance(project)

    val actualExpectedType = expectedType.orElse(this.expectedType())

    val expectedComponents =
      actualExpectedType match {
        case Some(NamedTupleType(comps)) if comps.size == components.size => comps.map(_._2)
        case _                                                            => components.map(_ => Any)
      }

    def transformComponent(comp: ScNamedTupleExprComponent, pt: Option[ScType]): (ScType, ScType) = {
      val exprType = comp.expr match {
        case Some(expr) => expr.`type`(pt).getOrNothing
        case None => stdTypes.Nothing
      }

      (comp.nameLiteralType.getOrNothing, exprType)
    }

    val typedComponents = components.zip(expectedComponents).map {
      case (comp, pt) =>
        val typed = transformComponent(comp, Option(pt))
        typed match {
          case t @ (_, _: ScLiteralType) if pt.conforms(Singleton) => t
          case (name, lit: ScLiteralType)                          => (name, lit.widen)
          case other                                               => other
        }
    }

    Right(typedComponents match {
      case Seq() => stdTypes.Unit
      case components => NamedTupleType(components)
    })
  }

  override def deleteChildInternal(child: ASTNode): Unit =
    ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)

  override def toString: String = "NamedTuple"
}
