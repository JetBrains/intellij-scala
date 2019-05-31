package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.extensions.SeqExt
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

// Can be used to:
// 1) Find differences between two types.
// 2) Find nested regions in a type.
sealed trait TypeDiff {
  def flatten: Seq[TypeDiff] = flattenTo(maxChars = Int.MaxValue, groupLength = 0)

  def flattenTo(maxChars: Int, groupLength: Int): Seq[TypeDiff] = flattenTo0(maxChars, groupLength)._1

  protected def flattenTo0(maxChars: Int, groupLength: Int) = (Seq(this), length(groupLength))

  protected def length(groupLength: Int): Int
}

object TypeDiff {
  final case class Group(diffs: Seq[TypeDiff]) extends TypeDiff {
    override def flattenTo0(maxChars: Int, groupLength: Int): (Seq[TypeDiff], Int) = {
      val (xs, length) = diffs.reverse.foldlr(0, (Seq.empty[TypeDiff], 0))((l, x) => l + x.length(groupLength)) { case (l, x, (acc, r)) =>
        val (xs, length) = x.flattenTo0(maxChars - l - r, groupLength)
        (acc ++ xs, length + r)
      }
      if (length <= maxChars.max(groupLength)) (xs, length) else (Seq(Group(xs)), groupLength)
    }

    override protected def length(groupLength: Int): Int = groupLength
  }

  final case class Match(text: String, tpe: Option[ScType] = None) extends TypeDiff {
    override protected def length(groupLength: Int): Int = text.length
  }

  final case class Mismatch(text: String, tpe: Option[ScType] = None) extends TypeDiff {
    override protected def length(groupLength: Int): Int = text.length
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
        val inner = if (t1.typeArguments.length == t2.typeArguments.length)
          (t1.typeArguments, t2.typeArguments).zipped.map(diff).intersperse(Seq(Match(", "))).flatten
        else
          Seq(Mismatch(t2.typeArguments.map(_.presentableText).mkString(", ")))

        diff(t1.designator, t2.designator) :+ Match("[") :+ Group(inner) :+ Match("]")

      case (t1, t2) =>
        Seq(if (conforms(t1, t2)) Match(tpe2.presentableText, Some(tpe2)) else Mismatch(tpe2.presentableText, Some(tpe2)))
    }
  }
}