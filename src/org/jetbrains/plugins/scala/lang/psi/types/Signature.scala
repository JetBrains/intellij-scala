package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements.ScFunction
import com.intellij.psi._
import com.intellij.ide.highlighter.JavaFileType
import util.MethodSignatureUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import extensions.toPsiNamedElementExt
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.TypeParameter

class Signature(val name: String, val typesEval: List[Stream[ScType]], val paramLength: List[Int],
                val typeParams: Array[TypeParameter], val substitutor: ScSubstitutor,
                val namedElement: Option[PsiNamedElement], val hasRepeatedParam: Seq[Int] = Seq.empty) {

  def this(name: String, stream: Stream[ScType], paramLength: Int, substitutor: ScSubstitutor,
           namedElement: Option[PsiNamedElement]) =
    this(name, List(stream), List(paramLength), Array.empty, substitutor, namedElement)

  private def types: List[Stream[ScType]] = typesEval

  def substitutedTypes: List[Stream[ScType]] = types.map(ScalaPsiUtil.getTypesStream(_, substitutor.subst))

  def equiv(other: Signature): Boolean = {
    def fieldCheck(other: Signature): Boolean = {
      def isField(s: Signature) = s.namedElement.exists(_.isInstanceOf[PsiField])
      !isField(this) ^ isField(other)
    }
    
    ScalaPsiUtil.convertMemberName(name) == ScalaPsiUtil.convertMemberName(other.name) &&
            ((typeParams.length == other.typeParams.length && paramTypesEquiv(other)) || 
              (paramLength == other.paramLength && javaErasedEquiv(other))) && fieldCheck(other)
    
  }

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
    paramTypesEquivExtended(other, new ScUndefinedSubstitutor, falseUndef = true)._1
  }


  def paramTypesEquivExtended(other: Signature, uSubst: ScUndefinedSubstitutor,
                              falseUndef: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    import Signature._

    var undefSubst = uSubst
    if (paramLength != other.paramLength && !(paramLength.sum == 0 && other.paramLength.sum == 0)) return (false, undefSubst)
    if (hasRepeatedParam != other.hasRepeatedParam) return (false, undefSubst)
    if (other.name == "collectNavigationMarkers") {
      "stop here"
    }
    val unified1 = unify(substitutor, typeParams, typeParams)
    val unified2 = unify(other.substitutor, typeParams, other.typeParams)
    val clauseIterator = substitutedTypes.iterator
    val otherClauseIterator = other.substitutedTypes.iterator
    while (clauseIterator.hasNext && otherClauseIterator.hasNext) {
      val clause1 = clauseIterator.next()
      val clause2 = otherClauseIterator.next()
      val typesIterator = clause1.iterator
      val otherTypesIterator = clause2.iterator
      while (typesIterator.hasNext && otherTypesIterator.hasNext) {
        val t1 = typesIterator.next()
        val t2 = otherTypesIterator.next()
        val tp2 = unified2.subst(t2)
        val tp1 = unified1.subst(t1)
        var t = Equivalence.equivInner(tp2, tp1, undefSubst, falseUndef)
        if (!t._1 && tp1.equiv(AnyRef) && this.isJava) {
          t = Equivalence.equivInner(tp2, Any, undefSubst, falseUndef)
        }
        if (!t._1 && tp2.equiv(AnyRef) && other.isJava) {
          t = Equivalence.equivInner(Any, tp1, undefSubst, falseUndef)
        }
        if (!t._1) {
          return (false, undefSubst)
        }
        undefSubst = t._2
      }
    }
    (true, undefSubst)
  }

  override def equals(that: Any) = that match {
    case s: Signature => equiv(s)
    case _ => false
  }

  override def hashCode: Int = {
    ScalaPsiUtil.convertMemberName(name).hashCode * 31
  }

  def isJava: Boolean = false
}

object Signature {
  def unify(subst: ScSubstitutor, tps1: Array[TypeParameter], tps2: Array[TypeParameter]) = {
    var res = subst
    val iterator1 = tps1.iterator
    val iterator2 = tps2.iterator
    while (iterator1.hasNext && iterator2.hasNext) {
      val (tp1, tp2) = (iterator1.next(), iterator2.next())

      def toTypeParameterType(tp: TypeParameter): ScTypeParameterType = {
        new ScTypeParameterType(tp.name, tp.typeParams.map(toTypeParameterType).toList, new Suspension[ScType](tp.lowerType),
          new Suspension[ScType](tp.upperType), tp.ptp)
      }

      res = res bindT ((tp2.name, ScalaPsiUtil.getPsiElementId(tp2.ptp)), toTypeParameterType(tp1))
    }
    res
  }
}



import com.intellij.psi.PsiMethod
object PhysicalSignature {
  def typesEval(method: PsiMethod): List[Stream[ScType]] = method match {
    case fun: ScFunction =>
      fun.effectiveParameterClauses.map(clause => ScalaPsiUtil.getTypesStream(clause.parameters)).toList
    case _ => List(ScalaPsiUtil.getTypesStream(method.getParameterList match {
      case p: ScParameters => p.params
      case p => p.getParameters.toSeq
    }))
  }

  def paramLength(method: PsiMethod): List[Int] = method match {
    case fun: ScFunction => fun.effectiveParameterClauses.map(_.parameters.length).toList
    case _ => List(method.getParameterList.getParametersCount)
  }

  def hasRepeatedParam(method: PsiMethod): Seq[Int] = {
    method.getParameterList match {
      case p: ScParameters =>
        val params = p.params
        val res = new ArrayBuffer[Int]()
        var i = 0
        while (i < params.length) {
          if (params(i).isRepeatedParameter) res += i
          i += 1
        }
        res.toSeq
      case p =>
        val parameters = p.getParameters
        if (parameters.length == 0) return Seq.empty
        if (parameters(parameters.length - 1).isVarArgs) return Seq(parameters.length - 1)
        Seq.empty
    }
  }
}

class PhysicalSignature(val method: PsiMethod, override val substitutor: ScSubstitutor)
        extends Signature(method.name, PhysicalSignature.typesEval(method), PhysicalSignature.paramLength(method),
          method.getTypeParameters.map(new TypeParameter(_)), substitutor, Some(method), PhysicalSignature.hasRepeatedParam(method)) {
  def updateThisType(thisType: ScType): PhysicalSignature = updateSubst(_.addUpdateThisType(thisType))

  def updateSubst(f: ScSubstitutor => ScSubstitutor): PhysicalSignature = new PhysicalSignature(method, f(substitutor))

  override def isJava = method.getLanguage == JavaFileType.INSTANCE.getLanguage
}