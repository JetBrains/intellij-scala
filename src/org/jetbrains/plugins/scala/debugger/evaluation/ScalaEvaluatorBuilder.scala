package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import evaluator._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.debugger.engine.evaluation._
import com.intellij.psi._
import expression._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameterClause, ScParameter, ScClassParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariable, ScValue}
import reflect.NameTransformer
import com.intellij.psi.util.PsiTreeUtil
import collection.mutable.{HashSet, ArrayBuffer}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScEarlyDefinitions}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaRecursiveElementVisitor, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScStableCodeReferenceElement, ScLiteral, ScReferenceElement}
import com.intellij.debugger.engine.{JVMName, JVMNameUtil}
import util.DebuggerUtil
import collection.Seq
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTrait, ScObject}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScParameterizedType, ScThisType, ScType}

/**
 * User: Alefas
 * Date: 11.10.11
 */

object ScalaEvaluatorBuilder extends EvaluatorBuilder {
  def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    new Builder(position).buildElement(codeFragment)
  }
  
  private class Builder(position: SourcePosition) extends ScalaElementVisitor {
    private var myResult: Evaluator = null
    private var myCurrentFragmentEvaluator: CodeFragmentEvaluator = null
    private var myContextClass: PsiElement = null

    private def getContextClass: PsiElement = myContextClass

    private def isGenerateClass(elem: PsiElement): Boolean = {
      elem match {
        case clazz: PsiClass => true
        case f: ScFunctionExpr => true
        case e: ScExpression if ScUnderScoreSectionUtil.underscores(e).length > 0 => true
        case _ => false
      }
    }
    
    private def getContainingClass(elem: PsiElement): PsiElement = {
      var element = elem.getParent
      while (element != null && !isGenerateClass(element)) element = element.getParent
      if (element == null) getContextClass else element
    }

    private def getContextClass(elem: PsiElement): PsiElement = {
      var element = elem.getContext
      while (element != null && !isGenerateClass(element)) element = element.getContext
      element
    }

    override def visitFile(file: PsiFile) {
      if (!file.isInstanceOf[ScalaCodeFragment]) return
      val oldCurrentFragmentEvaluator = myCurrentFragmentEvaluator
      myCurrentFragmentEvaluator = new CodeFragmentEvaluator(oldCurrentFragmentEvaluator)
      val evaluators = new ArrayBuffer[Evaluator]()
      var child = file.getFirstChild
      while (child != null) {
        child.accept(this)
        if (myResult != null) {
          evaluators += myResult
        }
        myResult = null
        child = child.getNextSibling
      }
      if (evaluators.length > 0) {
        myCurrentFragmentEvaluator.setStatements(evaluators.toArray)
        myResult = myCurrentFragmentEvaluator
      }
      myCurrentFragmentEvaluator = oldCurrentFragmentEvaluator
    }

    private def localParams(fun: ScFunction, context: PsiElement): Seq[PsiElement] = {
      val buf = new HashSet[PsiElement]
      fun.accept(new ScalaRecursiveElementVisitor {
        override def visitReference(ref: ScReferenceElement) {
          val elem = ref.resolve()
          if (elem != null) {
            var element = elem
            while (element.getContext != null) {
              element = element.getContext
              if (element == fun) return
              else if (element == context) {
                buf += elem
                return
              }
            }
          }
        }
      })
      buf.toSeq.filter(isLocalV(_)).sortBy(e => (e.isInstanceOf[ScObject], e.getTextRange.getStartOffset))
    }

    private def paramCount(fun: ScFunction, context: PsiElement, elem: PsiElement): Int = {
      var index = localParams(fun, context).indexOf(elem)
      index = index
      if (index < 0) index = 0
      fun.effectiveParameterClauses.foldLeft(0) {
        case (i: Int, clause: ScParameterClause) => 
          i + clause.parameters.length
      } + index
    }

    private def isLocalV(resolve: PsiElement): Boolean = {
      resolve match {
        case _: PsiLocalVariable => true
        case _: ScClassParameter => false
        case _: PsiParameter => true
        case b: ScBindingPattern =>
          ScalaPsiUtil.nameContext(b) match {
            case v: ScValue =>
              !v.getContext.isInstanceOf[ScTemplateBody] && !v.getContext.isInstanceOf[ScEarlyDefinitions]
            case v: ScVariable =>
              !v.getContext.isInstanceOf[ScTemplateBody] && !v.getContext.isInstanceOf[ScEarlyDefinitions]
          }
        case o: ScObject =>
          !o.getContext.isInstanceOf[ScTemplateBody] && ScalaPsiUtil.getContextOfType(o, true, classOf[PsiClass]) != null
        case _ => false
      }
    }

    override def visitFunctionExpression(stmt: ScFunctionExpr) {
      throw EvaluateExceptionUtil.createEvaluateException("Anonymous functions are not supported")
    }

    override def visitExprInParent(expr: ScParenthesisedExpr) {
      expr.expr match {
        case Some(expr) =>
          expr.accept(this)
        case None =>
      }
    }

    override def visitPrefixExpression(p: ScPrefixExpr) {
      visitCall(p.operation, Some(p.operand), Nil, s => {
        val exprText = p.operation.refName + s
        ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, p.getContext, p)
      }, Map.empty, p)
    }

    override def visitPostfixExpression(p: ScPostfixExpr) {
      val qualifier = Some(p.operand)
      val resolve = p.operation.resolve()
      visitReferenceNoParameters(qualifier, resolve, p.operation, (s: String) => {
        val exprText = s + " " + p.operation.refName
        ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, p.getContext, p)
      })
    }

    override def visitReferenceExpression(ref: ScReferenceExpression) {
      val qualifier: Option[ScExpression] = ref.qualifier
      val resolve: PsiElement = ref.resolve()
      visitReferenceNoParameters(qualifier, resolve, ref, (s: String) => {
        val exprText = s + "." + ref.refName
        ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, ref.getContext, ref)
      })
    }

    override def visitIfStatement(stmt: ScIfStmt) {
      val condEvaluator = stmt.condition match {
        case Some(cond) =>
          cond.accept(this)
          myResult
        case None =>
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate if statement without condition")
      }
      val ifBranch = stmt.thenBranch match {
        case Some(th) =>
          th.accept(this)
          myResult
        case None =>
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate if statment withou if branch")
      }
      val elseBranch = stmt.elseBranch.map(e => {
        e.accept(this)
        myResult
      })
      myResult = new ScalaIfEvaluator(condEvaluator, ifBranch, elseBranch)
    }

    override def visitLiteral(l: ScLiteral) {
      val tp = l.getType(TypingContext.empty).getOrAny
      val value = l.getValue
      import org.jetbrains.plugins.scala.lang.psi.types.Null
      if (value == null && tp != Null) {
        throw EvaluateExceptionUtil.createEvaluateException("Literal has null value")
      }
      myResult = new ScalaLiteralEvaluator(value, tp)
    }

    override def visitWhileStatement(ws: ScWhileStmt) {
      val condEvaluator = ws.condition match {
        case Some(cond) =>
          cond.accept(this)
          myResult
        case None =>
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate while statement without condition")
      }
      val iterationEvaluator = ws.body match {
        case Some(body) =>
          body.accept(this)
          myResult
        case None =>
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate while statement without body")
      }

      myResult = new WhileStatementEvaluator(condEvaluator, iterationEvaluator, null)
    }

    override def visitDoStatement(stmt: ScDoStmt) {
      val condEvaluator = stmt.condition match {
        case Some(cond) =>
          cond.accept(this)
          myResult
        case None =>
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate do statement without condition")
      }
      val iterationEvaluator = stmt.getExprBody match {
        case Some(body) =>
          body.accept(this)
          myResult
        case None =>
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate do statement without body")
      }
      myResult = new ScalaDoStmtEvaluator(condEvaluator, iterationEvaluator)
    }

    private def evalThis(ref: Option[ScStableCodeReferenceElement], evaluator: Int => ScalaThisEvaluator,
                         stableEvaluator: Evaluator => Evaluator) {
      def defaults() {
        var contextClass = getContextClass
        var iterations = 0
        while (contextClass != null && !contextClass.isInstanceOf[PsiClass]) {
          contextClass = getContextClass(contextClass)
          iterations += 1
        }
        if (contextClass == null) myResult = evaluator(0)
        else myResult = evaluator(iterations)
      }
      ref match {
        case Some(ref) if ref.resolve() != null && ref.resolve().isInstanceOf[PsiClass] =>
          val clazz = ref.resolve().asInstanceOf[PsiClass]
          clazz match {
            case o: ScObject if isStable(o) =>
              myResult = stableEvaluator(stableObjectEvaluator(o))
              return
            case _ =>
          }
          var contextClass = getContextClass
          var iterations = 0
          while (contextClass != null && contextClass != clazz) {
            contextClass = getContextClass(contextClass)
            iterations += 1
          }
          if (contextClass == null) myResult = evaluator(0)
          else myResult = evaluator(iterations)
        case Some(ref) =>
          val refName = ref.refName
          var contextClass = getContextClass
          var iterations = 0
          while (contextClass != null && (!contextClass.isInstanceOf[PsiClass] ||
            contextClass.asInstanceOf[PsiClass].getName == null ||
            contextClass.asInstanceOf[PsiClass].getName == refName)) {
            contextClass = getContextClass(contextClass)
            iterations += 1
          }
          contextClass match {
            case o: ScObject if isStable(o) =>
              myResult = stableEvaluator(stableObjectEvaluator(o))
              return
            case null => defaults()
            case _ => myResult = evaluator(iterations)
          }
        case _ => defaults()
      }
    }

    override def visitForExpression(expr: ScForStatement) {
      expr.getDesugarisedExpr match {
        case Some(expr) => expr.accept(this)
        case None => throw EvaluateExceptionUtil.createEvaluateException("Cannot desugarize for statement")
      }
    }

    override def visitTryExpression(tryStmt: ScTryStmt) {
      throw EvaluateExceptionUtil.createEvaluateException("Try expression is not supported")
    }

    override def visitReturnStatement(ret: ScReturnStmt) {
      throw EvaluateExceptionUtil.createEvaluateException("Return statement is not supported")
    }

    override def visitFunction(fun: ScFunction) {
      throw EvaluateExceptionUtil.createEvaluateException("Function definition is not supported") //todo: ?
    }

    override def visitThisReference(t: ScThisReference) {
      evalThis(t.reference, new ScalaThisEvaluator(_), e => e)
    }

    override def visitSuperReference(t: ScSuperReference) {
      evalThis(t.reference, new ScalaSuperEvaluator(_), e => new ScalaSuperDelegate(e))
    }

    override def visitGenericCallExpression(call: ScGenericCall) {
      call.referencedExpr.accept(this)
    }

    def evaluateLocalMethod(resolve: PsiElement, argEvaluators: Seq[Evaluator]) {
      //local method
      val fun = resolve.asInstanceOf[ScFunction]
      val name = NameTransformer.encode(fun.name)
      val containingClass = getContainingClass(fun)
      if (getContextClass == null) {
        throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate local method")
      }
      val thisEvaluator: Evaluator = containingClass match {
        case obj: ScObject if isStable(obj) =>
          stableObjectEvaluator(obj)
        case _ =>
          var iterationCount = 0
          var outerClass = getContextClass
          while (outerClass != null && outerClass != containingClass) {
            iterationCount += 1
            outerClass = getContextClass(outerClass)
          }
          if (outerClass != null)
            new ScalaThisEvaluator(iterationCount)
          else null
      }
      if (thisEvaluator != null) {
        if (fun.effectiveParameterClauses.lastOption.map(_.isImplicit).getOrElse(false)) {
          //todo: find and pass implicit parameters
          throw EvaluateExceptionUtil.createEvaluateException("passing implicit parameters is not supported")
        }
        val args = localParams(fun, getContextClass(fun))
        val evaluators = argEvaluators ++ args.map(arg => {
          val name = arg.asInstanceOf[PsiNamedElement].getName
          val ref = ScalaPsiElementFactory.createExpressionWithContextFromText(name, position.getElementAt,
            position.getElementAt).asInstanceOf[ScReferenceExpression]
          val builder = new Builder(position)
          builder.buildElement(ref)
          var res = builder.myResult
          if (arg.isInstanceOf[ScObject]) {
            val qual = "scala.runtime.VolatileObjectRef"
            val typeEvaluator = new TypeEvaluator(JVMNameUtil.getJVMRawText(qual))
            res = new ScalaNewClassInstanceEvaluator(typeEvaluator,
              JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)V"), Array(res))
          }
          res
        })
        val methodEvaluator = new ScalaMethodEvaluator(
          thisEvaluator, name, null /* todo? */ , evaluators, true, None,
          DebuggerUtil.getSourcePositions(resolve.getNavigationElement)
        )
        myResult = methodEvaluator
        return
      }
      throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate local method")
    }

    private val BOXES_RUN_TIME = new TypeEvaluator(JVMNameUtil.getJVMRawText("scala.runtime.BoxesRunTime"))
    private def boxEvaluator(eval: Evaluator): Evaluator = new BoxingEvaluator(eval)
    private def unboxEvaluator(eval: Evaluator): Evaluator = new UnBoxingEvaluator(eval)
    private def notEvaluator(eval: Evaluator): Evaluator = {
      unboxEvaluator(new ScalaMethodEvaluator(BOXES_RUN_TIME, "takeNot",
        JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)Ljava/lang/Object;"), Seq(boxEvaluator(eval)),
        false, None, Set.empty))
    }
    private def eqEvaluator(left: Evaluator, right: Evaluator): Evaluator = {
      new ScalaEqEvaluator(left, right)
    }
    private def neEvaluator(left: Evaluator, right: Evaluator): Evaluator = {
      notEvaluator(eqEvaluator(left, right))
    }
    private def unaryEvaluator(eval: Evaluator, boxesRunTimeName: String): Evaluator = {
      unboxEvaluator(new ScalaMethodEvaluator(BOXES_RUN_TIME, boxesRunTimeName,
        JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)Ljava/lang/Object;"), Seq(boxEvaluator(eval)),
        false, None, Set.empty))
    }
    private def binaryEvaluator(left: Evaluator, right: Evaluator, boxesRunTimeName: String): Evaluator = {
      unboxEvaluator(new ScalaMethodEvaluator(BOXES_RUN_TIME, boxesRunTimeName,
        JVMNameUtil.getJVMRawText("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        Seq(boxEvaluator(left), boxEvaluator(right)), false, None, Set.empty)) 
    }

    private def evaluateSyntheticFunction(synth: ScSyntheticFunction, qual: Option[ScExpression], ref: ScReferenceExpression,
                                  argEvaluators: Seq[Evaluator]) {
      evaluateSyntheticFunctionForName(synth.getName, qual, ref, argEvaluators)
    }

    private def evaluateSyntheticFunctionForName(name: String, qual: Option[ScExpression], ref: ScReferenceExpression,
                                  argEvaluators: Seq[Evaluator]) {
      def unaryEval(operatorName: String, function: Evaluator => Evaluator) {
        if (argEvaluators.length == 0) {
          val eval = qual match {
            case None => new ScalaThisEvaluator()
            case Some(qual) =>
              qual.accept(this)
              myResult
          }
          myResult = function(eval)
        } else throw EvaluateExceptionUtil.createEvaluateException("Wrong number of arguments for method '" +
          operatorName + "'")
      }
      def unaryEvalForBoxes(operatorName: String, boxesName: String) {
        unaryEval(operatorName, unaryEvaluator(_, boxesName))
      }
      def binaryEval(operatorName: String, function: (Evaluator, Evaluator) => Evaluator) {
        if (argEvaluators.length == 1) {
          val eval = qual match {
            case None => new ScalaThisEvaluator()
            case Some(qual) =>
              qual.accept(this)
              myResult
          }
          myResult = function(eval, argEvaluators(0))
        } else throw EvaluateExceptionUtil.createEvaluateException("Wrong number of arguments for method '" +
          operatorName + "'")
      }
      def binaryEvalForBoxes(operatorName: String, boxesName: String) {
        binaryEval(operatorName, binaryEvaluator(_, _, boxesName))
      }
      name match {
        case "isInstanceOf" =>
          unaryEval(name, eval => {
            import org.jetbrains.plugins.scala.lang.psi.types.Nothing
            val tp = ref.getParent match {
              case gen: ScGenericCall => gen.typeArgs match {
                case Some(args) => args.typeArgs match {
                  case Seq(arg) => arg.calcType
                  case _ => Nothing
                }
                case None => Nothing
              }
              case _ => Nothing
            }
            val jvmName: JVMName = DebuggerUtil.getJVMQualifiedName(tp)
            new ScalaInstanceofEvaluator(eval, new TypeEvaluator(jvmName))
          })
        case "asInstanceOf" => unaryEval(name, identity) //todo: primitive type casting?
        case "##" => unaryEval(name, eval => new ScalaMethodEvaluator(BOXES_RUN_TIME, "hashFromObject",
          JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)I"), Seq(new BoxingEvaluator(eval)),
          false, None, Set.empty))
        case "==" =>
          binaryEval(name, (l, r) => new ScalaMethodEvaluator(BOXES_RUN_TIME, "equals",
            JVMNameUtil.getJVMRawText("(Ljava/lang/Object;Ljava/lang/Object;)Z"), Seq(
              new BoxingEvaluator(l), new BoxingEvaluator(r)),
            false, None, Set.empty))
        case "!=" =>
          binaryEval(name, (l, r) =>new ScalaMethodEvaluator(BOXES_RUN_TIME, "equals",
            JVMNameUtil.getJVMRawText("(Ljava/lang/Object;Ljava/lang/Object;)Z"), Seq(
              new BoxingEvaluator(l), new BoxingEvaluator(r)),
            false, None, Set.empty))
        case "unary_!" => unaryEvalForBoxes("!", "takeNot")
        case "unary_~" => unaryEvalForBoxes("~", "complement")
        case "unary_+" => unaryEvalForBoxes("+", "positive")
        case "unary_-" => unaryEvalForBoxes("-", "negate")
        case "eq" => binaryEval(name, eqEvaluator(_, _))
        case "ne" => binaryEval(name, neEvaluator(_, _))
        case "<" => binaryEvalForBoxes(name, "testLessThan")
        case ">" => binaryEvalForBoxes(name, "testGreaterThan")
        case ">=" => binaryEvalForBoxes(name, "testGreaterOrEqualThan")
        case "<=" => binaryEvalForBoxes(name, "testLessOrEqualThan")
        case "+" => binaryEvalForBoxes(name, "add")
        case "-" => binaryEvalForBoxes(name, "subtract")
        case "*" => binaryEvalForBoxes(name, "multiply")
        case "/" => binaryEvalForBoxes(name, "divide")
        case "%" => binaryEvalForBoxes(name, "takeModulo")
        case ">>" => binaryEvalForBoxes(name, "shiftSignedRight")
        case "<<" => binaryEvalForBoxes(name, "shiftSignedLeft")
        case ">>>" => binaryEvalForBoxes(name, "shiftLogicalRight")
        case "&" => binaryEvalForBoxes(name, "takeAnd")
        case "|" => binaryEvalForBoxes(name, "takeOr")
        case "^" => binaryEvalForBoxes(name, "takeXor")
        case "&&" => binaryEvalForBoxes(name, "takeConditionalAnd") //todo: don't eval if not needed
        case "||" => binaryEvalForBoxes(name, "takeConditionalOr") //todo: don't eval if not needed
        case "toInt" => unaryEvalForBoxes(name, "toInteger")
        case "toChar" => unaryEvalForBoxes(name, "toCharacter")
        case "toShort" => unaryEvalForBoxes(name, "toShort")
        case "toByte" => unaryEvalForBoxes(name, "toByte")
        case "toDouble" => unaryEvalForBoxes(name, "toDouble")
        case "toLong" => unaryEvalForBoxes(name, "toLong")
        case "toFloat" => unaryEvalForBoxes(name, "toFloat")
        case "synchronized" =>
          throw EvaluateExceptionUtil.createEvaluateException("synchronized statement is not supported")
        case _ =>
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate synthetic method: " + name)
      }
    }

    private def evaluateArrayMethod(name: String, qual: Option[ScExpression], argEvaluators: Seq[Evaluator]) {
      name match {
        case "apply" =>
          if (argEvaluators.length == 1 && qual != None) {
            qual.get.accept(this)
            myResult = new ScalaArrayAccessEvaluator(myResult, argEvaluators(0))
          } else throw EvaluateExceptionUtil.createEvaluateException("Wrong number of parameters for Array.apply method")
        case "length" =>
          if (argEvaluators.length == 0 && qual != None) {
            qual.get.accept(this)
            myResult = new ScalaFieldEvaluator(myResult, _ => true, "length")
          } else throw EvaluateExceptionUtil.createEvaluateException("Wrong number of parameters for Array.length method")
        case "clone" =>
          if (argEvaluators.length == 0 && qual != None) {
            qual.get.accept(this)
            myResult = new ScalaMethodEvaluator(myResult, "clone", null/*todo*/, Nil, false, None, Set.empty)
          } else throw EvaluateExceptionUtil.createEvaluateException("Wrong number of parameters for Array.clone method")
        case "update" =>
          if (argEvaluators.length == 2 && qual != None) {
            qual.get.accept(this)
            val leftEval = new ScalaArrayAccessEvaluator(myResult, argEvaluators(0))
            myResult = new AssignmentEvaluator(leftEval, argEvaluators(1))
          } else throw EvaluateExceptionUtil.createEvaluateException("Wrong number of parameters for Array.update method")
        case "toString" =>
          if (argEvaluators.length == 0 && qual != None) {
            qual.get.accept(this)
            myResult = new ScalaMethodEvaluator(myResult, "toString", null/*todo*/, Nil, false, None, Set.empty)
          } else throw EvaluateExceptionUtil.createEvaluateException("Wrong number of parameters for Array.toString method")
        case _ =>
          throw EvaluateExceptionUtil.createEvaluateException("Array method not supported")
      }
    }

    private def isArrayFunction(fun: ScFunction): Boolean = {
      fun.getContext match {
        case tb: ScTemplateBody =>
          fun.getContainingClass match {
            case clazz: ScClass if clazz.getQualifiedName == "scala.Array" => true
            case _ => false
          }
        case _ => false
      }
    }

    private def replaceWithImplicitFunction(implicitFunction: PsiNamedElement, qual: ScExpression,
                                            replaceWithImplicit: (String) => ScExpression) {
      val context = ScalaPsiUtil.nameContext(implicitFunction)
      val clazz = context.getContext match {
        case tb: ScTemplateBody =>
          PsiTreeUtil.getContextOfType(tb, classOf[PsiClass])
        case _ => null
      }
      clazz match {
        case o: ScObject if isStable(o) =>
          val exprText = o.getQualifiedName + "." + implicitFunction.getName + "(" +
            qual.getText + ")"
          val expr = replaceWithImplicit(exprText)
          expr.accept(this)
          return
        case o: ScObject => //todo: It can cover many cases!
          throw EvaluateExceptionUtil.
            createEvaluateException("Implicit conversions from dependent objects are not supported")
        case _ => //from scope
          val exprText = implicitFunction.getName + "(" + qual.getText + ")"
          val expr = replaceWithImplicit(exprText)
          expr.accept(this)
          return
      }
    }

    private def visitCall(ref: ScReferenceExpression, qualOption: Option[ScExpression], arguments: Seq[ScExpression],
                          replaceWithImplicit: String => ScExpression,
                          matchedParameters: Map[Parameter, Seq[ScExpression]], expr: ScExpression) {
      val resolve = ref.resolve()
      def argEvaluators: Seq[Evaluator] = arguments.map(arg => {
        arg.accept(this)
        myResult
      })
      resolve match {
        case fun: ScFunction if isLocalFunction(fun) =>
          evaluateLocalMethod(resolve, argEvaluators) //todo: use matched parameters
        case synth: ScSyntheticFunction =>
          evaluateSyntheticFunction(synth, qualOption, ref, argEvaluators) //todo: use matched parameters
        case fun: ScFunction if isArrayFunction(fun) =>
          evaluateArrayMethod(fun.getName,  qualOption, argEvaluators) //todo: use matched parameters
        case fun: ScFunction =>
          val implicitParameters = fun.effectiveParameterClauses.lastOption.toSeq.flatMap(
            (clause: ScParameterClause) => {
              if (clause.isImplicit) clause.parameters
              else Seq.empty
            })
          var argEvaluators: Seq[Evaluator] = fun.effectiveParameterClauses.flatMap(_.parameters).map(
            (param: ScParameter) => {
              val p = new Parameter(param)
              val e = matchedParameters.getOrElse(p, Seq.empty)
              if (p.isRepeated) {
                val exprText = "_root_.scala.collection.Seq.newBuilder[Any]" +
                  (if (e.length > 0) e.sortBy(_.getTextRange.getStartOffset).map(_.getText).mkString(".+=(", ").+=(", ").result()") else "")
                val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, expr.getContext,
                  expr)
                newExpr.accept(this)
                myResult
              } else if (e.length > 0) {
                if (e.length == 1) {
                  e(0).accept(this)
                  myResult
                } else {
                  throw EvaluateExceptionUtil.createEvaluateException("Wrong number of matched expressions")
                }
              } else if (implicitParameters.contains(param)) {
                val i = implicitParameters.indexOf(param)
                expr.findImplicitParameters match {
                  case Some(s) if s.length == implicitParameters.length =>
                    s(i) match {
                      case null =>
                        ScType.extractClass(p.paramType, Some(expr.getProject)) match {
                          case Some(clazz) if clazz.getQualifiedName == "scala.reflect.ClassManifest" =>
                            p.paramType match {
                              case ScParameterizedType(tp, Seq(arg)) =>
                                import org.jetbrains.plugins.scala.lang.psi.types._
                                def text(arg: ScType): String = arg match {
                                  case Short => "_root_.scala.reflect.ClassManifest.Short"
                                  case Byte => "_root_.scala.reflect.ClassManifest.Byte"
                                  case Char => "_root_.scala.reflect.ClassManifest.Char"
                                  case Int => "_root_.scala.reflect.ClassManifest.Int"
                                  case Long => "_root_.scala.reflect.ClassManifest.Long"
                                  case Float => "_root_.scala.reflect.ClassManifest.Float"
                                  case Double => "_root_.scala.reflect.ClassManifest.Double"
                                  case Boolean => "_root_.scala.reflect.ClassManifest.Boolean"
                                  case Unit => "_root_.scala.reflect.ClassManifest.Unit"
                                  case Any => "_root_.scala.reflect.ClassManifest.Any"
                                  case AnyVal => "_root_.scala.reflect.ClassManifest.AnyVal"
                                  case Nothing => "_root_.scala.reflect.ClassManifest.Nothing"
                                  case Null => "_root_.scala.reflect.ClassManifest.Null"
                                  case Singleton => "_root_.scala.reflect.ClassManifest.Object"
                                  /*case JavaArrayType(arg) =>
                                    "_root_.scala.reflect.ClassManifest.arrayType(" + text(arg) + ")"
                                  case ScParameterizedType(ScDesignatorType(clazz: ScClass), Seq(arg))
                                    if clazz.getQualifiedName == "scala.Array" =>
                                    "_root_.scala.reflect.ClassManifest.arrayType(" + text(arg) + ")"
                                  case ScParameterizedType(des, args) =>
                                    ScType.extractClass(des, Option(expr.getProject)) match {
                                      case Some(clazz) =>
                                        "_root_.scala.reflect.ClassManifest.classType(" +
                                      case _ => "null"
                                    }
                                  case _ => ScType.extractClass(arg, Option(expr.getProject)) match {
                                    case Some(clazz) =>
                                    case _ => "null"
                                  }*/
                                  case _ => "null" //todo: something like above?
                                }
                                val e = ScalaPsiElementFactory.createExpressionWithContextFromText(text(arg),
                                  expr.getContext, expr)
                                e.accept(this)
                                myResult
                              case _ =>
                                throw EvaluateExceptionUtil.createEvaluateException("cannot find implicit parameters to pass")
                            }
                          case _ =>
                            throw EvaluateExceptionUtil.createEvaluateException("cannot find implicit parameters to pass")
                        }
                      case ScalaResolveResult(param, _) =>
                        val context = ScalaPsiUtil.nameContext(param)
                        val clazz = context.getContext match {
                          case _: ScTemplateBody | _: ScEarlyDefinitions =>
                            ScalaPsiUtil.getContextOfType(context, true, classOf[PsiClass])
                          case _ if context.isInstanceOf[ScClassParameter] =>
                            ScalaPsiUtil.getContextOfType(context, true, classOf[PsiClass])
                          case _ => null
                        }
                        clazz match {
                          case o: ScObject if isStable(o) =>
                            val exprText = o.getQualifiedName + "." + param.getName
                            val e = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText,
                              expr.getContext, expr)
                            e.accept(this)
                            myResult
                          case o: ScObject => //todo: It can cover many cases!
                            throw EvaluateExceptionUtil.
                              createEvaluateException("Implicit parameters from dependent objects are not supported")
                          case _ => //from scope
                            val exprText = param.getName
                            val e = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText,
                              expr.getContext, expr)
                            e.accept(this)
                            myResult
                        }
                    }
                  case None =>
                    throw EvaluateExceptionUtil.createEvaluateException("cannot find implicit parameters to pass")
                }
              } else if (p.isDefault) {
                //todo:
                throw EvaluateExceptionUtil.createEvaluateException("passing default parameters is not supported")
              } else null
            })
          if (argEvaluators.contains(null))
            argEvaluators = arguments.map(arg => {
              arg.accept(this)
              myResult
            })
          qualOption match {
            case Some(qual) =>
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  r.implicitFunction match {
                    case Some(fun) =>
                      replaceWithImplicitFunction(fun, qual, replaceWithImplicit)
                      return
                    case _ =>
                      qual.accept(this)
                      val name = NameTransformer.encode(fun.name)
                      myResult = new ScalaMethodEvaluator(myResult, name, null /* todo? */, argEvaluators, false,
                        traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                      return
                  }
                case _ => //resolve not null => shouldn't be
              }
            case None =>
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  if (!r.importsUsed.isEmpty) {
                    //todo:
                    throw EvaluateExceptionUtil.createEvaluateException("Evaluation of imported functions is not supported")
                  } else {
                    val evaluator = thisEvaluator(r)
                    val name = NameTransformer.encode(fun.getName)
                    myResult = new ScalaMethodEvaluator(evaluator, name, null /* todo? */, argEvaluators, false,
                      traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                    return
                  }
                case _ => //resolve not null => shouldn't be
              }
          }
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate method")
        case method: PsiMethod => //here you can use just arguments
          val argEvaluators: Seq[Evaluator] = arguments.map(arg => {
            arg.accept(this)
            myResult
          })
          qualOption match {
            case Some(qual) =>
              if (method.hasModifierProperty("static")) {
                val eval =
                  new TypeEvaluator(JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(method)))
                val name = method.getName
                myResult = new ScalaMethodEvaluator(eval, name, null /* todo? */, argEvaluators, false,
                  traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                return
              } else {
                ref.bind() match {
                  case Some(r: ScalaResolveResult) =>
                    r.implicitFunction match {
                      case Some(fun) =>
                        replaceWithImplicitFunction(fun, qual, replaceWithImplicit)
                        return
                      case _ =>
                        qual.accept(this)
                        val name = method.getName
                        myResult = new ScalaMethodEvaluator(myResult, name, null /* todo? */, argEvaluators, false,
                          traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                        return
                    }
                  case _ => //resolve not null => shouldn't be
                }
              }
            case None =>
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  if (!r.importsUsed.isEmpty) {
                    //todo:
                    throw EvaluateExceptionUtil.createEvaluateException("Evaluation of imported functions is not supported")
                  } else {
                    val evaluator = thisEvaluator(r)
                    val name = method.getName
                    myResult = new ScalaMethodEvaluator(evaluator, name, null /* todo? */, argEvaluators, false,
                      traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                    return
                  }
                case _ => //resolve not null => shouldn't be
              }
          }
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate method")
        case _ => //todo: dynamic method invocation
          throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate method")
      }
    }

    override def visitMethodCallExpression(parentCall: ScMethodCall) {
      def collectArguments(call: ScMethodCall, collected: Seq[ScExpression] = Seq.empty, tailString: String = "",
                           matchedParameters: Map[Parameter, Seq[ScExpression]] = Map.empty) {
        if (call.isApplyOrUpdateCall) {
          if (!call.isUpdateCall) {
            val newExprText = new StringBuilder
            newExprText.append("(").append(call.getInvokedExpr.getText).append(").apply").append(call.args.getText + tailString)
            val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(newExprText.toString(), call.getContext, call)
            expr.accept(this)
          } else {
            //should be handled on assignment
            throw EvaluateExceptionUtil.createEvaluateException("Update method is not supported")
          }
          return
        }
        call.getInvokedExpr match {
          case ref: ScReferenceExpression =>
            visitCall(ref, ref.qualifier, call.argumentExpressions ++ collected, s => {
              val exprText = s + "." + ref.refName + call.args.getText + tailString
              ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, parentCall.getContext,
                parentCall)
            }, matchedParameters ++ call.matchedParametersMap, parentCall)
          case newCall: ScMethodCall =>
            collectArguments(newCall, call.argumentExpressions ++ collected, call.args.getText + tailString,
              matchedParameters ++ call.matchedParametersMap)
          case _ =>
        }
      }
      collectArguments(parentCall)
    }

    override def visitInfixExpression(infix: ScInfixExpr) {
      val operation = infix.operation
      if (operation.refName.endsWith("=")) {
        operation.resolve() match {
          case n: PsiNamedElement if n.getName + "=" == operation.refName =>
            val exprText = new StringBuilder
            exprText.append(infix.getBaseExpr.getText).append(" = ").append(infix.getBaseExpr.getText).
              append(" ").append(operation.refName.dropRight(1)).append(" ").append(infix.getArgExpr.getText)
            val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText.toString(), infix.getContext, infix)
            expr.accept(this)
            return
          case _ =>
        }
      }
      visitCall(operation, Some(infix.getBaseExpr), infix.argumentExpressions, s => {
        val exprText = s + " " + operation.refName + " " + infix.getArgExpr.getText
        ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, infix.getContext, infix)
      }, infix.matchedParametersMap, infix)
    }

    private def isStable(o: ScObject): Boolean = {
      val context = ScalaPsiUtil.getContextOfType(o, true, classOf[PsiClass])
      if (context == null) return true
      context match {
        case o: ScObject => isStable(o)
        case _ => false
      }
    }

    private def stableObjectEvaluator(qual: String): ScalaFieldEvaluator = {
      val jvm = JVMNameUtil.getJVMRawText(qual)
      new ScalaFieldEvaluator(new TypeEvaluator(jvm), ref => ref.name() == qual, "MODULE$")
    }

    private def stableObjectEvaluator(obj: ScObject): Evaluator = {
      val qualName = 
        if (obj.isPackageObject)
          obj.getQualifiedName + ".package"
        else obj.getQualifiedNameForDebugger
      val qual = qualName.split('.').map(NameTransformer.encode(_)).mkString(".") + "$"
      stableObjectEvaluator(qual)
    }

    private def thisEvaluator(resolveResult: ScalaResolveResult): Evaluator = {
      //this reference
      val elem = resolveResult.element
      val containingClass = resolveResult.fromType match {
        case Some(ScThisType(clazz)) => clazz
        case Some(tp) => ScType.extractClass(tp, Some(elem.getProject)) match {
          case Some(x) => x
          case None => getContainingClass(elem)
        }
        case _ => getContainingClass(elem)
      }
      containingClass match {
        case o: ScObject if isStable(o) =>
          return stableObjectEvaluator(o)
        case _ =>
      }
      var iterationCount = 0
      var outerClass = getContextClass
      while (outerClass != null && outerClass != containingClass) {
        iterationCount += 1
        outerClass = getContextClass(outerClass)
      }

      if (outerClass != null)
        new ScalaThisEvaluator(iterationCount)
      else new ScalaThisEvaluator()
    }

    private def traitImplementation(elem: PsiElement): Option[JVMName] = {
      val clazz = getContextClass(elem)
      clazz match {
        case t: ScTrait =>
          Some(DebuggerUtil.getClassJVMName(t, true))
        case _ => None
      }
    }

    private def isLocalFunction(fun: ScFunction): Boolean = {
      !fun.getContext.isInstanceOf[ScTemplateBody]
    }

    private def isInsideLocalFunction(elem: PsiElement): Option[ScFunction] = {
      var element = elem
      while (element != null) {
        element match {
          case fun: ScFunction if isLocalFunction(fun) && fun.parameters.find(elem == _) == None => return Some(fun)
          case _ => element = element.getContext
        }
      }
      None
    }

    private def visitReferenceNoParameters(qualifier: Option[ScExpression],
                                           resolve: PsiElement,
                                           ref: ScReferenceExpression, replaceWithImplicit: String => ScExpression) {
      val isLocalValue = isLocalV(resolve)
      def calcLocal: Boolean = {
        val labeledValue = resolve.getUserData(CodeFragmentFactoryContextWrapper.LABEL_VARIABLE_VALUE_KEY)
        if (labeledValue != null) {
          myResult = new IdentityEvaluator(labeledValue)
          return true
        }

        val isObject = resolve.isInstanceOf[ScObject]

        val namedElement = resolve.asInstanceOf[PsiNamedElement]
        val name = NameTransformer.encode(namedElement.getName) + (if (isObject) "$module" else "")
        val containingClass = getContainingClass(namedElement)

        if (getContextClass == null || getContextClass == containingClass) {
          val evaluator = new ScalaLocalVariableEvaluator(name)
          namedElement match {
            case param: ScParameter =>
              val clause = param.getContext.asInstanceOf[ScParameterClause]
              evaluator.setParameterIndex(clause.parameters.indexOf(param))
            case _ =>
          }
          myResult = evaluator
          if (isObject) {
            myResult = new ScalaFieldEvaluator(myResult, ref => true, "elem") //get from VolatileObjectReference
          }
          return true
        }

        var iterationCount = 0
        var positionClass = getContextClass
        var outerClass = getContextClass(getContextClass)
        while (outerClass != null && outerClass != containingClass) {
          iterationCount += 1
          outerClass = getContextClass(outerClass)
          positionClass = getContextClass(positionClass)
        }
        if (outerClass != null) {
          val evaluator = new ScalaThisEvaluator(iterationCount)
          val filter = ScalaFieldEvaluator.getFilter(positionClass)
          myResult = new ScalaFieldEvaluator(evaluator, filter, name)
          if (isObject) {
            //todo: calss name() method to initialize this field?
            myResult = new ScalaFieldEvaluator(myResult, ref => true, "elem") //get from VolatileObjectReference
          }
          return true
        }
        throw EvaluateExceptionUtil.createEvaluateException("Cannot load local variable from anonymous class")
      }

      if (isLocalValue && isInsideLocalFunction(ref) == None) {
        calcLocal
        return
      } else if (isLocalValue) {
        val fun = isInsideLocalFunction(ref).get
        val contextClass = getContextClass(fun)
        if (PsiTreeUtil.isContextAncestor(contextClass, resolve, true)) {
          val pCount = paramCount(fun, contextClass, resolve)
          val context = getContextClass
          if (context != contextClass) {
            calcLocal
            return
          } else {
            val name = NameTransformer.encode(resolve.asInstanceOf[PsiNamedElement].getName)
            val evaluator = new ScalaLocalVariableEvaluator(name, true)
            //it's simple, let's take parameter
            evaluator.setParameterIndex(pCount)
            myResult = evaluator
            return
          }
        } else {
          calcLocal
          return
        }
      } else if (resolve.isInstanceOf[ScSyntheticFunction]) {
        val synth = resolve.asInstanceOf[ScSyntheticFunction]
        evaluateSyntheticFunction(synth, qualifier, ref, Seq.empty)
        return
      } else if (resolve.isInstanceOf[ScFunction] && isArrayFunction(resolve.asInstanceOf[ScFunction])) {
        evaluateArrayMethod(resolve.asInstanceOf[ScFunction].getName, qualifier, Nil)
      } else if (resolve.isInstanceOf[ScFunction] && !resolve.getContext.isInstanceOf[ScTemplateBody]) {
        evaluateLocalMethod(resolve, Seq.empty)
      } else if (resolve.isInstanceOf[ScObject]) {
        val obj = resolve.asInstanceOf[ScObject]
        //here we have few possibilities
        //1. top level object
        if (isStable(obj)) {
          myResult = stableObjectEvaluator(obj)
          return
        }
        //2. object on reference
        //3. object on implicit reference
        qualifier match {
          case Some(qual) =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                r.implicitFunction match {
                  case Some(fun) =>
                    replaceWithImplicitFunction(fun, qual, replaceWithImplicit)
                    return
                  case _ =>
                    qual.accept(this)
                    val name = NameTransformer.encode(obj.getName)
                    myResult = new ScalaMethodEvaluator(myResult, name, null /* todo? */, Seq.empty, false,
                      traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                    return
                }
              case _ => //resolve not null => shouldn't be
            }
          case None => 
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                if (!r.importsUsed.isEmpty) {
                  //todo:
                  throw EvaluateExceptionUtil.createEvaluateException("Evaluation of imported objects is not supported")
                } else {
                  val evaluator = thisEvaluator(r)
                  val name = NameTransformer.encode(obj.getName)
                  myResult = new ScalaMethodEvaluator(evaluator, name, null /* todo? */, Seq.empty, false,
                    traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                  return
                }
              case _ => //resolve not null => shouldn't be
            }
        }
        throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate object")
      } else if (resolve.isInstanceOf[ScFunction]) {
        visitCall(ref, qualifier, Nil, replaceWithImplicit, Map.empty, ref)
        return
      } else if (resolve.isInstanceOf[PsiMethod]) {
        visitCall(ref, qualifier, Nil, replaceWithImplicit, Map.empty, ref)
        return
      } else if (resolve.isInstanceOf[ScClassParameter] || resolve.isInstanceOf[ScBindingPattern]) {
        //this is scala "field"
        val named = resolve.asInstanceOf[ScNamedElement]
        qualifier match {
          case Some(qual) =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                r.implicitFunction match {
                  case Some(fun) =>
                    replaceWithImplicitFunction(fun, qual, replaceWithImplicit)
                    return
                  case _ =>
                    qual.accept(this)
                    val name = NameTransformer.encode(named.name)
                    myResult = new ScalaMethodEvaluator(myResult, name, null /* todo */, Seq.empty, false,
                      traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                    return
                }
              case None =>
            }
          case None =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                if (!r.importsUsed.isEmpty) {
                  //todo:
                  throw EvaluateExceptionUtil.createEvaluateException("Evaluation of imported fields is not supported")
                } else {
                  val evaluator = thisEvaluator(r)
                  val name = NameTransformer.encode(named.getName)
                  myResult = new ScalaMethodEvaluator(evaluator, name, null/* todo */, Seq.empty, false,
                    traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                  return
                }
              case None =>
            }
        }

        throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate value")
      } else if (resolve.isInstanceOf[PsiField]) {
        val field = resolve.asInstanceOf[PsiField]
        qualifier match {
          case Some(qual) =>
            if (field.hasModifierProperty("static")) {
              val eval =
                new TypeEvaluator(JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(field)))
              val name = field.getName
              myResult = new ScalaFieldEvaluator(eval, ref => true,name)
              return
            } else {
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  r.implicitFunction match {
                    case Some(fun) =>
                      replaceWithImplicitFunction(fun, qual, replaceWithImplicit)
                      return
                    case _ =>
                      qual.accept(this)
                      val name = field.getName
                      myResult = new ScalaFieldEvaluator(myResult,
                        ScalaFieldEvaluator.getFilter(getContainingClass(field)), name)
                      return
                  }
                case _ => //resolve not null => shouldn't be
              }
            }
          case None =>
            ref.bind() match {
              case Some(r: ScalaResolveResult) =>
                if (!r.importsUsed.isEmpty) {
                  //todo:
                  throw EvaluateExceptionUtil.createEvaluateException("Evaluation of imported fileds is not supported")
                } else {
                  val evaluator = thisEvaluator(r)
                  val name = field.getName
                  myResult = new ScalaFieldEvaluator(evaluator,
                    ScalaFieldEvaluator.getFilter(getContainingClass(field)), name)
                  return
                }
              case _ => //resolve not null => shouldn't be
            }
        }
      } else if (resolve.isInstanceOf[ScPackage]) {
        val pack = resolve.asInstanceOf[ScPackage]
        //let's try to find package object:
        val qual = (pack.getQualifiedName + ".package$").split('.').map(NameTransformer.encode(_)).mkString(".")
        myResult = stableObjectEvaluator(qual)
      } else {
        //unresolved symbol => try to resolve it dynamically
        val name = NameTransformer.encode(ref.refName)
        qualifier match {
          case Some(qual) =>
            qual.accept(this)
            if (myResult == null) {
              throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate unresolved reference expression")
            }
            myResult = new ScalaFieldEvaluator(myResult, ref => true, name)
            return
          case None =>
            myResult = new ScalaLocalVariableEvaluator(name, false)
            return
        }
      }
    }

    def buildElement(element: PsiElement): ExpressionEvaluator = {
      assert(element.isValid)
      myContextClass = getContextClass(element)
      try {
        element.accept(this)
      } catch {
        case e: EvaluateRuntimeException => throw e.getCause
      }
      if (myResult == null) {
        throw EvaluateExceptionUtil.createEvaluateException("Invalid evaluation expression")
      }
      new ExpressionEvaluatorImpl(myResult)
    }
  }
}