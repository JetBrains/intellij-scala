package org.jetbrains.plugins.scala.lang.psi.types

import api.statements.ScFunction
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiTypeParameter
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import api.statements.params.ScParameter
import psi.impl.ScalaPsiManager

class Signature(val name : String, val types : Seq[ScType],
                val typeParams : Array[PsiTypeParameter], val substitutor : ScSubstitutor) {
  def equiv(other : Signature) : Boolean = {
    name == other.name &&
    typeParams.length == other.typeParams.length &&
    paramTypesEquiv(other)
  }

  protected def paramTypesEquiv(other : Signature) = {
    val unified = unify(other.substitutor, typeParams, other.typeParams)
    types.equalsWith(other.types) {(t1, t2) => { substitutor.subst(t1) equiv unified.subst(t2) }}
  }

  protected def unify(subst : ScSubstitutor, tps1 : Array[PsiTypeParameter], tps2 : Array[PsiTypeParameter]) = {
    var res = subst
    for ((tp1, tp2) <- tps1 zip tps2) {
      val manager = ScalaPsiManager.instance(tp1.getProject)
      res = res + (manager.typeVariable(tp2), manager.typeVariable(tp1))
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
        val unified = unify(other.substitutor, typeParams, other.typeParams)
        val scala1 = method match {case _ : ScFunction => true; case _ => false}
        val scala2 = phys1.method match {case _ : ScFunction => true; case _ => false}
        types.equalsWith(other.types) {(t1, t2) => (t1, t2) match {
          case ((Any | AnyRef), _) if !scala2 => ScDesignatorType.getClassType("java.lang.Object", method) match {
            case Some(des) => des equiv unified.subst(t2)
            case None => false
          }
          case (_, (Any | AnyRef)) if !scala1 => ScDesignatorType.getClassType("java.lang.Object", method) match {
            case Some(des) => substitutor.subst(t1) equiv des
            case None => false
          }
          case _ => substitutor.subst(t1) equiv unified.subst(t2)
        }
      }}
      case _ => super.paramTypesEquiv(other)
    }
  }
