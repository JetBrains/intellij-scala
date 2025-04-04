package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScNamedTuplePattern, ScNamedTuplePatternComponent, ScPattern}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.NamedTupleType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

class ScNamedTuplePatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatternImpl with ScNamedTuplePattern {

  override def isIrrefutableForImpl(t: Option[ScType]): Boolean = t.exists {
    case NamedTupleType(incomingComps) =>
      val incoming = NamedTupleType.makeComponentMap(incomingComps)
      components.forall {
        case ScNamedTuplePatternComponent(expectedName, expectedPattern)  =>

          incoming
            .get(expectedName)
            .exists(
              incomingTy => expectedPattern.isIrrefutableFor(Some(incomingTy))
            )
        case _ =>
          false
      }
    case _ => false
  }

  override def subpatterns: Seq[ScPattern] = components.flatMap(_.subPattern)

  override def `type`(): TypeResult = {
    def transformComponent(comp: ScNamedTuplePatternComponent): (ScType, ScType) = {
      val exprType = comp.flatMap(comp.subPattern)(_.`type`()).getOrNothing
      (comp.nameLiteralType.getOrNothing, exprType)
    }

    val comps = components.map(transformComponent)
    Right(NamedTupleType(comps))
  }

  override def toString: String = "NamedTuplePattern"
}
