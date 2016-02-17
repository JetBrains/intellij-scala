package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.psi._
import com.intellij.psi.util.MethodSignatureUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameters
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.TypeParameter

import scala.collection.mutable.ArrayBuffer

case class TypeAliasSignature(name: String, typeParams: List[TypeParameter], lowerBound: ScType,
                              upperBound: ScType, isDefinition: Boolean, ta: ScTypeAlias) {
  def this(ta: ScTypeAlias) {
    this(ta.name, ta.typeParameters.map(new TypeParameter(_)).toList, ta.lowerBound.getOrNothing,
      ta.upperBound.getOrAny, ta.isInstanceOf[ScTypeAliasDefinition], ta)
  }

  def updateTypes(fun: ScType => ScType, withCopy: Boolean = true): TypeAliasSignature = {
    def updateTypeParam(tp: TypeParameter): TypeParameter = {
      new TypeParameter(tp.name, tp.typeParams.map(updateTypeParam), {
        val res = fun(tp.lowerType())
        () => res
      }, {
        val res = fun(tp.upperType())
        () => res
      }, tp.ptp)
    }
    val res = TypeAliasSignature(name, typeParams.map(updateTypeParam), fun(lowerBound), fun(upperBound), isDefinition, ta)

    if (withCopy) res.copy(ta = ScTypeAlias.getCompoundCopy(res, ta))
    else res
  }

  def updateTypesWithVariance(fun: (ScType, Int) => ScType, variance: Int, withCopy: Boolean = true): TypeAliasSignature = {
    def updateTypeParam(tp: TypeParameter): TypeParameter = {
      new TypeParameter(tp.name, tp.typeParams.map(updateTypeParam), () => fun(tp.lowerType(), variance),
        () => fun(tp.upperType(), -variance), tp.ptp)
    }
    val res = TypeAliasSignature(name, typeParams.map(updateTypeParam), fun(lowerBound, variance),
      fun(upperBound, -variance), isDefinition, ta)

    if (withCopy) res.copy(ta = ScTypeAlias.getCompoundCopy(res, ta))
    else res
  }

  def canEqual(other: Any): Boolean = other.isInstanceOf[TypeAliasSignature]

  override def equals(other: Any): Boolean = other match {
    case that: TypeAliasSignature =>
      (that canEqual this) &&
        name == that.name &&
        typeParams == that.typeParams &&
        lowerBound == that.lowerBound &&
        upperBound == that.upperBound &&
        isDefinition == that.isDefinition
    case _ => false
  }

  override def hashCode(): Int = {
    val state = Seq(name, typeParams, lowerBound, upperBound, isDefinition)
    state.map(_.hashCode()).foldLeft(0)((a, b) => 31 * a + b)
  }
}

class Signature(val name: String, private val typesEval: List[Seq[() => ScType]], val paramLength: List[Int],
                private val tParams: Array[TypeParameter], val substitutor: ScSubstitutor,
                val namedElement: PsiNamedElement, val hasRepeatedParam: Seq[Int] = Seq.empty) {

  def this(name: String, stream: Seq[() => ScType], paramLength: Int, substitutor: ScSubstitutor,
           namedElement: PsiNamedElement) =
    this(name, List(stream), List(paramLength), Array.empty, substitutor, namedElement)

  private def types: List[Seq[() => ScType]] = typesEval

  def substitutedTypes: List[Seq[() => ScType]] = types.map(_.map(f => () => substitutor.subst(f())))

  def typeParams: Array[TypeParameter] = tParams.map(_.update(substitutor.subst))

  def equiv(other: Signature): Boolean = {
    def fieldCheck(other: Signature): Boolean = {
      def isField(s: Signature) = s.namedElement.isInstanceOf[PsiField]
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
    import org.jetbrains.plugins.scala.lang.psi.types.Signature._

    var undefSubst = uSubst
    if (paramLength != other.paramLength && !(paramLength.sum == 0 && other.paramLength.sum == 0)) return (false, undefSubst)
    if (hasRepeatedParam != other.hasRepeatedParam) return (false, undefSubst)
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
        val tp2 = unified2.subst(t2())
        val tp1 = unified1.subst(t1())
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
    case s: Signature => equiv(s) && parameterlessKind == s.parameterlessKind
    case _ => false
  }

  def parameterlessKind: Int = {
    namedElement match {
      case f: ScFunction if !f.hasParameterClause => 1
      case p: PsiMethod => 2
      case _ => 3
    }
  }

  override def hashCode: Int = {
    simpleHashCode * 31 + parameterlessKind
  }

  /**
   * Use it, while building class hierarchy.
   * Because for class hierarch def foo(): Int is the same thing as def foo: Int and val foo: Int.
   */
  def simpleHashCode: Int = {
    ScalaPsiUtil.convertMemberName(name).hashCode
  }

  def isJava: Boolean = false

  def parameterlessCompatible(other: Signature): Boolean = {
    (namedElement, other.namedElement) match {
      case (f1: ScFunction, f2: ScFunction) =>
        !f1.hasParameterClause ^ f2.hasParameterClause
      case (f1: ScFunction, p: PsiMethod) => f1.hasParameterClause
      case (p: PsiMethod, f2: ScFunction) => f2.hasParameterClause
      case (p1: PsiMethod, p2: PsiMethod) => true
      case (p: PsiMethod, _) => false
      case (_, f: ScFunction)  => !f.hasParameterClause
      case (_, f: PsiMethod) => false
      case _ => true
    }
  }
}

object Signature {
  def apply(function: ScFunction) = new Signature(
    function.name,
    PhysicalSignature.typesEval(function),
    PhysicalSignature.paramLength(function),
    TypeParameter.fromArray(function.getTypeParameters),
    ScSubstitutor.empty,
    function,
    PhysicalSignature.hasRepeatedParam(function)
  )

  def getter(definition: ScTypedDefinition) = new Signature(
    definition.name,
    Seq.empty,
    0,
    ScSubstitutor.empty,
    definition
  )

  def setter(definition: ScTypedDefinition) = new Signature(
    s"$definition.name_=",
    Seq(() => definition.getType().getOrAny),
    1,
    ScSubstitutor.empty,
    definition
  )

  def unify(subst: ScSubstitutor, tps1: Array[TypeParameter], tps2: Array[TypeParameter]) = {
    var res = subst
    val iterator1 = tps1.iterator
    val iterator2 = tps2.iterator
    while (iterator1.hasNext && iterator2.hasNext) {
      val (tp1, tp2) = (iterator1.next(), iterator2.next())

      res = res bindT ((tp2.name, ScalaPsiUtil.getPsiElementId(tp2.ptp)), ScTypeParameterType.toTypeParameterType(tp1))
    }
    res
  }
}



import com.intellij.psi.PsiMethod
object PhysicalSignature {
  def typesEval(method: PsiMethod): List[Seq[() => ScType]] = method match {
    case fun: ScFunction =>
      fun.effectiveParameterClauses.map(clause => ScalaPsiUtil.mapToLazyTypesSeq(clause.effectiveParameters)).toList
    case _ => List(ScalaPsiUtil.mapToLazyTypesSeq(method.getParameterList match {
      case p: ScParameters => p.params
      case p => p.getParameters.toSeq
    }))
  }

  def paramLength(method: PsiMethod): List[Int] = method match {
    case fun: ScFunction => fun.effectiveParameterClauses.map(_.effectiveParameters.length).toList
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

  def unapply(signature: PhysicalSignature): Option[(PsiMethod, ScSubstitutor)] = {
    Some(signature.method, signature.substitutor)
  }
}

class PhysicalSignature(val method: PsiMethod, override val substitutor: ScSubstitutor)
        extends Signature(method.name, PhysicalSignature.typesEval(method), PhysicalSignature.paramLength(method),
          TypeParameter.fromArray(method.getTypeParameters), substitutor, method, PhysicalSignature.hasRepeatedParam(method)) {
  override def isJava = method.getLanguage == JavaFileType.INSTANCE.getLanguage
}