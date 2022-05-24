package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.Tree.{Leaf, Node}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}

class TypeConstructorDiff(val text: String, val isMismatch: Boolean, val isMissing: Boolean) {
  def hasError: Boolean = isMismatch || isMissing
}

object TypeConstructorDiff {
  implicit val TypeDiffTooltipFormatter: TooltipTreeFormatter[TypeConstructorDiff] = new TooltipTreeFormatter[TypeConstructorDiff] {
    override def textOf(element: TypeConstructorDiff): String = element.text
    override def isMismatch(element: TypeConstructorDiff): Boolean = element.isMismatch
    override def isMissing(element: TypeConstructorDiff): Boolean = element.isMissing
  }

  type TyConstr = (String, Seq[TypeParameter])

  def forActual(expected: TyConstr, actual: TyConstr, substitute: ScSubstitutor)(implicit tpc: TypePresentationContext): Tree[TypeConstructorDiff] =
    diff(actual._1, actual._2, expected._2, substitute)((lower, upper) => lower.conforms(upper), tpc)

  def forExpected(expected: TyConstr, actual: TyConstr, substitute: ScSubstitutor)(implicit tpc: TypePresentationContext): Tree[TypeConstructorDiff] =
    diff(expected._1, expected._2, actual._2, substitute)((lower, upper) => upper.conforms(lower), tpc)

  private def aMatch(text: String) = Leaf(new TypeConstructorDiff(text, false, false))
  private def aMismatch(text: String) = Leaf(new TypeConstructorDiff(text, true, false))
  private def aMissing(text: String) = Leaf(new TypeConstructorDiff(text, false, true))

  private type Conformance = (ScType, ScType) => Boolean

  private def diff(subjectName: String, subjectParams: Seq[TypeParameter], otherParams: Seq[TypeParameter], substitute: ScSubstitutor)(implicit conformance: Conformance, tpc: TypePresentationContext): Tree[TypeConstructorDiff] = {
    if (subjectParams.isEmpty && otherParams.isEmpty) {
      aMatch(subjectName)
    } else if (subjectParams.size > otherParams.size) {
      Node(
        Seq(
          aMatch(subjectName),
          aMatch("[")
        ) ++
          subjectParams.zipWithIndex.iterator
            .map {
              case (ty, idx) if idx < otherParams.size => aMatch(ty.name)
              case (ty, _) => aMissing(ty.name)
            }
            .intersperse(aMatch(", ")) :+
          aMatch("]"): _*
      )
    } else if (subjectParams.size < otherParams.size) {
      val paramDiff = otherParams.size - subjectParams.size
      val hasParams = subjectParams.nonEmpty
      Node(
        Seq(
          aMatch(subjectName),
          aMatch(if (hasParams) "[" else "")
        ) ++
          subjectParams.map(ty => aMatch(ty.name)).intersperse(aMatch(", ")) ++
          (if (hasParams) Seq(aMatch("]")) else Seq.empty) ++
          Iterator.fill(paramDiff)(Seq(aMissing(" "), aMatch(""))).flatten: _*
      )
    } else {
      val newSubstitutor = substitute.withBindings(subjectParams, otherParams)
      val paramsDiffs = subjectParams.lazyZip(otherParams).map(diff(_, _, newSubstitutor))
      Node(
        Seq(
          aMatch(subjectName),
          aMatch("[")
        ) ++
          paramsDiffs.iterator.intersperse(aMatch(", ")) :+
          aMatch("]"): _*
      )
    }
  }

  private def diff(subjectParam: TypeParameter, otherParam: TypeParameter, substitute: ScSubstitutor)(implicit conformance: Conformance, tpc: TypePresentationContext): Tree[TypeConstructorDiff] = {
    def listIf[T](cond: Boolean)(elem: T): List[T] = if (cond) List(elem) else Nil

    val subjectLowerType = subjectParam.lowerType
    val lowerBoundLeaf: String => List[Tree[TypeConstructorDiff]] = {
      val otherLowerType = otherParam.lowerType
      val list = listIf[Tree[TypeConstructorDiff]](!subjectLowerType.isNothing || !otherLowerType.isNothing) _
      if (subjectLowerType.isNothing) _ => list(aMatch(""))
      else if (!conformance(substitute(subjectLowerType), substitute(otherLowerType))) str => list(aMismatch(str))
      else str => list(aMatch(str))
    }

    val subjectUpperType = subjectParam.upperType
    val upperBoundLeaf: String => List[Tree[TypeConstructorDiff]] = {
      val otherUpperType = otherParam.upperType
      val list = listIf[Tree[TypeConstructorDiff]](!subjectUpperType.isAny || !otherUpperType.isAny) _
      if (subjectUpperType.isAny) _ => list(aMatch(""))
      else if (!conformance(substitute(otherUpperType), substitute(subjectUpperType))) str => list(aMismatch(str))
      else str => list(aMatch(str))
    }

    Node(
      diff(subjectParam.name, subjectParam.typeParameters, otherParam.typeParameters, substitute) ::
        lowerBoundLeaf(" >: ") :::
        lowerBoundLeaf(subjectLowerType.presentableText) :::
        upperBoundLeaf(" <: ") :::
        upperBoundLeaf(subjectUpperType.presentableText) :::
        Nil: _*
    )
  }
}
