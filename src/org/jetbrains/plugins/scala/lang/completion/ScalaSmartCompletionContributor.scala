package org.jetbrains.plugins.scala.lang.completion

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import com.intellij.util.ProcessingContext
import com.intellij.codeInsight.completion._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import collection.mutable.ArrayBuffer
import com.intellij.patterns.{ElementPattern, StandardPatterns, PlatformPatterns}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.lang.psi._
import api.statements._
import com.intellij.psi.{ResolveResult, PsiClass, PsiMethod, PsiElement}
import params.ScParameter
import types._
import result.TypingContext

/**
 * User: Alexander Podkhalyuzin
 * Date: 17.09.2009
 */

class ScalaSmartCompletionContributor extends CompletionContributor {
  private def acceptTypes(typez: Seq[ScType], variants: Array[Object], result: CompletionResultSet) {
    if (typez.length == 0 || typez.forall(_ == types.Nothing)) return
    for (variant <- variants) {
      variant match {
        case (el: LookupElement, elem: PsiElement, subst: ScSubstitutor) => {
          def checkType(tp: ScType): Unit = {
            val scType = subst.subst(tp)
            import types.Nothing
            if (!scType.equiv(Nothing) && typez.find(scType conforms _) != None) {
              result.addElement(el)
            }
          }
          elem match {
            case fun: ScSyntheticFunction => checkType(fun.retType)
            case fun: ScFunction => checkType(fun.returnType.getOrElse(Any))
            case meth: PsiMethod => checkType(ScType.create(meth.getReturnType, meth.getProject))
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
                       result: CompletionResultSet): Unit = {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      val assign = ref.getParent.asInstanceOf[ScAssignStmt]
      if (assign.getRExpression == Some(ref)) {
        assign.getLExpression match {
          case call: ScMethodCall => //todo: it's update method
          case leftExpression: ScExpression => {
            //we can expect that the type is same for left and right parts.
            acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result)
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
      acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result)
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
      acceptTypes(Seq[ScType](fun.returnType.getOrElse(Any)), ref.getVariants, result)
    }
  })

  /*
    call(exprs, ref, exprs)
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScArgumentExprList]),
    new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet): Unit = {
      val element = parameters.getPosition
      val referenceExpression = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ExpectedTypes.expectedExprTypes(referenceExpression), referenceExpression.getVariants, result)
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
                         result: CompletionResultSet): Unit = {
        val element = parameters.getPosition
        val ref = element.getParent.asInstanceOf[ScReferenceExpression]
        val ifStmt = ref.getParent.asInstanceOf[ScIfStmt]
        if (ifStmt.condition.getOrElse(null: ScExpression) == ref)
          acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result)
        else acceptTypes(ifStmt.expectedType.toList.toSeq, ref.getVariants, result)
      }
    })

  /*
    expr op ref
    expr ref name
    ref op expr
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScInfixExpr]),
    new CompletionProvider[CompletionParameters] {
      def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
        val element = parameters.getPosition
        val ref = element.getParent.asInstanceOf[ScReferenceExpression]
        val infix = ref.getParent.asInstanceOf[ScInfixExpr]
        val typez: ArrayBuffer[ScType] = new ArrayBuffer[ScType]
        def attachTypes: Unit = {
          val results: Array[ResolveResult] = infix.operation.multiResolve(false)
          for (result <- results) {
            val scalaResult: ScalaResolveResult = result.asInstanceOf[ScalaResolveResult]
            scalaResult match {
              case ScalaResolveResult(fun: ScFun, subst: ScSubstitutor) => {
                if (fun.paramTypes.length == 1) typez += subst.subst(fun.paramTypes.apply(0))
              }
              case ScalaResolveResult(fun: PsiMethod, subst: ScSubstitutor) => {
                fun match {
                  case fun: ScFunction => {
                    if (fun.paramClauses.clauses.length > 0 && fun.paramClauses.clauses.apply(0).
                            parameters.length == 1) {
                      typez += subst.subst(fun.paramClauses.clauses.apply(0).paramTypes.apply(0))
                    }
                  }
                  case method: PsiMethod => {
                    if (method.getParameterList.getParametersCount == 1) {
                      typez += subst.subst(ScType.create(method.getParameterList.getParameters.apply(0).getType,
                        method.getProject))
                    }
                  }
                }
              }
              case _ =>
            }
          }
        }
        if (infix.lOp == ref) {
          val op: String = infix.operation.getText
          if (op.endsWith(":")) {
            attachTypes
          } else {
            for (rOpType <- infix.rOp.getType(TypingContext.empty)) {
              val compoundType = ScCompoundType(Seq.empty, Seq.empty, Seq.empty)
              compoundType.signatureMap += Tuple2(new Signature(op, Seq[ScType](rOpType), 1, ScSubstitutor.empty),
                types.Any)
              typez += compoundType
            }
          }
        } else if (infix.rOp == ref) {
          val op: String = infix.operation.getText
          if (op.endsWith(":")) {
            for (lOpType <- infix.lOp.getType(TypingContext.empty)) {
              val compoundType = ScCompoundType(Seq.empty, Seq.empty, Seq.empty)
              compoundType.signatureMap += Tuple(new Signature(op, Seq[ScType](lOpType), 1, ScSubstitutor.empty),
                types.Any)
              typez += compoundType
            }
          } else {
            attachTypes
          }
        } else {
          //operation: nothing to do
        }
        acceptTypes(typez.toArray[ScType], ref.getVariants, result)
      }
    })

  /*
    inside try block according to expected type
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScTryBlock]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet): Unit = {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result)
    }
  })

  /*
   inside block expression according to expected type
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScBlockExpr]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet): Unit = {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result)
    }
  })

  /*
   inside finally block
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFinallyBlock]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext,
                       result: CompletionResultSet): Unit = {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result)
    }
  })

  /*
   inside anonymous function
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionExpr]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result)
    }
  })

  /*
   for function definitions
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScFunctionDefinition]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result)
    }
  })

  /*
   for default parameters
   */
  extend(CompletionType.SMART, superParentPattern(classOf[ScParameter]), new CompletionProvider[CompletionParameters] {
    def addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet): Unit = {
      val element = parameters.getPosition
      val ref = element.getParent.asInstanceOf[ScReferenceExpression]
      acceptTypes(ref.expectedType.toList.toSeq, ref.getVariants, result)
    }
  })
}