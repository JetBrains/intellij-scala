package org.jetbrains.plugins.scala.lang.completion

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.completion._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTyped
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScVariableDefinition, ScPatternDefinition, ScFunction}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import com.intellij.psi.{PsiClass, PsiMethod, PsiElement}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.psi.types.{ScFunctionType, ScSubstitutor, Nothing, ScType}
import com.intellij.patterns.{ElementPattern, StandardPatterns, PlatformPatterns}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.psi.util.PsiTreeUtil

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.09.2009
 */

class ScalaSmartCompletionContributor extends CompletionContributor {
  private def acceptTypes(typez: Seq[ScType], variants: Array[Object], result: CompletionResultSet) {
    if (typez.length == 0 || typez.forall(_ == Nothing)) return
    for (variant <- variants) {
      variant match {
        case (el: LookupElement, elem: PsiElement, subst: ScSubstitutor) => {
          def checkType(tp: ScType): Unit = {
            if (subst.subst(tp) != Nothing &&
                    typez.find(subst.subst(tp) conforms _) != None) result.addElement(el)
          }
          elem match {
            case fun: ScSyntheticFunction => checkType(fun.retType)
            case fun: ScFunction => checkType(fun.returnType)
            case meth: PsiMethod => checkType(ScType.create(meth.getReturnType, meth.getProject))
            case typed: ScTyped => checkType(typed.calcType)
            case _ =>
          }
        }
        case _ =>
      }
    }
  }

  private def superParentPattern(clazz: java.lang.Class[_ <: PsiElement]): ElementPattern[PsiElement] = {
    PlatformPatterns.psiElement(ScalaTokenTypes.tIDENTIFIER).withParent(classOf[ScReferenceExpression]).
          withSuperParent(2, clazz)
  }

  /*
    ref = expr
    expr = ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScAssignStmt]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet): Unit = {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      val assign = ref.getParent.asInstanceOf[ScAssignStmt]
      if (assign.getRExpression == Some(ref)) {
        assign.getLExpression match {
          case call: ScMethodCall => //todo: it's update method
          case leftExpression: ScExpression => {
            //we can expect that the type is same for left and right parts.
            acceptTypes(Seq[ScType](leftExpression.cachedType), ref.getVariants, result)
          }
        }
      } else { //so it's left expression
        //todo: if right expression exists?
      }
    }
  })

  /*
    val x: Type = ref
    var y: Type = ref
   */
  extend(CompletionType.SMART, StandardPatterns.or[PsiElement](superParentPattern(classOf[ScPatternDefinition]),
    superParentPattern(classOf[ScVariableDefinition])), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet): Unit = {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      ref.getParent match {
        case patternDefinition: ScPatternDefinition => acceptTypes(Seq[ScType](patternDefinition.getType),
          ref.getVariants, result)
        case variableDefinition: ScVariableDefinition => acceptTypes(Seq[ScType](variableDefinition.getType),
          ref.getVariants, result)
      }
    }
  })

  /*
    return ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScReturnStmt]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      val fun: ScFunction = PsiTreeUtil.getParentOfType(ref, classOf[ScFunction])
      if (fun == null) return
      acceptTypes(Seq[ScType](fun.returnType), ref.getVariants, result)
    }
  })

  /*
    call(exprs, ref, exprs)
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScArgumentExprList]),
    new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet): Unit = {
      val typez: ArrayBuffer[ScType] = new ArrayBuffer[ScType]
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      val args = ref.getParent.asInstanceOf[ScArgumentExprList]
      val index = args.exprs.findIndexOf(_ == ref)
      if (index == -1) return
      val nameCallFromParameter = args.nameCallFromParameter
      if (nameCallFromParameter != -1 && nameCallFromParameter <= index) return //todo: we can complete parameter names
      args.callReference match {
        case Some(ref: ScReferenceElement) => {
          val variants = ref.multiResolve(false)
          val invocationCount = args.invocationCount
          for (variant <- variants) {
            variant match {
              case ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor) => {
                method match {
                  case fun: ScSyntheticFunction => if (invocationCount == 1 && index < fun.paramTypes.length)
                    typez += subst.subst(fun.paramTypes.apply(index))
                  case fun: ScFunction => {
                    val clauses = fun.paramClauses.clauses
                    if (invocationCount <= clauses.length) {
                      val types = clauses.apply(invocationCount - 1).paramTypes
                      if (index < types.length)
                        typez += subst.subst(types.apply(index))
                    }
                  }
                  case method: PsiMethod => {
                    if (invocationCount == 1 && index < method.getParameterList.getParameters.length)
                      typez += subst.subst(ScType.create(method.getParameterList.getParameters.apply(index).
                              getTypeElement.getType, method.getProject))
                  }
                }
              }
              case _ => //todo: another options
            }
          }
        }
        case None =>
      }
      acceptTypes(typez.toArray, ref.getVariants, result)
    }
  })
}