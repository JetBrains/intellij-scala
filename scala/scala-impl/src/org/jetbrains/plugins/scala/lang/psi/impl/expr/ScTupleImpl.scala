package org.jetbrains.plugins.scala.lang.psi.impl.expr

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Singleton, TupleType, Unit}
import org.jetbrains.plugins.scala.lang.psi.types.result._

class ScTupleImpl(node: ASTNode) extends ScExpressionImplBase(node) with ScTuple {

  protected override def innerType(expectedType: Option[ScType]): TypeResult = {
    val actualExpectedType = expectedType.orElse(this.expectedType())

    val expectedComponents = actualExpectedType match {
      case Some(TupleType(comps)) if comps.size == exprs.size => comps
      case _                                                  => exprs.map(_ => Any)
    }

    val typedComponents = exprs.zip(expectedComponents).map { case (comp, pt) =>
      val tpe = comp.`type`(expectedType = Option(pt)).getOrAny
      tpe match {
        case lit: ScLiteralType =>
          val inferSingleton = pt.conforms(Singleton)

          if (inferSingleton) lit
          else                lit.widen
        case other => other
      }
    }

    val res = typedComponents match {
      case Seq()      => Unit
      case components => TupleType(components, context = this)
    }

    Right(res)
  }

  override def deleteChildInternal(child: ASTNode): Unit = {
    ScalaPsiUtil.deleteElementInCommaSeparatedList(this, child)
  }

  override def toString: String = "Tuple"
}
