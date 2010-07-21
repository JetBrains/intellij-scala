package org.jetbrains.plugins.scala.lang.resolve.processor

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types._
import com.intellij.psi.{PsiNamedElement, PsiMethod, ResolveState, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, StdKinds}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter

/**
 * @author Alexander Podkhalyuzin
 */

class CompoundTypeCheckProcessor(decl: ScNamedElement, undefSubst: ScUndefinedSubstitutor, substitutor: ScSubstitutor)
        extends BaseProcessor(StdKinds.methodRef + ResolveTargets.CLASS) {
  private var trueResult = false

  def getResult: Boolean = trueResult

  private var innerUndefinedSubstitutor = undefSubst

  def getUndefinedSubstitutor = innerUndefinedSubstitutor

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    if (!element.isInstanceOf[PsiNamedElement]) return true
    val namedElement = element.asInstanceOf[PsiNamedElement]
    val subst = getSubst(state)
    if (namedElement.getName != decl.name) return true
    element match {
      case _: ScBindingPattern | _: ScFieldId | _: ScFunction | _: ScParameter if !element.isInstanceOf[ScFunction] ||
        !element.asInstanceOf[ScFunction].hasParameterClause => {
        lazy val bType = subst.subst(element match {
          case b: ScBindingPattern => b.getType(TypingContext.empty).getOrElse(Nothing)
          case f: ScFieldId => f.getType(TypingContext.empty).getOrElse(Nothing)
          case fun: ScFunction => fun.returnType.getOrElse(Nothing)
          case param: ScParameter => param.getType(TypingContext.empty).getOrElse(Nothing)
        })
        val gType = substitutor.subst(decl match {
          case g: ScBindingPattern => g.getType(TypingContext.empty).getOrElse(Any)
          case g: ScFieldId => g.getType(TypingContext.empty).getOrElse(Any)
          case fun: ScFunction if !fun.hasParameterClause => fun.returnType.getOrElse(Any)
          case param: ScParameter => param.getType(TypingContext.empty).getOrElse(Any)
          case _ => return true
        })
        val t = Conformance.conformsInner(gType, bType, Set.empty, undefSubst)
        if (t._1) {
          trueResult = true
          innerUndefinedSubstitutor = t._2
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
          case fun: ScFunction => fun.returnType.getOrElse(Nothing)
          case method: PsiMethod => ScType.create(method.getReturnType, method.getProject, method.getResolveScope)
        })
        val gType = substitutor.subst(decl match {
          case fun: ScFunction => fun.returnType.getOrElse(Nothing)
          case method: PsiMethod => ScType.create(method.getReturnType, method.getProject, method.getResolveScope)
        })
        val t = Conformance.conformsInner(gType, bType, Set.empty, undefSubst)
        if (t._1) {
          trueResult = true
          innerUndefinedSubstitutor = t._2
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