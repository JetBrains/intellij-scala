package org.jetbrains.plugins.scala.lang.resolve.processor

import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, StdKinds}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScTypeParam, ScParameter}
import com.intellij.psi._

/**
 * @author Alexander Podkhalyuzin
 */

class CompoundTypeCheckProcessor(decl: ScNamedElement, undefSubst: ScUndefinedSubstitutor, substitutor: ScSubstitutor)
        extends BaseProcessor(StdKinds.methodRef + ResolveTargets.CLASS) {
  private val typeParameters: Seq[ScTypeParam] = decl match {
    case o: ScTypeParametersOwner => o.typeParameters
    case _ => Seq.empty
  }

  private var trueResult = false

  def getResult: Boolean = trueResult

  private var innerUndefinedSubstitutor = undefSubst

  def getUndefinedSubstitutor = innerUndefinedSubstitutor

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    if (!element.isInstanceOf[PsiNamedElement]) return true
    val namedElement = element.asInstanceOf[PsiNamedElement]
    val subst = getSubst(state)
    if (namedElement.getName != decl.name) return true

    var undef = undefSubst

    def checkTypeParameters(tp1: PsiTypeParameter, tp2: ScTypeParam, variance: Int = 1): Boolean = {
      tp1 match {
        case tp1: ScTypeParam =>
          if (tp1.typeParameters.length != tp2.typeParameters.length) return false
          val iter = tp1.typeParameters.zip(tp2.typeParameters).iterator
          while (iter.hasNext) {
            val (tp1, tp2) = iter.next()
            if (!checkTypeParameters(tp1, tp2, -variance)) return false
          }
          //lower type
          val lower1 = tp1.lowerBound.getOrNothing
          val lower2 = substitutor.subst(tp2.lowerBound.getOrNothing)
          var t = Conformance.conformsInner(
            if (variance == 1) lower2
            else lower1,
            if (variance == 1) lower1
            else lower2, Set.empty, undef)
          if (!t._1) return false
          undef = t._2

          val upper1 = tp1.upperBound.getOrAny
          val upper2 = substitutor.subst(tp2.upperBound.getOrAny)
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
          if (tp2.typeParameters.length > 0) return false
          //todo: check bounds?
          true
      }
    }

    //let's check type parameters
    element match {
      case o: ScTypeParametersOwner =>
        if (o.typeParameters.length != typeParameters.length) return true
        val iter = o.typeParameters.zip(typeParameters).iterator
        while (iter.hasNext) {
          val (tp1, tp2) = iter.next()
          if (!checkTypeParameters(tp1, tp2)) return true
        }
      case p: PsiTypeParameterListOwner =>
        if (p.getTypeParameters.length != typeParameters.length) return true
        val iter = p.getTypeParameters.toSeq.zip(typeParameters).iterator
        while (iter.hasNext) {
          val (tp1, tp2) = iter.next()
          if (!checkTypeParameters(tp1, tp2)) return true
        }
      case _ => if (typeParameters.length > 0) return true
    }

    element match {
      case _: ScBindingPattern | _: ScFieldId | _: ScFunction | _: ScParameter if !element.isInstanceOf[ScFunction] ||
        !element.asInstanceOf[ScFunction].hasParameterClause => {
        lazy val bType = subst.subst(element match {
          case b: ScBindingPattern => b.getType(TypingContext.empty).getOrNothing
          case f: ScFieldId => f.getType(TypingContext.empty).getOrNothing
          case fun: ScFunction => fun.returnType.getOrNothing
          case param: ScParameter => param.getType(TypingContext.empty).getOrNothing
        })
        val gType = substitutor.subst(decl match {
          case g: ScBindingPattern => g.getType(TypingContext.empty).getOrAny
          case g: ScFieldId => g.getType(TypingContext.empty).getOrAny
          case fun: ScFunction if !fun.hasParameterClause => fun.returnType.getOrAny
          case param: ScParameter => param.getType(TypingContext.empty).getOrAny
          case _ => return true
        })
        val t = Conformance.conformsInner(gType, bType, Set.empty, undef)
        if (t._1) {
          trueResult = true
          undef = t._2
          innerUndefinedSubstitutor = undef
          return false
        }
      }
      case method: PsiMethod => {
        val anotherMethod = decl match {
          case fun: ScFunction if !fun.hasParameterClause => return true
          case meth: PsiMethod => meth
          case _ => return true
        }
        val sign1 = new PhysicalSignature(method, subst)
        val sign2 = new PhysicalSignature(decl.asInstanceOf[PsiMethod], substitutor)
        if (!sign1.paramTypesEquiv(sign2)) return false
        val bType = subst.subst(method match {
          case fun: ScFunction => fun.returnType.getOrNothing
          case method: PsiMethod => ScType.create(method.getReturnType, method.getProject, method.getResolveScope)
        })
        val gType = substitutor.subst(decl match {
          case fun: ScFunction => fun.returnType.getOrNothing
          case method: PsiMethod => ScType.create(method.getReturnType, method.getProject, method.getResolveScope)
        })
        val t = Conformance.conformsInner(gType, bType, Set.empty, undef)
        if (t._1) {
          trueResult = true
          undef = t._2
          innerUndefinedSubstitutor = undef
          return false
        }
      }
      case tp: ScTypeAlias => {
        trueResult = true
        return false //todo: we don't know anything about this case, now we can match it only by name
      }
      case _ =>
    }
    true
  }
}