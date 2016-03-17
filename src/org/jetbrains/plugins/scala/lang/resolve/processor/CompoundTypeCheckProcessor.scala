package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, StdKinds}

/**
 * @author Alexander Podkhalyuzin
 */

class CompoundTypeCheckSignatureProcessor(s: Signature, retType: ScType,
                                 undefSubst: ScUndefinedSubstitutor, substitutor: ScSubstitutor)
  extends BaseProcessor(StdKinds.methodRef + ResolveTargets.CLASS)(ScalaTypeSystem) {

  private val name = s.name

  private var trueResult = false

  def getResult: Boolean = trueResult

  private var innerUndefinedSubstitutor = undefSubst

  def getUndefinedSubstitutor = innerUndefinedSubstitutor

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    if (!element.isInstanceOf[PsiNamedElement]) return true
    val namedElement = element.asInstanceOf[PsiNamedElement]
    val subst = getSubst(state)
    if (namedElement.name != name) return true

    var undef = undefSubst

    def checkTypeParameters(tp1: PsiTypeParameter, tp2: TypeParameter, variance: Int = 1): Boolean = {
      tp1 match {
        case tp1: ScTypeParam =>
          if (tp1.typeParameters.length != tp2.typeParams.length) return false
          val iter = tp1.typeParameters.zip(tp2.typeParams).iterator
          while (iter.hasNext) {
            val (tp1, tp2) = iter.next()
            if (!checkTypeParameters(tp1, tp2, -variance)) return false
          }
          //lower type
          val lower1 = tp1.lowerBound.getOrNothing
          val lower2 = substitutor.subst(tp2.lowerType())
          var t = Conformance.conformsInner(
            if (variance == 1) lower2
            else lower1,
            if (variance == 1) lower1
            else lower2, Set.empty, undef)
          if (!t._1) return false
          undef = t._2

          val upper1 = tp1.upperBound.getOrAny
          val upper2 = substitutor.subst(tp2.upperType())
          t = Conformance.conformsInner(
            if (variance == 1) upper1
            else upper2,
            if (variance == 1) upper2
            else upper1, Set.empty, undef)
          if (!t._1) return false
          undef = t._2

          //todo: view?
          true
        case _ =>
          if (tp2.typeParams.nonEmpty) return false
          //todo: check bounds?
          true
      }
    }

    //let's check type parameters
    element match {
      case o: ScTypeParametersOwner =>
        if (o.typeParameters.length != s.typeParams.length) return true
        val iter = o.typeParameters.zip(s.typeParams).iterator
        while (iter.hasNext) {
          val (tp1, tp2) = iter.next()
          if (!checkTypeParameters(tp1, tp2)) return true
        }
      case p: PsiTypeParameterListOwner =>
        if (p.getTypeParameters.length != s.typeParams.length) return true
        val iter = p.getTypeParameters.toSeq.zip(s.typeParams).iterator
        while (iter.hasNext) {
          val (tp1, tp2) = iter.next()
          if (!checkTypeParameters(tp1, tp2)) return true
        }
      case _ => if (s.typeParams.length > 0) return true
    }

    def checkSignature(sign1: Signature, typeParams: Array[PsiTypeParameter], returnType: ScType): Boolean = {
      import org.jetbrains.plugins.scala.lang.psi.types.Signature.unify

      val sign2 = s

      if (!sign1.parameterlessCompatible(sign2)) return true

      var t = sign1.paramTypesEquivExtended(sign2, undef, falseUndef = false)
      if (!t._1) return true
      undef = t._2
      innerUndefinedSubstitutor = undef

      val typeParams = sign1.typeParams
      val otherTypeParams = s.typeParams
      val unified1 = unify(subst, typeParams, typeParams)
      val unified2 = unify(substitutor, typeParams, otherTypeParams)

      val bType = unified1.subst(subst.subst(returnType))
      val gType = unified2.subst(substitutor.subst(retType))
      t = Conformance.conformsInner(gType, bType, Set.empty, undef)
      if (t._1) {
        trueResult = true
        undef = t._2
        innerUndefinedSubstitutor = undef
        return false
      }
      true
    }

    element match {
      case _: ScBindingPattern | _: ScFieldId | _: ScParameter =>
        val rt = subst.subst(element match {
          case b: ScBindingPattern => b.getType(TypingContext.empty).getOrNothing
          case f: ScFieldId => f.getType(TypingContext.empty).getOrNothing
          case param: ScParameter => param.getType(TypingContext.empty).getOrNothing
        })
        val dcl: ScTypedDefinition = element.asInstanceOf[ScTypedDefinition]
        val isVar = dcl.isVar
        if (!checkSignature(new Signature(dcl.name, Seq.empty, 0, subst, dcl), Array.empty, rt)) return false
        if (isVar && !checkSignature(new Signature(dcl.name + "_=", Seq(() => rt), 1, subst, dcl),
          Array.empty, Unit)) return false
      case method: PsiMethod =>
        val sign1 = new PhysicalSignature(method, subst)
        if (!checkSignature(sign1, method.getTypeParameters, method match {
          case fun: ScFunction => fun.returnType.getOrNothing
          case method: PsiMethod => ScType.create(method.getReturnType, method.getProject, method.getResolveScope)
        })) return false
      case _ =>
    }
    true
  }
}

class CompoundTypeCheckTypeAliasProcessor(sign: TypeAliasSignature, undefSubst: ScUndefinedSubstitutor, substitutor: ScSubstitutor)
  extends BaseProcessor(StdKinds.methodRef + ResolveTargets.CLASS)(ScalaTypeSystem) {
  private val name = sign.name

  private var trueResult = false

  def getResult: Boolean = trueResult

  private var innerUndefinedSubstitutor = undefSubst

  def getUndefinedSubstitutor = innerUndefinedSubstitutor

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    if (!element.isInstanceOf[PsiNamedElement]) return true
    val namedElement = element.asInstanceOf[PsiNamedElement]
    val subst = getSubst(state)
    if (namedElement.name != name) return true

    var undef = undefSubst

    def checkTypeParameters(tp1: PsiTypeParameter, tp2: TypeParameter, variance: Int = 1): Boolean = {
      tp1 match {
        case tp1: ScTypeParam =>
          if (tp1.typeParameters.length != tp2.typeParams.length) return false
          val iter = tp1.typeParameters.zip(tp2.typeParams).iterator
          while (iter.hasNext) {
            val (tp1, tp2) = iter.next()
            if (!checkTypeParameters(tp1, tp2, -variance)) return false
          }
          //lower type
          val lower1 = tp1.lowerBound.getOrNothing
          val lower2 = substitutor.subst(tp2.lowerType())
          var t = Conformance.conformsInner(
            if (variance == 1) lower2
            else lower1,
            if (variance == 1) lower1
            else lower2, Set.empty, undef)
          if (!t._1) return false
          undef = t._2

          val upper1 = tp1.upperBound.getOrAny
          val upper2 = substitutor.subst(tp2.upperType())
          t = Conformance.conformsInner(
            if (variance == 1) upper1
            else upper2,
            if (variance == 1) upper2
            else upper1, Set.empty, undef)
          if (!t._1) return false
          undef = t._2

          //todo: view?
          true
        case _ =>
          if (tp2.typeParams.length > 0) return false
          //todo: check bounds?
          true
      }
    }

    //let's check type parameters
    element match {
      case o: ScTypeParametersOwner =>
        if (o.typeParameters.length != sign.typeParams.length) return true
        val iter = o.typeParameters.zip(sign.typeParams).iterator
        while (iter.hasNext) {
          val (tp1, tp2) = iter.next()
          if (!checkTypeParameters(tp1, tp2)) return true
        }
      case p: PsiTypeParameterListOwner =>
        if (p.getTypeParameters.length != sign.typeParams.length) return true
        val iter = p.getTypeParameters.toSeq.zip(sign.typeParams).iterator
        while (iter.hasNext) {
          val (tp1, tp2) = iter.next()
          if (!checkTypeParameters(tp1, tp2)) return true
        }
      case _ => if (sign.typeParams.length > 0) return true
    }

    def checkDeclarationForTypeAlias(tp: ScTypeAlias): Boolean = {
      sign.ta match {
        case _: ScTypeAliasDeclaration =>
          var t = Conformance.conformsInner(subst.subst(tp.lowerBound.getOrNothing),
            substitutor.subst(sign.lowerBound), Set.empty, undef)
          if (t._1) {
            t = Conformance.conformsInner(substitutor.subst(sign.upperBound),
              subst.subst(tp.upperBound.getOrAny), Set.empty, t._2)
            if (t._1) {
              trueResult = true
              undef = t._2
              innerUndefinedSubstitutor = undef
              return true
            }
          }
        case _ =>
      }
      false
    }

    element match {
      case tp: ScTypeAliasDefinition =>
        sign.ta match {
          case _: ScTypeAliasDefinition =>
            val t = subst.subst(tp.aliasedType.getOrNothing).equiv(substitutor.subst(sign.lowerBound), undef, falseUndef = false)
            if (t._1) {
              undef = t._2
              trueResult = true
              innerUndefinedSubstitutor = undef
              return false
            }
          case _: ScTypeAliasDeclaration => if (checkDeclarationForTypeAlias(tp)) return false
          case _ =>
        }
      case tp: ScTypeAliasDeclaration => if (checkDeclarationForTypeAlias(tp)) return false
      case _ =>
    }
    true
  }
}