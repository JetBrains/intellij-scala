package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements.ScFunction
import com.intellij.openapi.util.Key
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import api.statements.params.ScParameter
import com.intellij.psi.{NavigatablePsiElement, PsiTypeParameter, PsiNamedElement, PsiClass}
import psi.impl.ScalaPsiManager

class Signature(val name: String, val typesEval: Suspension[Seq[ScType]], val paramLength: Int,
                val typeParams: Array[PsiTypeParameter], val substitutor: ScSubstitutor) {

  def this(name: String, seq: Seq[ScType], paramLength: Int, substitutor: ScSubstitutor) =
    this (name, Suspension.any2Susp(seq), paramLength, Array[PsiTypeParameter](), substitutor)

  def types = typesEval.v

  def equiv(other: Signature): Boolean = {
    name == other.name &&
            typeParams.length == other.typeParams.length &&
            paramTypesEquiv(other)
  }

  protected def paramTypesEquiv(other: Signature): Boolean = {
    if (paramLength != other.paramLength) return false
    val unified1 = unify(substitutor, typeParams, typeParams)
    val unified2 = unify(other.substitutor, typeParams, other.typeParams)
    types.zip(other.types) forall {case (t1, t2) => {unified1.subst(t1) equiv unified2.subst(t2)}}
  }

  protected def unify(subst: ScSubstitutor, tps1: Array[PsiTypeParameter], tps2: Array[PsiTypeParameter]) = {
    var res = subst
    for ((tp1, tp2) <- tps1 zip tps2) {
      res = res bindT (tp2.getName, ScalaPsiManager.typeVariable(tp1))
    }
    res
  }

  override def equals(that: Any) = that match {
    case s: Signature => equiv(s)
    case _ => false
  }

  override def hashCode: Int = {
    name.hashCode * 31 + types.hashCode
  }
}

import com.intellij.psi.PsiMethod
class PhysicalSignature(val method : PsiMethod, override val substitutor : ScSubstitutor)
  extends Signature(method.getName,
                     new Suspension(() => collection.immutable.Sequence(method.getParameterList.getParameters.map({p => p match {
                                                                  case scp : ScParameter => scp.calcType
                                                                  case _ => ScType.create(p.getType, p.getProject)
                                                                }}).toSeq :_*)),
                     method.getParameterList.getParameters.length,
                     method.getTypeParameters,
                     substitutor) {
    override def paramTypesEquiv(other: Signature) = other match {
      case phys1 : PhysicalSignature => {
        val unified1 = unify(substitutor, typeParams, typeParams)
        val unified2 = unify(other.substitutor, typeParams, other.typeParams)
        types.zip(other.types) forall {case (t1, t2) => (unified1.subst(t1), unified2.subst(t2)) match {
          case ((Any | AnyRef), ScDesignatorType(c: PsiClass)) if c.getQualifiedName == "java.lang.Object" => true
          case (ScDesignatorType(c: PsiClass), (Any | AnyRef)) if c.getQualifiedName == "java.lang.Object" => true
          case (t1, t2) => t1 equiv t2
        }
      }
    }
    case _ => super.paramTypesEquiv(other)
  }
}

case class FullSignature(val sig: Signature, val retType: ScType, val element: NavigatablePsiElement, val clazz: PsiClass) {
  override def hashCode: Int = sig.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: FullSignature => sig equals other.sig
    case _ => false
  }
}