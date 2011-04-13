package org.jetbrains.plugins.scala.lang.completion

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import collection.mutable.ArrayBuffer
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi._
import api.statements._
import com.intellij.psi.{ResolveResult, PsiMethod, PsiElement}
import params.ScParameter
import types._
import result.TypingContext
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.patterns.{ElementPatternCondition, ElementPattern, StandardPatterns, PlatformPatterns}

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.09.2009
 */

class ScalaSmartCompletionContributor extends CompletionContributor {
  override def beforeCompletion(context: CompletionInitializationContext) {

  }

  private def acceptTypes(typez: Seq[ScType], variants: Array[Object], result: CompletionResultSet, scope: GlobalSearchScope) {
    if (typez.length == 0 || typez.forall(_ == types.Nothing)) return
    for (variant <- variants) {
      variant match {
        case (el: LookupElement, elem: PsiElement, subst: ScSubstitutor) => {
          def checkType(tp: ScType) {
            val scType = subst.subst(tp)
            import types.Nothing
            if (!scType.equiv(Nothing) && typez.find(scType conforms _) != None) {
              result.addElement(el)
            }
          }
          elem match {
            case fun: ScSyntheticFunction => checkType(fun.retType)
            case fun: ScFunction => checkType(fun.returnType.getOrElse(Any))
            case meth: PsiMethod => checkType(ScType.create(meth.getReturnType, meth.getProject, scope))
            case typed: ScTypedDefinition => for (tt <- typed.getType(TypingContext.empty)) checkType(tt)
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
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      val assign = ref.getParent.asInstanceOf[ScAssignStmt]
      if (assign.getRExpression == Some(ref)) {
        assign.getLExpression match {
          case call: ScMethodCall => //todo: it's update method
          case leftExpression: ScExpression => {
            //we can expect that the type is same for left and right parts.
            acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result, ref.getResolveScope)
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
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result, ref.getResolveScope)
    }
  })

  /*
    return ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScReturnStmt]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      val fun: ScFunction = PsiTreeUtil.getParentOfType(ref, classOf[ScFunction])
      if (fun == null) return
      acceptTypes(Seq[ScType](fun.returnType.getOrElse(Any)), ref.getVariants, result, ref.getResolveScope)
    }
  })

  /*
    call(exprs, ref, exprs)
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScArgumentExprList]),
    new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val referenceExpression = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(referenceExpression.expectedTypes, referenceExpression.getVariants, result, referenceExpression.getResolveScope)
    }
  })

  /*
    if (ref) expr
    if (expr) ref
    if (expr) expr else ref
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScIfStmt]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                         result: CompletionResultSet) {
        val element = parameters.getPosition
        val ref = element.getParent.asInstanceOf[ScReferenceExpression]
        val ifStmt = ref.getParent.asInstanceOf[ScIfStmt]
        if (ifStmt.condition.getOrElse(null: ScExpression) == ref)
          acceptTypes(ref.expectedTypes, ref.getVariants, result, ref.getResolveScope)
        else acceptTypes(ifStmt.expectedTypes, ref.getVariants, result, ref.getResolveScope)
      }
    })

  /*
    expr op ref
    expr ref name
    ref op expr
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScInfixExpr]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val element = parameters.getPosition
        val ref = element.getParent.asInstanceOf[ScReferenceExpression]
        val infix = ref.getParent.asInstanceOf[ScInfixExpr]
        val typez: ArrayBuffer[ScType] = new ArrayBuffer[ScType]
        if (infix.lOp == ref) {
          val op: String = infix.operation.getText
          if (op.endsWith(":")) {
            typez ++= ref.expectedTypes
          }
        } else if (infix.rOp == ref) {
          val op: String = infix.operation.getText
          if (!op.endsWith(":")) {
            typez ++= ref.expectedTypes
          }
        }
        acceptTypes(typez, ref.getVariants, result, ref.getResolveScope)
      }
    })

  /*
    inside try block according to expected type
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScTryBlock]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes, ref.getVariants, result, ref.getResolveScope)
    }
  })

  /*
   inside block expression according to expected type
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScBlockExpr]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes, ref.getVariants, result, ref.getResolveScope)
    }
  })

  /*
   inside finally block
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFinallyBlock]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes, ref.getVariants, result, ref.getResolveScope)
    }
  })

  /*
   inside anonymous function
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionExpr]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result, ref.getResolveScope)
    }
  })

  /*
   for function definitions
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionDefinition]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes, ref.getVariants, result, ref.getResolveScope)
    }
  })

  /*
   for default parameters
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScParameter]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedTypes, ref.getVariants, result, ref.getResolveScope)
    }
  })
}