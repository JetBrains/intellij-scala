package org.jetbrains.plugins.scala.annotator

import org.jetbrains.plugins.scala.annotator.Tree.{Leaf, Node}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter

class TypeConstructorDiff(val text: String, val isMismatch: Boolean)

object TypeConstructorDiff {
  type TyConstr = (String, Seq[TypeParameter])

  def forBoth(expected: TyConstr, actual: TyConstr)(implicit tpc: TypePresentationContext): (Tree[TypeConstructorDiff], Tree[TypeConstructorDiff]) =
    (forExpected(expected, actual), forActual(expected, actual))

  def forActual(expected: TyConstr, actual: TyConstr)(implicit tpc: TypePresentationContext): Tree[TypeConstructorDiff] =
    diff(actual._1, actual._2, expected._2)((lower, upper) => lower.conforms(upper), tpc)

  def forExpected(expected: TyConstr, actual: TyConstr)(implicit tpc: TypePresentationContext): Tree[TypeConstructorDiff] =
    diff(expected._1, expected._2, actual._2)((lower, upper) => upper.conforms(lower), tpc)

  private def aMatch(text: String) = Leaf(new TypeConstructorDiff(text, false))
  private def aMismatch(text: String) = Leaf(new TypeConstructorDiff(text, true))

  private type Conformance = (ScType, ScType) => Boolean

  private def diff(subjectName: String, subjectParams: Seq[TypeParameter], otherParams: Seq[TypeParameter])(implicit conformance: Conformance, tpc: TypePresentationContext): Tree[TypeConstructorDiff] = {
    if (subjectParams.isEmpty) {
      Leaf(new TypeConstructorDiff(subjectName, isMismatch = otherParams.nonEmpty))
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
          paramsDiffs.iterator.intersperse(aMatch(", ")) :+
          aMatch("]"): _*
      )
    }
  }

  private def diff(subjectParam: TypeParameter, otherParam: TypeParameter)(implicit conformance: Conformance, tpc: TypePresentationContext): Tree[TypeConstructorDiff] = {
    def listIf[T](cond: Boolean)(elem: T): List[T] = if (cond) List(elem) else Nil

    val subjectLowerType = subjectParam.lowerType
    val lowerBoundLeaf: String => List[Tree[TypeConstructorDiff]] = {
      val otherLowerType = otherParam.lowerType
      val list = listIf[Tree[TypeConstructorDiff]](!subjectLowerType.isNothing || !otherLowerType.isNothing) _
      if (subjectLowerType.isNothing) _ => list(aMatch(""))
      else if (!conformance(subjectLowerType, otherLowerType)) str => list(aMismatch(str))
      else str => list(aMatch(str))
    }

    val subjectUpperType = subjectParam.upperType
    val upperBoundLeaf: String => List[Tree[TypeConstructorDiff]] = {
      val otherUpperType = otherParam.upperType
      val list = listIf[Tree[TypeConstructorDiff]](!subjectUpperType.isAny || !otherUpperType.isAny) _
      if (subjectUpperType.isAny) _ => list(aMatch(""))
      else if (!conformance(otherUpperType, subjectUpperType)) str => list(aMismatch(str))
      else str => list(aMatch(str))
    }

    Node(
      diff(subjectParam.name, subjectParam.typeParameters, otherParam.typeParameters) ::
      lowerBoundLeaf(" >: ") :::
      lowerBoundLeaf(subjectLowerType.presentableText) :::
      upperBoundLeaf(" <: ") :::
      upperBoundLeaf(subjectUpperType.presentableText) :::
      Nil: _*
    )
  }
}
