package org.jetbrains.plugins.scala.lang.psi.impl.base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{NamedTupleType, TupleType}

class ScTuplePatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatternImpl with ScTuplePattern {
  override def isIrrefutableForImpl(t: Option[ScType]): Boolean = t match {
    case Some(TupleType(comps)) =>
      subpatterns.corresponds(comps) {
        case (pattern, ty) => pattern.isIrrefutableFor(Some(ty))
      }
    case Some(NamedTupleType(incomingComps)) =>
      incomingComps.corresponds(subpatterns) {
        case ((_, incomingTy), expectedPattern) =>
          expectedPattern.isIrrefutableFor(Some(incomingTy))
        case _ =>
          false
      }
    case _ => false
  }

  override def toString: String = "TuplePattern"

  override def subpatterns: Seq[ScPattern] =  patternList match {
    case Some(l) => l.patterns
    case None => Seq.empty
  }
}
