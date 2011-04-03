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
import com.intellij.ide.highlighter.JavaFileType
import util.{MethodSignatureUtil}

class Signature(val name: String, val typesEval: Stream[ScType], val paramLength: Int,
                val typeParams: Array[PsiTypeParameter], val substitutor: ScSubstitutor) {

  def this(name: String, stream: Stream[ScType], paramLength: Int, substitutor: ScSubstitutor) =
    this (name, stream, paramLength, Array[PsiTypeParameter](), substitutor)

  def types: scala.Stream[ScType] = typesEval

  def substitutedTypes: Stream[ScType] = types.map(substitutor.subst(_))

  def equiv(other: Signature): Boolean = {
    name == other.name &&
            ((typeParams.length == other.typeParams.length && paramTypesEquiv(other)) || (paramLength == other.paramLength && javaErasedEquiv(other)))
  }

  // This is a quick fix for SCL-2973.
  // TODO Handle this properly
  def javaErasedEquiv(other: Signature): Boolean = {
    (this, other) match {
      case (ps1: PhysicalSignature, ps2: PhysicalSignature) if ps1.isJava && ps2.isJava =>
        val psiSub1 = ScalaPsiUtil.getPsiSubstitutor(ps1.substitutor, ps1.method.getProject, ps1.method.getResolveScope)
        val psiSub2 = ScalaPsiUtil.getPsiSubstitutor(ps2.substitutor, ps2.method.getProject, ps2.method.getResolveScope)
        val psiSig1 = ps1.method.getSignature(psiSub1)
        val psiSig2 = ps2.method.getSignature(psiSub2)
        MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY.equals(psiSig1, psiSig2)
      case _ => false
    }
  }

  protected def paramTypesEquiv(other: Signature): Boolean = {
    if (paramLength != other.paramLength) return false
    val unified1 = unify(substitutor, typeParams, typeParams)
    val unified2 = unify(other.substitutor, typeParams, other.typeParams)
    val typesIterator = substitutedTypes.iterator
    val otherTypesIterator = other.substitutedTypes.iterator
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
      res = res bindT ((tp2.getName, ScalaPsiUtil.getPsiElementId(tp2)), ScalaPsiManager.typeVariable(tp1))
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
class PhysicalSignature(val method : PsiMethod, override val substitutor: ScSubstitutor)
        extends Signature(method.getName, method match {
          case fun: ScFunction => ScalaPsiUtil.getTypesStream(fun.parameters)
          case _ => ScalaPsiUtil.getTypesStream(method.getParameterList.getParameters.toSeq)
        },
          method.getParameterList.getParameters.length,
          method.getTypeParameters,
          substitutor) {
  def hasRepeatedParam: Boolean = {
    method.getParameterList.getParameters.lastOption match {
      case Some(p: PsiParameter) => p.isVarArgs
      case _ => false
    }
  }

  def updateThisType(thisType: ScType): PhysicalSignature = updateSubst(_.addUpdateThisType(thisType))

  def updateSubst(f: ScSubstitutor => ScSubstitutor): PhysicalSignature = new PhysicalSignature(method, f(substitutor))

  override def paramTypesEquiv (other: Signature): Boolean = {
    other match {
      case phys1: PhysicalSignature => {
        if (phys1.paramLength != paramLength) return false
        if ((phys1.hasRepeatedParam && !hasRepeatedParam) ||
                (!phys1.hasRepeatedParam && hasRepeatedParam)) return false
        val unified1 = unify(substitutor, typeParams, typeParams)
        val unified2 = unify(other.substitutor, typeParams, other.typeParams)
        val otherTypesIterator = other.substitutedTypes.iterator
        val typesIterator = substitutedTypes.iterator
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

  def isJava = method.getLanguage == JavaFileType.INSTANCE.getLanguage
}

case class FullSignature(sig: Signature, retType: Suspension[ScType], element: NavigatablePsiElement, clazz: Option[PsiClass]) {
  override def hashCode: Int = sig.hashCode

  override def equals(obj: Any): Boolean = obj match {
    case other: FullSignature => sig equals other.sig
    case _ => false
  }
}