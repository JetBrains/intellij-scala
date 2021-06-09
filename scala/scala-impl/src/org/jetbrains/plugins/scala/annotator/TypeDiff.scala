package org.jetbrains.plugins.scala.annotator

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.annotator.Tree.{Leaf, Node}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ParameterizedType, TypePresentation, TupleType, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.{ScCompoundType, ScExistentialArgument, ScExistentialType, ScParameterizedType, ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * Can be used to:
 * 1) Parse subtypes (for tooltips, navigation)
 * 2) Parse subgroups (for folding)
 * 3) Detect non-matching elements (for error highlighting)
 * 4) Match elements pairwise (for table-based tooltip)
 * */
// TODO Work in progress (it's not yet clear what's the best way to implement this functionality)
// TODO First parse the trees and then compare them? (but how to balance placeholders?)
sealed trait TypeDiff {
  def text: String
}

object TypeDiff {
  final case class Match(override val text: String, tpe: Option[ScType] = None) extends TypeDiff
  final case class Mismatch(override val text: String, tpe: Option[ScType] = None) extends TypeDiff

  implicit val TypeDiffTooltipFormatter: TooltipTreeFormatter[TypeDiff] = new TooltipTreeFormatter[TypeDiff] {
    override def textOf(element: TypeDiff): String = element.text
    override def isMismatch(element: TypeDiff): Boolean = element.is[Mismatch]
    override def isMissing(element: TypeDiff): Boolean = false
  }

  // To display a type hint
  def parse(tpe: ScType)(implicit context: TypePresentationContext): Tree[TypeDiff] =
    diff(tpe, tpe)((_, _) => true, context)

  // To highlight a type ascription
  def forExpected(expected: ScType, actual: ScType)(implicit context: TypePresentationContext): Tree[TypeDiff] =
    diff(actual, expected)(_.conforms(_), context)

  // To display a type mismatch hint
  def forActual(expected: ScType, actual: ScType)(implicit context: TypePresentationContext): Tree[TypeDiff] =
    diff(expected, actual)(reversed(_.conforms(_)), context)

  // To display a type mismatch tooltip
  def forBoth(expected: ScType, actual: ScType)(implicit context: TypePresentationContext): (Tree[TypeDiff], Tree[TypeDiff]) =
    (forExpected(expected, actual), forActual(expected, actual))

  def lengthOf(nodeLength: Int)(diff: Tree[TypeDiff]): Int = diff match {
    case Node(_ @_*) => nodeLength
    case Leaf(element) => element.text.length
  }

  def asString(diff: Tree[TypeDiff]): String = diff match {
    case Node(elements @_*) => elements.map(asString).mkString
    case Leaf(element) => element.text
  }

  private type Conformance = (ScType, ScType) => Boolean

  // TODO refactor (decompose, unify, etc.)
  private def diff(tpe1: ScType, tpe2: ScType)(implicit conformance: Conformance, context: TypePresentationContext): Tree[TypeDiff] = {
    def conformanceFor(variance: Variance): Conformance = variance match {
      case Variance.Invariant => (t1: ScType, t2: ScType) => t1.equiv(t2)
      case Variance.Covariant => conformance
      case Variance.Contravariant => reversed
    }

    (tpe1, tpe2) match {
      // TODO Comparison (now, it's just "parsing" for the type annotation hints)
      case (_: ScCompoundType, ScCompoundType(cs2, tms2, tps2)) if tpe1 == tpe2 =>
        val components = (cs2 lazyZip cs2).map(diff).intersperse(aMatch(context.compoundTypeSeparatorText))
        if (tms2.isEmpty && tps2.isEmpty) Node(components: _*) else {
          val declarations = {
            val members = (tms2.keys.map(_.namedElement) ++ tps2.values.map(_.typeAlias)).toSeq
            members.map(_.getText.takeWhile(_ != '=').trim).sorted.map(s => Node(aMatch(s)))
          }
          Node(components :+ aMatch("{") :+ Node(declarations.intersperse(aMatch("; ")): _*) :+ aMatch("}"): _*)
        }

      // TODO More flexible comparison, unify with the clause above
      case (ScCompoundType(cs1, EmptyMap(), EmptyMap()), ScCompoundType(cs2, EmptyMap(), EmptyMap())) if cs1.length == cs2.length =>
        Node((cs1 lazyZip cs2).map(diff).intersperse(aMatch(context.compoundTypeSeparatorText)): _*)

      // TODO Comparison (now, it's just "parsing" for the type annotation hints)
      case (_: ScExistentialType, ScExistentialType(q2: ScParameterizedType, ws2)) if tpe1 == tpe2 =>
        val wildcards = ws2.map { case ScExistentialArgument(_, _, lower, upper) =>
          Node(aMatch("_") +:
            ((if (lower.isNothing) Seq.empty else Seq(aMatch(" >: "), diff(lower, lower)(reversed(conformance), context))) ++
              (if (upper.isAny) Seq.empty else Seq(aMatch(" <: "), diff(upper, upper)))): _*)
        }
        Node(diff(q2.designator, q2.designator), aMatch("["), Node(wildcards.intersperse(aMatch(", ")): _*), aMatch("]"))

      case (InfixType(l1, d1, r1), InfixType(l2, d2, r2)) =>
        val (v1, v2) = d1.extractDesignated(expandAliases = false) match {
          case Some(aClass: ScClass) => aClass.typeParameters match {
            case Seq(p1, p2) => (p1.variance, p2.variance)
            case _ => (Variance.Invariant, Variance.Invariant)
          }
          case _ => (Variance.Invariant, Variance.Invariant)
        }
        Node(diff(l1, l2)(conformanceFor(v1), context), aMatch(" "), diff(d1, d2), aMatch(" "), diff(r1, r2)(conformanceFor(v2), context))

      case (TupleType(ts1), TupleType(ts2)) =>
        if (ts1.length == ts2.length) Node(aMatch("("), Node((ts1 lazyZip ts2).map(diff).intersperse(aMatch(", ")): _*), aMatch(")"))
        else Node(aMismatch(tpe2.presentableText))

      case (FunctionType(r1, p1), FunctionType(r2, p2)) =>
        val needsParens = (t: ScType) => FunctionType.isFunctionType(t) || TupleType.isTupleType(t)
        val left = {
          if (p1.length == p2.length) {
            val parameters = (p1 lazyZip p2).map(diff(_, _)(reversed, context)).intersperse(aMatch(", "))
            if (p2.isEmpty) Seq(aMatch("()"))
            else if (p2.length > 1) Seq(aMatch("("), Node(parameters: _*), aMatch(")"))
            else if (p2.exists(needsParens)) Seq(aMatch("("), parameters.head, aMatch(")"))
            else if (p1.exists(needsParens)) Seq(aMatch(""), parameters.head, aMatch(""))
            else parameters
          } else {
            Seq(aMismatch(if (p2.length == 1 && !p2.exists(needsParens)) p2.head.presentableText else p2.map(_.presentableText).mkString("(", ", ", ")")))
          }
        }
        val right = diff(r1, r2)
        Node(left :+ aMatch(" => ") :+ right: _*)

      case (ParameterizedType(d1, args1), ParameterizedType(d2, args2)) =>
        val conformances: Seq[(ScType, ScType) => Boolean] = d1.extractClass match {
          case Some(scalaClass: ScClass) => scalaClass.typeParameters.map(_.variance).map(conformanceFor)
          case _ => Seq.fill(args2.length)((t1: ScType, t2: ScType) => t1.equiv(t2))
        }
        val inner = if (args1.length == args2.length)
          (args1 lazyZip args2 lazyZip conformances).map(diff(_, _)(_, context)).intersperse(aMatch(", "))
        else
          Seq(aMismatch(args2.map(_.presentableText).mkString(", ")))

        Node(diff(d1, d2), aMatch("["), Node(inner: _*), aMatch("]"))

      case (t1, t2) =>
        val text2 = if (t1.equiv(t2)) t2.presentableText else TypePresentation.different(t1, t2)._2
        Node(if (conformance(t1, t2)) aMatch(text2, Some(t2)) else aMismatch(text2, Some(t2)))
    }
  }

  private def aMatch(text: String, tpe: Option[ScType] = None) = Leaf(Match(text, tpe))

  private def aMismatch(text: String, tpe: Option[ScType] = None) = Leaf(Mismatch(text, tpe))

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