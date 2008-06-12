/**
* @author ven
*/
package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.openapi.util.Key
import com.intellij.psi.{PsiTypeParameter, PsiSubstitutor}
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

object ScSubstitutor {
  val empty = new ScSubstitutor

  val key : Key[ScSubstitutor] = Key.create("scala substitutor key")

  def create (psiSubst : PsiSubstitutor, project : Project) : ScSubstitutor = {
    var substitutor = empty
    val it = psiSubst.getSubstitutionMap.entrySet.iterator
    while (it.hasNext) {
      val entry = it.next
      val tp = entry.getKey
      val t = entry.getValue
      if (t == null) {
        substitutor = substitutor + (tp, ScExistentialType.unbounded)
      } else {
        substitutor = substitutor + (tp, ScType.create(t, project))
      }
    }
    substitutor
  }
}

class ScSubstitutor(val map : Map[PsiTypeParameter, ScType]) {

  def this() = {
    this(Map.empty)
  }

  def +(p : PsiTypeParameter, t : ScType) = new ScSubstitutor(map + ((p, t)))

  def subst(p : PsiTypeParameter) = {
    map.get(p) match {
      case None => new ScDesignatorType(p)
      case Some(v) => v
    }
  }

  def subst (t : ScType) : ScType = {
    t match {
      case ScFunctionType(ret, params) => new ScFunctionType(subst(ret), params map (subst _))
      case ScParameterizedType (td, s) => td match {
        case tp : PsiTypeParameter => subst(tp)
        case _ => {
          val newMap = map transform ((tp : PsiTypeParameter, t : ScType) => subst(s.subst(t)))
          new ScParameterizedType(td, new ScSubstitutor(newMap))
        }
      }
      case _ => t //todo
    }
  }
}

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
      res = res + (tp2, new ScDesignatorType(tp1))
    }
    res
  }
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

