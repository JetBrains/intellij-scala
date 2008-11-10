package org.jetbrains.plugins.scala.lang.psi.types

import api.statements.ScFunction
import com.intellij.openapi.util.Key
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import api.statements.params.ScParameter
import com.intellij.psi.{NavigatablePsiElement, PsiTypeParameter, PsiNamedElement, PsiClass}
import psi.impl.ScalaPsiManager

class Signature(val name : String, val types : Seq[ScType],
                val typeParams : Array[PsiTypeParameter], val substitutor : ScSubstitutor) {
  def equiv(other : Signature) : Boolean = {
    name == other.name &&
    typeParams.length == other.typeParams.length &&
    paramTypesEquiv(other)
  }

  protected def paramTypesEquiv(other : Signature) = {
    val unified1 = unify(substitutor, typeParams, typeParams)
    val unified2 = unify(other.substitutor, typeParams, other.typeParams)
    types.equalsWith(other.types) {(t1, t2) => { unified1.subst(t1) equiv unified2.subst(t2) }}
  }

  protected def unify(subst : ScSubstitutor, tps1 : Array[PsiTypeParameter], tps2 : Array[PsiTypeParameter]) = {
    var res = subst
    for ((tp1, tp2) <- tps1 zip tps2) {
      res = res bindT (tp2.getName, ScalaPsiManager.typeVariable(tp1))
    }
    res
  }

  override def equals(that : Any) = that match {
    case s : Signature => equiv(s)
    case _ => false
  }

  override def hashCode = name.hashCode * 31 + types.length
}

import com.intellij.psi.PsiMethod
class PhysicalSignature(val method : PsiMethod, override val substitutor : ScSubstitutor)
  extends Signature (method.getName,
                     method.getParameterList.getParameters.map {p => p match {
                                                                  case scp : ScParameter => scp.calcType
                                                                  case _ => ScType.create(p.getType, p.getProject)
                                                                }},
                     method.getTypeParameters,
                     substitutor) {
    override def paramTypesEquiv(other: Signature) = other match {
      case phys1 : PhysicalSignature => {
        val unified1 = unify(substitutor, typeParams, typeParams)
        val unified2 = unify(other.substitutor, typeParams, other.typeParams)
        val scala1 = method match {case _ : ScFunction => true; case _ => false}
        val scala2 = phys1.method match {case _ : ScFunction => true; case _ => false}
        types.equalsWith(other.types) {(t1, t2) => (t1, t2) match {
          case ((Any | AnyRef), _) if !scala2 => t2 match {
            case ScDesignatorType(c : PsiClass) if c.getQualifiedName == "java.lang.Object" => true
            case _ => false
          }
          case (_, (Any | AnyRef)) if !scala1 => t1 match {
            case ScDesignatorType(c : PsiClass) if c.getQualifiedName == "java.lang.Object" => true
            case _ => false
          }
          case _ => unified1.subst(t1) equiv unified2.subst(t2)
        }
      }}
      case _ => super.paramTypesEquiv(other)
    }
  }

case class FullSignature(val sig : Signature, val retType : ScType, val element : NavigatablePsiElement, val clazz : PsiClass) {
  override def hashCode: Int = sig.hashCode
  override def equals(obj: Any): Boolean = obj match {
    case other : FullSignature => sig equals other.sig
    case _ => false
  }
}