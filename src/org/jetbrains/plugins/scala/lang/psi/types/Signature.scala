package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements.ScFunction
import psi.impl.ScalaPsiManager
import com.intellij.psi._
import com.intellij.ide.highlighter.JavaFileType
import util.MethodSignatureUtil
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import api.statements.params.ScParameters

class Signature(val name: String, val typesEval: Stream[ScType], val paramLength: Int,
                val typeParams: Array[PsiTypeParameter], val substitutor: ScSubstitutor,
                val namedElement: Option[PsiNamedElement]) {

  def this(name: String, stream: Stream[ScType], paramLength: Int, substitutor: ScSubstitutor,
           namedElement: Option[PsiNamedElement]) =
    this (name, stream, paramLength, Array[PsiTypeParameter](), substitutor, namedElement)

  def types: scala.Stream[ScType] = typesEval

  def substitutedTypes: Stream[ScType] = ScalaPsiUtil.getTypesStream(types, substitutor.subst _)

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

  def paramTypesEquiv(other: Signature): Boolean = {
    paramTypesEquivExtended(other, new ScUndefinedSubstitutor, true)._1
  }


  // TODO SCL-3518, SCL-3519
  def paramTypesEquivExtended(other: Signature, uSubst: ScUndefinedSubstitutor,
                              falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    var undefSubst = uSubst
    if (paramLength != other.paramLength) return (false, undefSubst)
    if (hasRepeatedParam != other.hasRepeatedParam) return (false, undefSubst)
    val unified1 = unify(substitutor, typeParams, typeParams)
    val unified2 = unify(other.substitutor, typeParams, other.typeParams)
    val typesIterator = substitutedTypes.iterator
    val otherTypesIterator = other.substitutedTypes.iterator
    while (typesIterator.hasNext && otherTypesIterator.hasNext) {
      val t1 = typesIterator.next()
      val t2 = otherTypesIterator.next()
      val t = Equivalence.equivInner(unified2.subst(t2), unified1.subst(t1), undefSubst, falseUndef)
      if (!t._1) return (false, undefSubst)
      undefSubst = t._2
    }
    (true, undefSubst)
  }

  protected def unify(subst: ScSubstitutor, tps1: Array[PsiTypeParameter], tps2: Array[PsiTypeParameter]) = {
    var res = subst
    val iterator1 = tps1.iterator
    val iterator2 = tps2.iterator
    while (iterator1.hasNext && iterator2.hasNext) {
      val (tp1, tp2) = (iterator1.next(), iterator2.next())
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

  def hasRepeatedParam: Boolean = false
}



import com.intellij.psi.PsiMethod
object PhysicalSignature {
  private def typesEval(method: PsiMethod) = method match {
    case fun: ScFunction =>
      ScalaPsiUtil.getTypesStream(fun.effectiveParameterClauses.flatMap(_.parameters))
    case _ => ScalaPsiUtil.getTypesStream(method.getParameterList match {
      case p: ScParameters => p.params
      case p => p.getParameters.toSeq
    })
  }

  private def paramLength(method: PsiMethod) = method match {
    case fun: ScFunction => fun.effectiveParameterClauses.map(_.parameters.length).sum
    case _ => method.getParameterList.getParametersCount
  }
}

class PhysicalSignature(val method: PsiMethod, override val substitutor: ScSubstitutor)
        extends Signature(method.getName, PhysicalSignature.typesEval(method), PhysicalSignature.paramLength(method),
          method.getTypeParameters, substitutor, Some(method)) {

  override def hasRepeatedParam: Boolean = {
    val lastParam = method.getParameterList match {
      case p: ScParameters => p.params.lastOption
      case p => p.getParameters.lastOption
    }
    lastParam match {
      case Some(p: PsiParameter) => p.isVarArgs
      case _ => false
    }
  }

  def updateThisType(thisType: ScType): PhysicalSignature = updateSubst(_.addUpdateThisType(thisType))

  def updateSubst(f: ScSubstitutor => ScSubstitutor): PhysicalSignature = new PhysicalSignature(method, f(substitutor))

  def isJava = method.getLanguage == JavaFileType.INSTANCE.getLanguage
}