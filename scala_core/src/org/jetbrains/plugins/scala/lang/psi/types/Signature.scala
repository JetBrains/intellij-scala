package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Key
import com.intellij.psi.PsiTypeParameter
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import psi.impl.ScalaPsiManager

class Signature(val name : String, val types : Seq[ScType],
                val typeParams : Array[PsiTypeParameter], val substitutor : ScSubstitutor) {
  def equiv(other : Signature) : Boolean = {
    name == other.name &&
    typeParams.length == other.typeParams.length &&
    types.equalsWith(other.types) {(t1, t2) => {
      val unified = unify(other.substitutor, typeParams, other.typeParams)
      substitutor.subst(t1) equiv unified.subst(t2)}
    }
  }

  private def unify(subst : ScSubstitutor, tps1 : Array[PsiTypeParameter], tps2 : Array[PsiTypeParameter]) = {
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
                     substitutor)
