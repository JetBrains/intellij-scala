package org.jetbrains.plugins.scala.lang.psi.implicits

import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.psi._
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.StubBasedExt
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeParameterType, TypeSystem}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.types.{ScSubstitutor, ScType}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.processor.{BaseProcessor, ImplicitProcessor}
import org.jetbrains.plugins.scala.lang.resolve.{ResolveUtils, ScalaResolveResult, StdKinds}

/**
  * @author adkozlov
  */
class CollectImplicitsProcessor(expression: ScExpression, withoutPrecedence: Boolean)
                               (implicit override val typeSystem: TypeSystem)
  extends ImplicitProcessor(StdKinds.refExprLastRef, withoutPrecedence) {

  protected def getPlace: PsiElement = expression

  def execute(element: PsiElement, state: ResolveState): Boolean = {
    val functionType = expression.elementScope.function1Type(level = 0)

    lazy val subst: ScSubstitutor = state.get(BaseProcessor.FROM_TYPE_KEY) match {
      case null => getSubst(state)
      case tp => getSubst(state).followUpdateThisType(tp)
    }

    import CollectImplicitsProcessor._
    element match {
      case named: PsiNamedElement if kindMatches(element) => named match {
        //there is special case for Predef.conforms method
        case f: ScFunction if f.hasModifierProperty("implicit") && !isConformsMethod(f) =>
          if (!checkFucntionIsEligible(f, getPlace) ||
            !ResolveUtils.isAccessible(f, getPlace)) return true
          val clauses = f.paramClauses.clauses
          //filtered cases
          if (clauses.length > 2) return true
          if (clauses.length == 2) {
            if (!clauses(1).isImplicit) {
              return true
            }
            if (f.hasTypeParameters) {
              val typeParameters = f.typeParameters
              for {
                param <- clauses(1).parameters
                paramType <- param.getType(TypingContext.empty)
              } {
                var hasTypeParametersInType = false
                paramType.recursiveUpdate {
                  case tp@TypeParameterType(_, _, _, _) if typeParameters.contains(tp.name) =>
                    hasTypeParametersInType = true
                    (true, tp)
                  case tp: ScType if hasTypeParametersInType => (true, tp)
                  case tp: ScType => (false, tp)
                }
                if (hasTypeParametersInType) return true //looks like it's not working in compiler 2.10, so it's faster to avoid it
              }
            }
          }
          if (clauses.isEmpty) {
            val rt = subst.subst(f.returnType.getOrElse(return true))
            if (functionType.exists(!rt.conforms(_))) return true
          } else if (clauses.head.parameters.length != 1 || clauses.head.isImplicit) return true
          addResult(new ScalaResolveResult(f, subst, getImports(state)))
        case b: ScBindingPattern =>
          ScalaPsiUtil.nameContext(b) match {
            case d: ScDeclaredElementsHolder if (d.isInstanceOf[ScValue] || d.isInstanceOf[ScVariable]) &&
              d.asInstanceOf[ScModifierListOwner].hasModifierProperty("implicit") =>
              if (!ResolveUtils.isAccessible(d.asInstanceOf[ScMember], getPlace)) return true
              val tp = subst.subst(b.getType(TypingContext.empty).getOrElse(return true))
              if (functionType.exists(!tp.conforms(_))) return true
              addResult(new ScalaResolveResult(b, subst, getImports(state)))
            case _ => return true
          }
        case param: ScParameter if param.isImplicitParameter =>
          param match {
            case c: ScClassParameter =>
              if (!ResolveUtils.isAccessible(c, getPlace)) return true
            case _ =>
          }
          val tp = subst.subst(param.getType(TypingContext.empty).getOrElse(return true))
          if (functionType.exists(!tp.conforms(_))) return true
          addResult(new ScalaResolveResult(param, subst, getImports(state)))
        case obj: ScObject if obj.hasModifierProperty("implicit") =>
          if (!ResolveUtils.isAccessible(obj, getPlace)) return true
          val tp = subst.subst(obj.getType(TypingContext.empty).getOrElse(return true))
          if (functionType.exists(!tp.conforms(_))) return true
          addResult(new ScalaResolveResult(obj, subst, getImports(state)))
        case _ =>
      }
      case _ =>
    }
    true
  }
}


object CollectImplicitsProcessor {
  private def isConformsMethod(f: ScFunction): Boolean = {
    (f.name == "conforms" || f.name == "$conforms") &&
      Option(f.containingClass).flatMap(cls => Option(cls.qualifiedName)).contains("scala.Predef")
  }

  def checkFucntionIsEligible(function: ScFunction, expression: PsiElement): Boolean = {
    if (!function.hasExplicitType) {
      if (PsiTreeUtil.isContextAncestor(function.getContainingFile, expression, false)) {
        val commonContext = PsiTreeUtil.findCommonContext(function, expression)
        if (expression == commonContext) return true //weird case, it covers situation, when function comes from object, not treeWalkUp
        if (function == commonContext) return false
        else {
          var functionContext: PsiElement = function
          while (functionContext.getContext != commonContext) functionContext = functionContext.getContext
          var placeContext: PsiElement = expression
          while (placeContext.getContext != commonContext) placeContext = placeContext.getContext
          (functionContext, placeContext) match {
            case (functionContext: ScalaPsiElement, placeContext: ScalaPsiElement) =>
              val funElem = functionContext.getDeepSameElementInContext
              val conElem = placeContext.getDeepSameElementInContext
              val children = commonContext.stubOrPsiChildren(TokenSet.ANY, PsiElement.ARRAY_FACTORY)

              children.find(elem => elem == funElem || elem == conElem) match {
                case Some(elem) if elem == conElem => return false
                case _ =>
              }
            case _ =>
          }
        }
      }
    }
    true
  }
}

