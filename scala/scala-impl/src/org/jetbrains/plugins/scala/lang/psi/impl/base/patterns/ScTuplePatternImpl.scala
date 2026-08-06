package org.jetbrains.plugins.scala.lang.psi.impl.base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{NamedTupleType, TupleType}

class ScTuplePatternImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScPatternImpl with ScTuplePattern {
  override def isIrrefutableForImpl(scrutineeType: ScType, deep: Boolean): Boolean = scrutineeType match {
    case TupleType(comps) =>
      subpatterns.corresponds(comps) {
        case (pattern, ty) => !deep || pattern.isIrrefutableFor(ty, deep)
      }
    case NamedTupleType(incomingComps) =>
      incomingComps.corresponds(subpatterns) {
        case ((_, incomingTy), expectedPattern) =>
          !deep || expectedPattern.isIrrefutableFor(incomingTy, deep)
      }
    case _ =>
      false
  }

  override def toString: String = "TuplePattern"

  override def subpatterns: Seq[ScPattern] =  patternList match {
    case Some(l) => l.patterns
    case None => Seq.empty
  }

  def infixPatternOfWhichThisIsTheArgPatternList: Option[ScInfixPattern] = {
    getContext match {
      case infix: ScInfixPattern if !this.isInScala3File && infix.rightOption.contains(this) => Some(infix)
      case _ => None
    }
  }
}
