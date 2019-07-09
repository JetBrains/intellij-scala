package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiNamedElementExt, SeqExt}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ParameterizedType, TupleType, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScExistentialArgument, ScExistentialType, ScLiteralType, ScParameterizedType, ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

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

  def text: String
}

object TypeDiff {
  final case class Group(diffs: TypeDiff*) extends TypeDiff {
    override def flattenTo0(maxChars: Int, groupLength: Int): (Seq[TypeDiff], Int) = {
      val (xs, length) = diffs.reverse.foldlr(0, (Vector.empty[TypeDiff], 0))((l, x) => l + x.length(groupLength)) { case (l, x, (acc, r)) =>
        val (xs, length) = x.flattenTo0(maxChars - l - r, groupLength)
        (acc ++ xs, length + r)
      }
      if (length <= maxChars.max(groupLength)) (xs, length) else (Seq(Group(xs: _*)), groupLength)
    }

    override protected def length(groupLength: Int): Int = groupLength

    override def text: String = diffs.view.map(_.text).mkString
  }

  final case class Match(override val text: String, tpe: Option[ScType] = None) extends TypeDiff {
    override protected def length(groupLength: Int): Int = text.length
  }

  final case class Mismatch(override val text: String, tpe: Option[ScType] = None) extends TypeDiff {
    override protected def length(groupLength: Int): Int = text.length
  }

  // To display a type hint
  def parse(tpe: ScType)(implicit context: TypePresentationContext): TypeDiff =
    diff(tpe, tpe)((_, _) => true, context)

  // To highlight a type ascription
  def forExpected(expected: ScType, actual: ScType)(implicit context: TypePresentationContext): TypeDiff =
    diff(actual, expected)(_.conforms(_), context)

  // To display a type mismatch hint
  def forActual(expected: ScType, actual: ScType)(implicit context: TypePresentationContext): TypeDiff =
    diff(expected, actual)(reversed(_.conforms(_)), context)

  // To display a type mismatch tooltip
  def forBoth(expected: ScType, actual: ScType)(implicit context: TypePresentationContext): (TypeDiff, TypeDiff) =
    (forExpected(expected, actual), forActual(expected, actual))

  private type Conformance = (ScType, ScType) => Boolean

  // TODO refactor (decompose, unify, etc.)
  private def diff(tpe1: ScType, tpe2: ScType)(implicit conformance: Conformance, context: TypePresentationContext): TypeDiff = {
    def conformanceFor(variance: Variance): Conformance = variance match {
      case Variance.Invariant => (t1: ScType, t2: ScType) => t1.equiv(t2)
      case Variance.Covariant => conformance
      case Variance.Contravariant => reversed
    }

    (tpe1, tpe2) match {
      // TODO Comparison (now, it's just "parsing" for the type annotation hints)
      case (_: ScCompoundType, ScCompoundType(cs2, tms2, tps2)) if tpe1 == tpe2 =>
        val components = (cs2, cs2).zipped.map(diff).intersperse(Match(" with "))
        if (tms2.isEmpty && tps2.isEmpty) Group(components: _*) else {
          val declarations = {
            val members = (tms2.keys.map(_.namedElement) ++ tps2.values.map(_.typeAlias)).toSeq
            members.map(_.getText.takeWhile(_ != '=').trim).sorted.map(s => Group(Match(s)))
          }
          Group(components :+ Match("{") :+ Group(declarations.intersperse(Match("; ")): _*) :+ Match("}"): _*)
        }

      // TODO More flexible comparison, unify with the clause above
      case (ScCompoundType(cs1, EmptyMap(), EmptyMap()), ScCompoundType(cs2, EmptyMap(), EmptyMap())) if cs1.length == cs2.length =>
        Group((cs1, cs2).zipped.map(diff).intersperse(Match(" with ")): _*)

      // TODO Comparison (now, it's just "parsing" for the type annotation hints)
      case (_: ScExistentialType, ScExistentialType(q2: ScParameterizedType, ws2)) if tpe1 == tpe2 =>
        val wildcards = ws2.map { case ScExistentialArgument(_, _, lower, upper) =>
          Group(Match("_") +:
            ((if (lower.isNothing) Seq.empty else Seq(Match(" >: "), diff(lower, lower)(reversed(conformance), context))) ++
              (if (upper.isAny) Seq.empty else Seq(Match(" <: "), diff(upper, upper)))): _*)
        }
        Group(diff(q2.designator, q2.designator), Match("["), Group(wildcards.intersperse(Match(", ")): _*), Match("]"))

      case (InfixType(l1, d1, r1), InfixType(l2, d2, r2)) =>
        val (v1, v2) = d1.extractDesignated(expandAliases = false) match {
          case Some(aClass: ScClass) => aClass.typeParameters match {
            case Seq(p1, p2) => (p1.variance, p2.variance)
            case _ => (Variance.Invariant, Variance.Invariant)
          }
          case _ => (Variance.Invariant, Variance.Invariant)
        }
        Group(diff(l1, l2)(conformanceFor(v1), context), Match(" "), diff(d1, d2), Match(" "), diff(r1, r2)(conformanceFor(v2), context))

      case (TupleType(ts1), TupleType(ts2)) =>
        if (ts1.length == ts2.length) Group(Match("("), Group((ts1, ts2).zipped.map(diff).intersperse(Match(", ")): _*), Match(")"))
        else Group(Mismatch(tpe2.presentableText))

      case (FunctionType(r1, p1), FunctionType(r2, p2)) =>
        val left = {
          if (p1.length == p2.length) {
            val parameters = (p1, p2).zipped.map(diff(_, _)(reversed, context)).intersperse(Match(", "))
            if (p2.length > 1 || p2.exists(FunctionType.isFunctionType)) Seq(Match("("), Group(parameters: _*), Match(")")) else parameters
          } else {
            Seq(Mismatch(if (p2.length == 1) p2.head.presentableText else p2.map(_.presentableText).mkString("(", ", ", ")")))
          }
        }
        val right = diff(r1, r2)
        Group(left :+ Match(" => ") :+ right: _*)

      case (ParameterizedType(d1, args1), ParameterizedType(d2, args2)) =>
        val conformances: Seq[(ScType, ScType) => Boolean] = d1.extractClass match {
          case Some(scalaClass: ScClass) => scalaClass.typeParameters.map(_.variance).map(conformanceFor)
          case _ => Seq.fill(args2.length)((t1: ScType, t2: ScType) => t1.equiv(t2))
        }
        val inner = if (args1.length == args2.length)
          (args1, args2, conformances).zipped.map(diff(_, _)(_, context)).intersperse(Match(", "))
        else
          Seq(Mismatch(args2.map(_.presentableText).mkString(", ")))

        Group(diff(d1, d2), Match("["), Group(inner: _*), Match("]"))

      // On-demand widening of literal types (SCL-15481)
      case (t1, t2: ScLiteralType) if !t1.is[ScLiteralType] => diff(t1, t2.wideType)

      case (t1, t2) =>
        Group(if (conformance(t1, t2)) Match(tpe2.presentableText, Some(tpe2)) else Mismatch(tpe2.presentableText, Some(tpe2)))
    }
  }

  private def reversed(implicit conformance: Conformance): Conformance = (t1: ScType, t2: ScType) => conformance(t2, t1)

  // TODO Move to ParameterizedType.scala / FunctionType.scala?
  private object InfixType {
    def unapply(tpe: ScType): Option[(ScType, ScType, ScType)] = Some(tpe) collect {
      case ParameterizedType(d, Seq(l, r)) if isInfix(d) => (l, d, r)
    }

    private def isInfix(designatorType: ScType) = {
      val designator = designatorType.extractDesignated(expandAliases = false)
      designator.exists(it => ScalaNamesUtil.isOperatorName(it.name)) || designator.exists {
        case aClass: PsiClass => aClass.getAnnotations.map(_.getQualifiedName).contains("scala.annotation.showAsInfix")
        case _ => false
      }
    }
  }

  private object EmptyMap {
    def unapply(map: Map[_, _]): Boolean = map.isEmpty
  }
}