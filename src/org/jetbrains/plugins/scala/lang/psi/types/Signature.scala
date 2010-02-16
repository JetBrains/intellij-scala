package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements.ScFunction
import com.intellij.openapi.util.Key
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import api.statements.params.ScParameter
import psi.impl.ScalaPsiManager
import result.TypingContext
import com.intellij.psi._

class Signature(val name: String, val typesEval: Suspension[Seq[ScType]], val paramLength: Int,
                val typeParams: Array[PsiTypeParameter], val substitutor: ScSubstitutor) {

  def this(name: String, seq: Seq[ScType], paramLength: Int, substitutor: ScSubstitutor) =
    this (name, Suspension.any2Susp(seq), paramLength, Array[PsiTypeParameter](), substitutor)

  def types = typesEval.v

  def substitutedTypes = types.map(substitutor.subst(_))

  def equiv(other: Signature): Boolean = {
    name == other.name &&
            typeParams.length == other.typeParams.length &&
            paramTypesEquiv(other)
  }

  protected def paramTypesEquiv(other: Signature): Boolean = {
    if (paramLength != other.paramLength) return false
    val unified1 = unify(substitutor, typeParams, typeParams)
    val unified2 = unify(other.substitutor, typeParams, other.typeParams)
    val typesIterator = types.iterator
    val otherTypesIterator = other.types.iterator
    while (typesIterator.hasNext && otherTypesIterator.hasNext) {
      val t1 = typesIterator.next
      val t2 = otherTypesIterator.next
      if (!unified1.subst(t1).equiv(unified2.subst(t2))) return false
    }
    return true
  }

  protected def unify(subst: ScSubstitutor, tps1: Array[PsiTypeParameter], tps2: Array[PsiTypeParameter]) = {
    var res = subst
    val iterator1 = tps1.iterator
    val iterator2 = tps2.iterator
    while (iterator1.hasNext && iterator2.hasNext) {
      val (tp1, tp2) = (iterator1.next, iterator2.next)
      res = res bindT (tp2.getName, ScalaPsiManager.typeVariable(tp1))
    }
    res
  }

  override def equals(that: Any) = that match {
    case s: Signature => equiv(s)
    case _ => false
  }

  override def hashCode: Int = {
    name.hashCode * 31
  }
}

import com.intellij.psi.PsiMethod
class PhysicalSignature(val method : PsiMethod, override val substitutor : ScSubstitutor)
  extends Signature(method.getName,
                     new Suspension(() => collection.immutable.Seq(method.getParameterList.getParameters.map({p => p match {
                                                                  case scp : ScParameter => scp.getType(TypingContext.empty).getOrElse(Nothing)
                                                                  case _ => ScType.create(p.getType, p.getProject)
                                                                }}).toSeq :_*)),
                     method.getParameterList.getParameters.length,
                     method.getTypeParameters,
                     substitutor) {
    def hasRepeatedParam: Boolean = {
      method.getParameterList.getParameters.lastOption match {
        case Some(p: PsiParameter) => p.isVarArgs
        case _ => false
      }
    }

    override def paramTypesEquiv(other: Signature): Boolean = {
      other match {
        case phys1: PhysicalSignature => {
          if (phys1.paramLength != paramLength) return false
          if ((phys1.hasRepeatedParam && !hasRepeatedParam) ||
                  (!phys1.hasRepeatedParam && hasRepeatedParam)) return false
          val unified1 = unify(substitutor, typeParams, typeParams)
          val unified2 = unify(other.substitutor, typeParams, other.typeParams)
          val otherTypesIterator = other.types.iterator
          val typesIterator = types.iterator
          while (typesIterator.hasNext && otherTypesIterator.hasNext) {
            val t1 = typesIterator.next
            val t2 = otherTypesIterator.next
            if (!unified1.subst(t1).equiv(unified2.subst(t2))) return false
          }
          return true
        }
        case _ if !hasRepeatedParam => super.paramTypesEquiv(other)
        case _ => false
      }
    }
}

case class FullSignature(val sig: Signature, val retType: ScType, val element: NavigatablePsiElement, val clazz: PsiClass) {
  override def hashCode: Int = sig.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: FullSignature => sig equals other.sig
    case _ => false
  }
}