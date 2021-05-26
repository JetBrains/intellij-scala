package org.jetbrains.plugins.scala.lang.psi.impl.base.patterns

import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScPattern, ScReferencePattern, ScTypedPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeVariableTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScGenerator
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, TypeResult}

import scala.collection.immutable.ArraySeq

trait ScPatternImpl extends ScPattern {

  override def `type`(): TypeResult = Failure(ScalaBundle.message("cannot.type.pattern"))

  override def bindings: Seq[ScBindingPattern] = {
    val builder = Seq.newBuilder[ScBindingPattern]

    def inner(p: ScPattern): Unit = {
      p match {
        case binding: ScBindingPattern if !binding.isWildcard =>
          builder += binding
        case _ =>
      }

      for (sub <- p.subpatterns) {
        inner(sub)
      }
    }

    inner(this)
    builder.result()
  }

  override def typeVariables: Seq[ScTypeVariableTypeElement] = {
    val builder = Seq.newBuilder[ScTypeVariableTypeElement]

    def inner(p: ScPattern): Unit = {
      p match {
        case ScTypedPattern(te) =>
          te.accept(new ScalaRecursiveElementVisitor {
            override def visitTypeVariableTypeElement(tvar: ScTypeVariableTypeElement): Unit = {
              builder += tvar
            }
          })
        case _ =>
      }

      for (sub <- p.subpatterns) {
        inner(sub)
      }
    }

    inner(this)
    builder.result()
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitPattern(this)
  }

  override def subpatterns: Seq[ScPattern] = this match {
    case _: ScReferencePattern => Seq.empty
    case _ => findChildren[ScPattern]
  }

  override def analogInDesugaredForExpr: Option[ScPattern] = {
    Some(getContext)
      .collect { case gen: ScGenerator => gen }
      .flatMap { _.forStatement }
      .flatMap { _.desugarPattern(this) }
  }
}
