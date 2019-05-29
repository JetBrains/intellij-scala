package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.TypeDiff.Show
import org.jetbrains.plugins.scala.extensions.SeqExt
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

// Can be used to:
// 1) Find differences between two types.
// 2) Find nested regions in type.
sealed trait TypeDiff {
  def format(f: Show): String
}

object TypeDiff {
  type Show = (String, Boolean) => String

  final case class Group(elements: Seq[TypeDiff]) extends TypeDiff {
    override def format(f: Show): String =
      elements.map(_.format(f)).mkString
  }

  final case class Match(s: String) extends TypeDiff {
    override def format(f: Show): String = f(s, true)
  }

  final case class Mismatch(s: String) extends TypeDiff {
    override def format(f: Show): String = f(s, false)
  }

  // To display folding (type hint)
  def parse(tpe: ScType): TypeDiff =
    group(tpe, tpe)((_, _) => true)

  // To display a single type (type mismatch hint)
  def forSecond(tpe1: ScType, tpe2: ScType): TypeDiff =
    group(tpe1, tpe2)((t1, t2) => t2.conforms(t1))

  // To display the both types (tooltip)
  def forBoth(tpe1: ScType, tpe2: ScType): (TypeDiff, TypeDiff) =
    (group(tpe2, tpe1)((t1, t2) => t1.conforms(t2)), group(tpe1, tpe2)((t1, t2) => t2.conforms(t1)))

  private def group(tpe1: ScType, tpe2: ScType)(implicit conforms: (ScType, ScType) => Boolean) = Group(diff(tpe1, tpe2))

  private def diff(tpe1: ScType, tpe2: ScType)(implicit conforms: (ScType, ScType) => Boolean): Seq[TypeDiff] = {
    (tpe1, tpe2) match {
      case (t1: ScParameterizedType, t2: ScParameterizedType) =>
        diff(t1.designator, t2.designator) :+
          Match("[") :+ Group((t1.typeArguments, t2.typeArguments).zipped.map(diff).intersperse(Seq(Match(", "))).flatten) :+ Match("]")

      case (t1, t2) =>
        Seq(if (conforms(t1, t2)) Match(tpe2.presentableText) else Mismatch(tpe2.presentableText))
    }
  }
}