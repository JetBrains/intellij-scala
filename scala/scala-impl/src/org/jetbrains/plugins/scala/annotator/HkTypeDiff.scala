package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.Tree.{Leaf, Node}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter

class HkTypeDiff(val text: String, val isMismatch: Boolean)

object HkTypeDiff {
  type HkTy = (String, Seq[TypeParameter])

  def forBoth(expected: HkTy, actual: HkTy)(implicit tpc: TypePresentationContext): (Tree[HkTypeDiff], Tree[HkTypeDiff]) =
    (forExpected(expected, actual), forActual(expected, actual))

  def forActual(expected: HkTy, actual: HkTy)(implicit tpc: TypePresentationContext): Tree[HkTypeDiff] =
    diff(actual._1, actual._2, expected._2)((lower, upper) => lower.conforms(upper), tpc)

  def forExpected(expected: HkTy, actual: HkTy)(implicit tpc: TypePresentationContext): Tree[HkTypeDiff] =
    diff(expected._1, expected._2, actual._2)((lower, upper) => upper.conforms(lower), tpc)

  private def aMatch(text: String) = Leaf(new HkTypeDiff(text, true))
  private def aMismatch(text: String) = Leaf(new HkTypeDiff(text, false))

  private type Conformance = (ScType, ScType) => Boolean

  private def diff(subjectName: String, subjectParams: Seq[TypeParameter], otherParams: Seq[TypeParameter])(implicit conformance: Conformance, tpc: TypePresentationContext): Tree[HkTypeDiff] = {
    if (subjectParams.isEmpty) {
      Leaf(new HkTypeDiff(subjectName, isMismatch = otherParams.nonEmpty))
    } else if (subjectParams.size != otherParams.size) {
      val paramText = subjectParams.map(_.name).mkString(", ")
      aMismatch(s"$subjectName[$paramText]")
    } else {
      val paramsDiffs = subjectParams.lazyZip(otherParams).map(diff)
      Node(
        Seq(
          aMatch(subjectName),
          aMatch("[")
        ) ++
        paramsDiffs :+
        aMatch("]"): _*
      )
    }
  }

  private def diff(subjectParam: TypeParameter, otherParam: TypeParameter)(implicit conformance: Conformance, tpc: TypePresentationContext): Tree[HkTypeDiff] = {
    val lowerType = subjectParam.lowerType
    val lowerBoundLeaf: String => Seq[Tree[HkTypeDiff]] =
      if (!conformance(otherParam.lowerType, lowerType)) str => Seq(aMismatch(str))
      else if (lowerType.isNothing) _ => Seq()
      else str => Seq(aMatch(str))

    val upperType = subjectParam.upperType
    val upperBoundLeaf: String => Seq[Tree[HkTypeDiff]] =
      if (!conformance(upperType, otherParam.upperType)) str => Seq(aMismatch(str))
      else if (upperType.isAny) _ => Seq()
      else str => Seq(aMatch(str))

    Node(
      diff(subjectParam.name, subjectParam.typeParameters, otherParam.typeParameters) +:
      (lowerBoundLeaf(" >: ") ++
      lowerBoundLeaf(lowerType.presentableText) ++
      upperBoundLeaf(" <: ") ++
      upperBoundLeaf(upperType.presentableText)): _*
    )
  }
}
