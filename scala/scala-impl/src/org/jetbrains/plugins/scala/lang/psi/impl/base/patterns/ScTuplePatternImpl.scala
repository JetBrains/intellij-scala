package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package patterns

import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.extensions.ClassQualifiedName
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType

class ScTuplePatternImpl(node: ASTNode) extends ScalaPsiElementImpl (node) with ScPatternImpl with ScTuplePattern {
  override def isIrrefutableFor(t: Option[ScType]): Boolean = t match {
    case Some(parameterizedType@ParameterizedType(ScDesignatorType(ClassQualifiedName(qName)), _)) if qName == s"scala.Tuple${subpatterns.length}" =>
      subpatterns.corresponds(parameterizedType.typeArguments) {
        case (pattern, ty) => pattern.isIrrefutableFor(Some(ty))
      }
    case _ => false
  }

  override def toString: String = "TuplePattern"

  override def subpatterns: Seq[ScPattern] =  patternList match {
    case Some(l) => l.patterns
    case None => Seq.empty
  }
}