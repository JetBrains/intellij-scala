package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.extensions.SeqExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TupleType, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScType}

/**
 * Can be used to:
 * 1) Parse subtypes (for tooltips, navigation)
 * 2) Parse subgroups (for folding)
 * 3) Detect non-matching elements (for error highlighting)
 * 4) Match elements pairwise (for table-based tooltip)
 * */
// TODO Work in progress (it's not yet clear what's the best way to implement this functionality)
// TODO Separate Tree implementation from Match / Mismatch?
// TODO First parse the trees and then compare them? (but how to balance placeholders?)
sealed trait TypeDiff {
  def flatten: Seq[TypeDiff] = flattenTo(maxChars = Int.MaxValue, groupLength = 0)

  def flattenTo(maxChars: Int, groupLength: Int): Seq[TypeDiff] = flattenTo0(maxChars, groupLength)._1

  protected def flattenTo0(maxChars: Int, groupLength: Int) = (Seq(this), length(groupLength))

  protected def length(groupLength: Int): Int
}

object TypeDiff {
  final case class Group(diffs: Seq[TypeDiff]) extends TypeDiff {
    override def flattenTo0(maxChars: Int, groupLength: Int): (Seq[TypeDiff], Int) = {
      val (xs, length) = diffs.reverse.foldlr(0, (Vector.empty[TypeDiff], 0))((l, x) => l + x.length(groupLength)) { case (l, x, (acc, r)) =>
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

  // To display a type hint
  def parse(tpe: ScType): TypeDiff =
    group(tpe, tpe)((_, _) => true)

  // To display a type mismatch hint
  def forSecond(tpe1: ScType, tpe2: ScType): TypeDiff =
    group(tpe1, tpe2)((t1, t2) => t2.conforms(t1))

  // To display a type mismatch tooltip
  def forBoth(tpe1: ScType, tpe2: ScType): (TypeDiff, TypeDiff) =
    (group(tpe2, tpe1)((t1, t2) => t1.conforms(t2)), group(tpe1, tpe2)((t1, t2) => t2.conforms(t1)))

  private def group(tpe1: ScType, tpe2: ScType)(implicit conforms: (ScType, ScType) => Boolean) = Group(diff(tpe1, tpe2))

  private def diff(tpe1: ScType, tpe2: ScType)(implicit conforms: (ScType, ScType) => Boolean): Seq[TypeDiff] = {
    (tpe1, tpe2) match {
      case (TupleType(ts1), TupleType(ts2)) =>
        if (ts1.length == ts2.length) Match("(") +: (ts1, ts2).zipped.map(diff).intersperse(Seq(Match(", "))).flatten :+ Match(")")
        else Seq(Mismatch(tpe2.presentableText))

      case (FunctionType(r1, p1), FunctionType(r2, p2)) =>
        val left = {
          if (p1.length == p2.length) {
            val parameters = (p1, p2).zipped.map((t1, t2) => diff(t1, t2)(reversed)).intersperse(Seq(Match(", "))).flatten
            if (parameters.length == 1) parameters else Seq(Match("("), Group(parameters), Match(")"))
          } else {
            Seq(Mismatch(if (p2.length == 1) p2.head.presentableText else p2.map(_.presentableText).mkString("(", ", ", ")")))
          }
        }
        val right = diff(r1, r2)
        left ++ Seq(Match(" => "), Group(right))

      case (t1: ScParameterizedType, t2: ScParameterizedType) =>
        val fs: Seq[(ScType, ScType) => Boolean] = t1.designator.extractClass match {
          case Some(scalaClass: ScClass) => scalaClass.typeParameters.map(_.variance).map {
            case Variance.Invariant => (t1: ScType, t2: ScType) => t1.equiv(t2)
            case Variance.Covariant => conforms
            case Variance.Contravariant => reversed(conforms)
          }
          case _ => Seq.fill(t2.typeArguments.length)((t1: ScType, t2: ScType) => t1.equiv(t2))
        }
        val inner = if (t1.typeArguments.length == t2.typeArguments.length)
          (t1.typeArguments, t2.typeArguments, fs).zipped.map((t1, t2, conforms) => diff(t1, t2)(conforms)).intersperse(Seq(Match(", "))).flatten
        else
          Seq(Mismatch(t2.typeArguments.map(_.presentableText).mkString(", ")))

        diff(t1.designator, t2.designator) :+ Match("[") :+ Group(inner) :+ Match("]")

      case (t1, t2) =>
        Seq(if (conforms(t1, t2)) Match(tpe2.presentableText, Some(tpe2)) else Mismatch(tpe2.presentableText, Some(tpe2))) // TODO wrap each type in a Group?
    }
  }

  private def reversed(implicit conforms: (ScType, ScType) => Boolean) = (t1: ScType, t2: ScType) => conforms(t2, t1)
}