package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.lang.psi.annotator.ScPatternAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScPattern, ScReferencePattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeVariableTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenerator
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

import scala.collection.mutable

trait ScPatternImpl extends ScPattern with ScPatternAnnotator {

  override def `type`(): TypeResult = Failure("Cannot type pattern")

  override def bindings: Seq[ScBindingPattern] = {
    val b = mutable.ArrayBuffer.empty[ScBindingPattern]

    def inner(p: ScPattern) {
      p match {
        case binding: ScBindingPattern => b += binding
        case _ =>
      }

      for (sub <- p.subpatterns) {
        inner(sub)
      }
    }

    inner(this)
    b
  }

  override def typeVariables: Seq[ScTypeVariableTypeElement] = {
    val b = mutable.ArrayBuffer.empty[ScTypeVariableTypeElement]

    def inner(p: ScPattern) {
      p match {
        case ScTypedPattern(te) =>
          te.accept(new ScalaRecursiveElementVisitor {
            override def visitTypeVariableTypeElement(tvar: ScTypeVariableTypeElement): Unit = {
              b += tvar
            }
          })
        case _ =>
      }

      for (sub <- p.subpatterns) {
        inner(sub)
      }
    }

    inner(this)
    b
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitPattern(this)
  }

  override def subpatterns: Seq[ScPattern] = this match {
    case _: ScReferencePattern => Seq.empty
    case _ => findChildrenByClassScala[ScPattern](classOf[ScPattern])
  }

  override def analogInDesugaredForExpr: Option[ScPattern] = {
    Some(getContext)
      .collect { case gen: ScGenerator => gen }
      .flatMap { _.forStatement }
      .flatMap { _.desugarePattern(this) }
  }
}
