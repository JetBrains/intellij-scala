package org.jetbrains.plugins.scala.lang.psi.types

import api.statements.{ScFunction, ScFunctionDeclaration}
import com.intellij.openapi.util.Key
import collection.immutable.{Map, HashMap}
import com.intellij.openapi.project.Project
import api.statements.params.ScParameter
import com.intellij.psi.{PsiTypeParameter, PsiClass, PsiType, PsiModifier}
import psi.impl.ScalaPsiManager

class Signature(val name : String, val types : Seq[ScType], val retType : ScType,
                val typeParams : Array[PsiTypeParameter], val substitutor : ScSubstitutor,
                val isAbstract : Boolean, val isScala : Boolean) {

  def this(name : String, paramTypes : Seq[ScType], retType : ScType, s : ScSubstitutor) =
    this(name, paramTypes, retType, Array(), s, false, true)

  def equiv(other : Signature) : Boolean = {
    name == other.name &&
    typeParams.length == other.typeParams.length &&
    paramTypesEquiv(other)
  }

  def paramTypesEquiv(other: Signature) = {
    val unified = unify(other.substitutor, typeParams, other.typeParams)
    types.equalsWith(other.types) {
      (t1, t2) => (t1, t2) match {
        case ((Any | AnyRef), _) if !other.isScala => t2 match {
          case ScDesignatorType(clazz : PsiClass) if clazz.getQualifiedName == "java.lang.Object" => true
          case _ => false
        }
        case (_, (Any | AnyRef)) if !isScala => t1 match {
          case ScDesignatorType(clazz : PsiClass) if clazz.getQualifiedName == "java.lang.Object" => true
          case _ => false
        }
        case _ => substitutor.subst(t1) equiv unified.subst(t2)
      }
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
object PhysicalSignature {
  def create(method : PsiMethod, substitutor : ScSubstitutor) = {
    val paramTypes = method.getParameterList.getParameters.map {p => p match {
      case scp : ScParameter => scp.calcType
      case _ => ScType.create(p.getType, p.getProject)
    }}
    val retType = method match {
      case f : ScFunction => f.calcType match {case ScFunctionType(ret, _) => ret case _ => Nothing}
      case _ => {
        val psiRet = method.getReturnType
        if (psiRet == null) Unit else ScType.create(psiRet, method.getProject)
      }
    }
    val isAbstract = method match {
      case _ : ScFunctionDeclaration => true
      case m if m.hasModifierProperty(PsiModifier.ABSTRACT) => true
      case _ => false
    }
    val isScala = method.getLanguage == ScalaFileType.SCALA_LANGUAGE
    new PhysicalSignature(method, paramTypes, retType, substitutor, isAbstract, isScala)
  }
}

class PhysicalSignature(val method : PsiMethod, types : Seq[ScType], retType : ScType,
                        substitutor : ScSubstitutor, isAbstract: Boolean, isScala : Boolean)
  extends Signature (method.getName, types, retType, method.getTypeParameters, substitutor, isScala, isAbstract)