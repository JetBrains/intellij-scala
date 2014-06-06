package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation._
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.debugger.engine.{JVMName, JVMNameUtil}
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions.{toObjectExt, toPsiClassExt, toPsiModifierListOwnerExt, toPsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaElementVisitor, ScalaRecursiveElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.NameTransformer

/**
 * User: Alefas
 * Date: 11.10.11
 */

object ScalaEvaluatorBuilder extends EvaluatorBuilder {
  def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    val project = position.getFile.getProject
    val cache = ScalaEvaluatorCache.getInstance(project)

    def cached: Option[ExpressionEvaluator] = {
      try cache.get(position, codeFragment)
      catch {
        case e: Exception =>
          cache.clear()
          None
      }
    }

    def buildNew(): ExpressionEvaluator = {
      if (codeFragment.getLanguage.isInstanceOf[JavaLanguage])
        return EvaluatorBuilderImpl.getInstance().build(codeFragment, position) //java builder (e.g. SCL-6117)

      val eval = new Builder(position).buildElement(codeFragment)
      cache.add(position, codeFragment, eval)
      eval
    }

    assert(codeFragment != null)
    cached.getOrElse(buildNew())
  }

  private class Builder(position: SourcePosition) extends ScalaElementVisitor {
    private var myResult: Evaluator = null
    private var myCurrentFragmentEvaluator: CodeFragmentEvaluator = null
    private var myContextClass: PsiElement = null

    private def getContextClass: PsiElement = myContextClass

    private def isGenerateClass(elem: PsiElement, child: PsiElement): Boolean = {
      elem match {
        case clazz: PsiClass => true
        case f: ScFunctionExpr => true
        case f: ScForStatement if f.body == Some(child) => true
        case e: ScExpression if ScUnderScoreSectionUtil.underscores(e).length > 0 => true
        case b: ScBlockExpr if b.isAnonymousFunction => true
        case _ => false
      }
    }
    
    private def anonClassCount(elem: PsiElement): Int = {
      elem match {
        case f: ScForStatement =>
          f.enumerators.fold(1)(e => e.enumerators.length + e.generators.length + e.guards.length) //todo: non irrefutable patterns?
        case _ => 1
      }
    }

    private def getContainingClass(elem: PsiElement): PsiElement = {
      var element = elem.getParent
      var child = elem
      while (element != null && !isGenerateClass(element, child)) {
        element = element.getParent
        child = child.getParent
      }
      if (element == null) getContextClass else element
    }

    private def getContextClass(elem: PsiElement): PsiElement = {
      var element = elem.getContext
      var child = elem
      while (element != null && !isGenerateClass(element, child)) {
        element = element.getContext
        child = child.getContext
      }
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
      super.visitFile(file)
    }

    private def localParams(fun: ScFunctionDefinition, context: PsiElement): Seq[PsiElement] = {
      val buf = new mutable.HashSet[PsiElement]
      val body = fun.body //to exclude references from default parameters
      body.foreach(_.accept(new ScalaRecursiveElementVisitor {
        override def visitReference(ref: ScReferenceElement) {
          if (ref.qualifier != None) {
            super.visitReference(ref)
            return
          }
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
          super.visitReference(ref)
        }
      }))
      buf.toSeq.filter(isLocalV).sortBy(e => (e.isInstanceOf[ScObject], e.getTextRange.getStartOffset))
    }

    private def isLocalV(resolve: PsiElement): Boolean = {
      resolve match {
        case _: PsiLocalVariable => true
        case _: ScClassParameter => false
        case _: PsiParameter => true
        case b: ScBindingPattern =>
          ScalaPsiUtil.nameContext(b) match {
            case v @ (_: ScValue | _: ScVariable) =>
              !v.getContext.isInstanceOf[ScTemplateBody] && !v.getContext.isInstanceOf[ScEarlyDefinitions]
            case clause: ScCaseClause => true
            case _ => true //todo: for generator/enumerators
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
        case Some(ex) =>
          ex.accept(this)
        case None =>
      }
      super.visitExprInParent(expr)
    }

    override def visitPrefixExpression(p: ScPrefixExpr) {
      visitCall(p.operation, Some(p.operand), Nil, s => {
        val exprText = p.operation.refName + s
        ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, p.getContext, p)
      }, Map.empty, p)
      super.visitPrefixExpression(p)
    }

    override def visitPostfixExpression(p: ScPostfixExpr) {
      val qualifier = Some(p.operand)
      val resolve = p.operation.resolve()
      visitReferenceNoParameters(qualifier, resolve, p.operation, (s: String) => {
        val exprText = s + " " + p.operation.refName
        ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, p.getContext, p)
      })
      super.visitPostfixExpression(p)
    }

    override def visitReferenceExpression(ref: ScReferenceExpression) {
      val qualifier: Option[ScExpression] = ref.qualifier
      val resolve: PsiElement = ref.resolve()
      visitReferenceNoParameters(qualifier, resolve, ref, (s: String) => {
        val exprText = s + "." + ref.refName
        ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, ref.getContext, ref)
      })
      visitExpression(ref)
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
      super.visitIfStatement(stmt)
    }

    override def visitLiteral(l: ScLiteral) {
      myResult = l match {
        case interpolated: ScInterpolatedStringLiteral if interpolated.getType != InterpolatedStringType.STANDART =>
          throw EvaluateExceptionUtil.createEvaluateException("Only standart string interpolator s\"...\" is supported")
        case interpolated: ScInterpolatedStringLiteral if interpolated.getType == InterpolatedStringType.STANDART =>
          val evaluatorOpt = for (expr <- interpolated.getStringContextExpression) yield {
            expr.accept(this)
            myResult
          }
          evaluatorOpt.getOrElse(ScalaLiteralEvaluator(l))
        case _ if l.isSymbol =>
          val value = l.getValue.asInstanceOf[Symbol].name
          val expr = ScalaPsiElementFactory.createExpressionFromText(s"""Symbol("$value")""", l.getContext)
          expr.accept(this)
          myResult
        case _ => ScalaLiteralEvaluator(l)
      }
      super.visitLiteral(l)
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
      super.visitWhileStatement(ws)
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
      super.visitDoStatement(stmt)
    }

    private def evalThis(refOpt: Option[ScStableCodeReferenceElement], evaluator: Int => ScalaThisEvaluator,
                         stableEvaluator: Evaluator => Evaluator) {
      def defaults() {
        var contextClass = getContextClass
        var iterations = 0
        while (contextClass != null && !contextClass.isInstanceOf[PsiClass]) {
          contextClass = getContextClass(contextClass)
          iterations += anonClassCount(contextClass)
        }
        if (contextClass == null) myResult = evaluator(0)
        else myResult = evaluator(iterations)
      }
      refOpt match {
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
            iterations += anonClassCount(contextClass)
          }
          if (contextClass == null) myResult = evaluator(0)
          else myResult = evaluator(iterations)
        case Some(ref) =>
          val refName = ref.refName
          var contextClass = getContextClass
          var iterations = 0
          while (contextClass != null && (!contextClass.isInstanceOf[PsiClass] ||
            contextClass.asInstanceOf[PsiClass].name == null ||
            contextClass.asInstanceOf[PsiClass].name == refName)) {
            contextClass = getContextClass(contextClass)
            iterations += anonClassCount(contextClass)
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

    override def visitForExpression(forStmt: ScForStatement) {
      forStmt.getDesugarizedExpr match {
        case Some(expr) => expr.accept(this)
        case None => throw EvaluateExceptionUtil.createEvaluateException("Cannot desugarize for statement")
      }
      super.visitForExpression(forStmt)
    }

    override def visitBlockExpression(block: ScBlockExpr) {
      val evaluators = mutable.ListBuffer[Evaluator]()
      for (stmt <- block.statements) {
        stmt.accept(this)
        evaluators += myResult
      }
      myResult = new ScalaBlockExpressionEvaluator(evaluators.toSeq)
      super.visitBlockExpression(block)
    }

    override def visitTryExpression(tryStmt: ScTryStmt) {
      throw EvaluateExceptionUtil.createEvaluateException("Try expression is not supported")
    }

    override def visitReturnStatement(ret: ScReturnStmt) {
      throw EvaluateExceptionUtil.createEvaluateException("Return statement is not supported")
    }

    override def visitFunction(fun: ScFunction) {
      throw EvaluateExceptionUtil.createEvaluateException("Function definition is not supported")
    }

    override def visitThisReference(t: ScThisReference) {
      evalThis(t.reference, new ScalaThisEvaluator(_), e => e)
      super.visitThisReference(t)
    }

    override def visitSuperReference(t: ScSuperReference) {
      evalThis(t.reference, new ScalaSuperEvaluator(_), e => new ScalaSuperDelegate(e))
      super.visitSuperReference(t)
    }

    override def visitGenericCallExpression(call: ScGenericCall) {
      call.referencedExpr.accept(this)
      super.visitGenericCallExpression(call)
    }

    def evaluateLocalMethod(fun: ScFunctionDefinition, argEvaluators: Seq[Evaluator]) {
      //local method
      val name = NameTransformer.encode(fun.name)
      val containingClass = if (fun.isSynthetic) fun.containingClass else getContainingClass(fun)
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
            iterationCount += anonClassCount(outerClass)
            outerClass = getContextClass(outerClass)
          }
          if (outerClass != null)
            new ScalaThisEvaluator(iterationCount)
          else null
      }
      if (thisEvaluator != null) {
        val args = localParams(fun, getContextClass(fun))
        val evaluators = argEvaluators ++ args.map(arg => {
          val name = arg.asInstanceOf[PsiNamedElement].name
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
        myResult = new ScalaMethodEvaluator(
          thisEvaluator, name, DebuggerUtil.getFunctionJVMSignature(fun), evaluators, None,
          DebuggerUtil.getSourcePositions(fun.getNavigationElement), localFunctionIndex(fun))
        return
      }
      throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate local method")
    }

    private val BOXES_RUN_TIME = new TypeEvaluator(JVMNameUtil.getJVMRawText("scala.runtime.BoxesRunTime"))
    private val BOXED_UNIT = new TypeEvaluator(JVMNameUtil.getJVMRawText("scala.runtime.BoxedUnit"))
    private def boxEvaluator(eval: Evaluator): Evaluator = new BoxingEvaluator(eval)
    private def unboxEvaluator(eval: Evaluator): Evaluator = new UnBoxingEvaluator(eval)
    private def notEvaluator(eval: Evaluator): Evaluator = {
      unboxEvaluator(new ScalaMethodEvaluator(BOXES_RUN_TIME, "takeNot",
        JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)Ljava/lang/Object;"), Seq(boxEvaluator(eval))))
    }
    private def eqEvaluator(left: Evaluator, right: Evaluator): Evaluator = {
      new ScalaEqEvaluator(left, right)
    }
    private def neEvaluator(left: Evaluator, right: Evaluator): Evaluator = {
      notEvaluator(eqEvaluator(left, right))
    }
    private def unitEvaluator(): Evaluator = {
      new ScalaFieldEvaluator(BOXED_UNIT, _ => true, "UNIT")
    }
    private def unaryEvaluator(eval: Evaluator, boxesRunTimeName: String): Evaluator = {
      unboxEvaluator(new ScalaMethodEvaluator(BOXES_RUN_TIME, boxesRunTimeName,
        JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)Ljava/lang/Object;"), Seq(boxEvaluator(eval))))
    }
    private def binaryEvaluator(left: Evaluator, right: Evaluator, boxesRunTimeName: String): Evaluator = {
      unboxEvaluator(new ScalaMethodEvaluator(BOXES_RUN_TIME, boxesRunTimeName,
        JVMNameUtil.getJVMRawText("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
        Seq(boxEvaluator(left), boxEvaluator(right))))
    }

    private def evaluateSyntheticFunction(synth: ScSyntheticFunction, qual: Option[ScExpression], ref: ScReferenceExpression,
                                  argEvaluators: Seq[Evaluator]) {
      evaluateSyntheticFunctionForName(synth.name, qual, ref, argEvaluators)
    }

    private def evaluateSyntheticFunctionForName(name: String, qualOpt: Option[ScExpression], ref: ScReferenceExpression,
                                  argEvaluators: Seq[Evaluator]) {
      def unaryEval(operatorName: String, function: Evaluator => Evaluator) {
        if (argEvaluators.length == 0) {
          val eval = qualOpt match {
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
          val eval = qualOpt match {
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
          JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)I"), Seq(new BoxingEvaluator(eval))))
        case "==" =>
          binaryEval(name, (l, r) => new ScalaMethodEvaluator(BOXES_RUN_TIME, "equals",
            JVMNameUtil.getJVMRawText("(Ljava/lang/Object;Ljava/lang/Object;)Z"),
            Seq(new BoxingEvaluator(l), new BoxingEvaluator(r))))
        case "!=" =>
          binaryEval(name, (l, r) => new ScalaMethodEvaluator(BOXES_RUN_TIME, "equals",
            JVMNameUtil.getJVMRawText("(Ljava/lang/Object;Ljava/lang/Object;)Z"),
            Seq(new BoxingEvaluator(l), new BoxingEvaluator(r))))
          myResult = unaryEvaluator(myResult, "takeNot")
        case "unary_!" => unaryEvalForBoxes("!", "takeNot")
        case "unary_~" => unaryEvalForBoxes("~", "complement")
        case "unary_+" => unaryEvalForBoxes("+", "positive")
        case "unary_-" => unaryEvalForBoxes("-", "negate")
        case "eq" => binaryEval(name, eqEvaluator)
        case "ne" => binaryEval(name, neEvaluator)
        case "<" => binaryEvalForBoxes(name, "testLessThan")
        case ">" => binaryEvalForBoxes(name, "testGreaterThan")
        case ">=" => binaryEvalForBoxes(name, "testGreaterOrEqualThan")
        case "<=" => binaryEvalForBoxes(name, "testLessOrEqualThan")
        case "+" if qualOpt.map(_.getType(TypingContext.empty).getOrAny).filter(tp => {
          ScType.extractClass(tp) match {
            case Some(clazz) => clazz.qualifiedName == "java.lang.String"
            case _ => false
          }
        }) != None =>
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
            myResult = new ScalaMethodEvaluator(myResult, "clone", null/*todo*/, Nil)
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
            myResult = new ScalaMethodEvaluator(myResult, "toString", null/*todo*/, Nil)
          } else throw EvaluateExceptionUtil.createEvaluateException("Wrong number of parameters for Array.toString method")
        case _ =>
          throw EvaluateExceptionUtil.createEvaluateException("Array method not supported")
      }
    }

    private def isArrayFunction(fun: ScFunction): Boolean = {
      fun.getContext match {
        case tb: ScTemplateBody =>
          fun.containingClass match {
            case clazz: ScClass if clazz.qualifiedName == "scala.Array" => true
            case _ => false
          }
        case _ => false
      }
    }

    private def isClassOfFunction(fun: ScFunction): Boolean = {
      if (fun.name != "classOf") return false
      fun.getContext match {
        case tb: ScTemplateBody =>
          fun.containingClass match {
            case clazz: PsiClass if clazz.qualifiedName == "scala.Predef" => true
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
          val exprText = o.qualifiedName + "." + implicitFunction.name + "(" +
            qual.getText + ")"
          val expr = replaceWithImplicit(exprText)
          expr.accept(this)
          return
        case o: ScObject => //todo: It can cover many cases!
          throw EvaluateExceptionUtil.
            createEvaluateException("Implicit conversions from dependent objects are not supported")
        case _ => //from scope
          val exprText = implicitFunction.name + "(" + qual.getText + ")"
          val expr = replaceWithImplicit(exprText)
          expr.accept(this)
          return
      }
    }


    private def boxArguments(arguments: Seq[Evaluator], element: PsiElement): Seq[Evaluator] = {
      element match {
        case fun: ScMethodLike =>
          val res = new ArrayBuffer[Evaluator]
          var i = 0
          val params = fun.effectiveParameterClauses.map(_.parameters).flatten
          while (i < arguments.length) {
            val evaluator = arguments(i)
            if (params.length > i) {
              val param = params(i)
              import org.jetbrains.plugins.scala.lang.psi.types._
              res += (param.getType(TypingContext.empty).getOrAny match {
                case Boolean | Int | Char | Double | Float | Long | Byte | Short => evaluator
                case _ => boxEvaluator(evaluator)
              })
            } else res += evaluator
            i = i + 1
          }
          res.toSeq
        case method: PsiMethod =>
          val res = new ArrayBuffer[Evaluator]
          var i = 0
          val params = method.getParameterList.getParameters
          while (i < arguments.length) {
            val evaluator = arguments(i)
            if (params.length > i) {
              val param = params(i)
              import com.intellij.psi.PsiType._
              res += (param.getType match {
                case BOOLEAN | INT | CHAR | DOUBLE | FLOAT | LONG | BYTE | SHORT => evaluator
                case _ => boxEvaluator(evaluator)
              })
            } else res += evaluator
            i = i + 1
          }
          res.toSeq
        case _ => arguments
      }
    }

    private def functionEvaluator(qualOption: Option[ScExpression], ref: ScReferenceExpression,
                                  replaceWithImplicit: (String) => ScExpression, funName: String,
                                  argEvaluators: Seq[Evaluator], resolve: PsiElement) {
      ref.bind().foreach(r =>
        if (r.tuplingUsed) {
          throw EvaluateExceptionUtil.createEvaluateException("Tupling is unsupported. Use tuple expression.")
        }
      )
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
                  r.getActualElement match {
                    case o: ScObject if funName == "apply" =>
                      if (isStable(o)) {
                        myResult = stableObjectEvaluator(o)
                      } else {
                        val objName = NameTransformer.encode(o.name)
                        myResult = new ScalaMethodEvaluator(myResult, objName, null /* todo? */, Seq.empty,
                          traitImplementation(o), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                      }
                    case _ =>
                  }
                  val name = NameTransformer.encode(funName)
                  val signature = resolve match {
                    case fun: ScFunction => DebuggerUtil.getFunctionJVMSignature(fun)
                    case _ => null
                  }
                  myResult = new ScalaMethodEvaluator(myResult, name, signature, boxArguments(argEvaluators, resolve),
                    traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                  return
              }
            case _ =>
              //resolve not null => shouldn't be
              throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate method")
          }
        case None =>
          ref.bind() match {
            case Some(r: ScalaResolveResult) =>
              var evaluator = thisOrImportedQualifierEvaluator(ref, r)
              r.getActualElement match {
                case o: ScObject if funName == "apply" =>
                  if (isStable(o)) {
                    evaluator = stableObjectEvaluator(o)
                  } else {
                    val objName = NameTransformer.encode(o.name)
                    evaluator = new ScalaMethodEvaluator(evaluator, objName, null /* todo? */, Seq.empty,
                      traitImplementation(o), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                  }
                case _ =>
              }
              val name = NameTransformer.encode(funName)
              val signature = resolve match {
                case fun: ScFunction => DebuggerUtil.getFunctionJVMSignature(fun)
                case _ => null
              }
              myResult = new ScalaMethodEvaluator(evaluator, name, signature, boxArguments(argEvaluators, resolve),
                traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
              return
            case _ =>
              //resolve not null => shouldn't be
              throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate method")
          }
      }
    }

    private def argumentEvaluators(fun: ScFunction, matchedParameters: Map[Parameter, Seq[ScExpression]],
                               expr: ScExpression, qualOption: Option[ScExpression], ref: ScReferenceExpression,
                               replaceWithImplicit: (String) => ScExpression, resolve: PsiElement,
                               arguments: Seq[ScExpression]): Seq[Evaluator] = {
      val implicitParameters = fun.effectiveParameterClauses.lastOption.toSeq.flatMap(
        (clause: ScParameterClause) => {
          if (clause.isImplicit) clause.parameters
          else Seq.empty
        })
      val parameters = fun.effectiveParameterClauses.flatMap(_.parameters).map(new Parameter(_))
      var argEvaluators: Seq[Evaluator] = fun.effectiveParameterClauses.foldLeft(Seq.empty[Evaluator])(
        (seq: Seq[Evaluator], clause: ScParameterClause) => seq ++ clause.parameters.map {
          case param =>
            val p = new Parameter(param)
            val e = matchedParameters.find(_._1.name == p.name).map(_._2).getOrElse(Seq.empty).filter(_ != null)
            if (p.isByName) throw EvaluateExceptionUtil.createEvaluateException("cannot evaluate methods with by-name parameters")
            if (p.isRepeated) {
              val argTypeText = e.headOption.map(_.getType()).map(_.get).getOrElse(Any).canonicalText
              val argsText = if (e.length > 0) e.sortBy(_.getTextRange.getStartOffset).map(_.getText).mkString(".+=(", ").+=(", ").result()") else ""
              def tail: Evaluator = {
                val exprText = s"_root_.scala.collection.Seq.newBuilder[$argTypeText]$argsText"
                val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, expr.getContext, expr)
                newExpr.accept(this)
                myResult
              }
              if (e.length == 1) {
                e(0) match {
                  case t: ScTypedStmt if t.isSequenceArg =>
                    t.expr.accept(this)
                    myResult
                  case _ => tail
                }
              } else tail
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
                  if (s(i) == null)
                    throw EvaluateExceptionUtil.createEvaluateException("cannot find implicit parameters to pass")
                  s(i) match {
                    case ScalaResolveResult(clazz: ScTrait, substitutor)
                      if clazz.qualifiedName == "scala.reflect.ClassManifest" =>
                      val argType = substitutor.subst(clazz.getType(TypingContext.empty).get)
                      argType match {
                        case ScParameterizedType(tp, Seq(paramType)) =>
                          import org.jetbrains.plugins.scala.lang.psi.types._
                          def text(scType: ScType): String = scType match {
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
                            case JavaArrayType(arg) =>
                              "_root_.scala.reflect.ClassManifest.arrayType(" + text(arg) + ")"
                            case ScParameterizedType(ScDesignatorType(clazz: ScClass), Seq(arg))

                              if clazz.qualifiedName == "scala.Array" =>
                              "_root_.scala.reflect.ClassManifest.arrayType(" + text(arg) + ")"
                            /*case ScParameterizedType(des, args) =>
                              ScType.extractClass(des, Option(expr.getProject)) match {
                                case Some(clazz) =>
                                  "_root_.scala.reflect.ClassManifest.classType(" +
                                case _ => "null"
                              }*/   //todo:
                            case _ => ScType.extractClass(scType, Option(expr.getProject)) match {
                              case Some(clss) => "_root_.scala.reflect.ClassManifest.classType(classOf[_root_." +
                                clss.qualifiedName + "])"
                              case _ => "_root_.scala.reflect.ClassManifest.classType(classOf[_root_.java.lang." +
                                "Object])"
                            }
                          }
                          val e = ScalaPsiElementFactory.createExpressionWithContextFromText(text(paramType),
                            expr.getContext, expr)
                          e.accept(this)
                          myResult
                        case _ =>
                          throw EvaluateExceptionUtil.createEvaluateException("cannot find implicit parameters to pass")
                      }
                    case ScalaResolveResult(clazz: ScTrait, substitutor)
                      if clazz.qualifiedName == "scala.reflect.ClassTag" =>
                      val argType = substitutor.subst(clazz.getType(TypingContext.empty).get)
                      argType match {
                        case ScParameterizedType(tp, Seq(arg)) =>
                          import org.jetbrains.plugins.scala.lang.psi.types._
                          def text(arg: ScType): String = arg match {
                            case Short => "_root_.scala.reflect.ClassTag.Short"
                            case Byte => "_root_.scala.reflect.ClassTag.Byte"
                            case Char => "_root_.scala.reflect.ClassTag.Char"
                            case Int => "_root_.scala.reflect.ClassTag.Int"
                            case Long => "_root_.scala.reflect.ClassTag.Long"
                            case Float => "_root_.scala.reflect.ClassTag.Float"
                            case Double => "_root_.scala.reflect.ClassTag.Double"
                            case Boolean => "_root_.scala.reflect.ClassTag.Boolean"
                            case Unit => "_root_.scala.reflect.ClassTag.Unit"
                            case Any => "_root_.scala.reflect.ClassTag.Any"
                            case AnyVal => "_root_.scala.reflect.ClassTag.AnyVal"
                            case Nothing => "_root_.scala.reflect.ClassTag.Nothing"
                            case Null => "_root_.scala.reflect.ClassTag.Null"
                            case Singleton => "_root_.scala.reflect.ClassTag.Object"
                            //todo:
                            case _ => "_root_.scala.reflect.ClassTag.apply(classOf[_root_.java.lang.Object])"
                          }
                          val e = ScalaPsiElementFactory.createExpressionWithContextFromText(text(arg),
                            expr.getContext, expr)
                          e.accept(this)
                          myResult
                        case _ =>
                          throw EvaluateExceptionUtil.createEvaluateException("cannot find implicit parameters to pass")
                      }
                    case ScalaResolveResult(elem, _) =>
                      val context = ScalaPsiUtil.nameContext(elem)
                      val clazz = context.getContext match {
                        case _: ScTemplateBody | _: ScEarlyDefinitions =>
                          ScalaPsiUtil.getContextOfType(context, true, classOf[PsiClass])
                        case _ if context.isInstanceOf[ScClassParameter] =>
                          ScalaPsiUtil.getContextOfType(context, true, classOf[PsiClass])
                        case _ => null
                      }
                      clazz match {
                        case o: ScObject if isStable(o) =>
                          val exprText = o.qualifiedName + "." + elem.name
                          val e = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText,
                            expr.getContext, expr)
                          e.accept(this)
                          myResult
                        case o: ScObject => //todo: It can cover many cases!
                          throw EvaluateExceptionUtil.
                            createEvaluateException("Implicit parameters from dependent objects are not supported")
                        case _ => //from scope
                          val exprText = elem.name
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
              val methodName = defaultParameterMethodName(fun, p, parameters)
              functionEvaluator(qualOption, ref, replaceWithImplicit, methodName, seq, resolve)
              myResult
            } else null
        })
      if (argEvaluators.contains(null))
        argEvaluators = arguments.map(arg => {
          arg.accept(this)
          myResult
        })
      argEvaluators
    }

    private def localFunctionIndex(fun: ScFunction): Int = {
      val containingClass = getContainingClass(fun)
      import org.jetbrains.plugins.scala.extensions._
      val sameNameLocalFunctions = containingClass.depthFirst.collect{case f: ScFunction if f.isLocal && f.name == fun.name => f}.toList
      sameNameLocalFunctions.indexOf(fun) + 1
    }

    private def defaultParameterMethodName(fun: ScFunction, p: Parameter, parameters: Seq[Parameter]): String = {
      val suffix: String = if (!fun.isLocal) "" else "$" + localFunctionIndex(fun)
      fun.name + "$default$" + (parameters.indexOf(p) + 1) + suffix
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
        case fun: ScFunctionDefinition if isLocalFunction(fun) =>
          evaluateLocalMethod(fun,
            argumentEvaluators(fun, matchedParameters, expr, qualOption, ref, replaceWithImplicit, resolve, arguments))
        case fun: ScFunction if isClassOfFunction(fun) =>
          val clazzJVMName = expr.getContext match {
            case gen: ScGenericCall =>
              gen.arguments.apply(0).getType(TypingContext.empty).map(tp => {
                ScType.extractClass(tp, Some(ref.getProject)) match {
                  case Some(clazz) =>
                    DebuggerUtil.getClassJVMName(clazz)
                  case None => null
                }
              }).getOrElse(null)
            case _ => null
          }
          import org.jetbrains.plugins.scala.lang.psi.types.Null
          if (clazzJVMName != null)
            myResult = new ClassObjectEvaluator(new TypeEvaluator(clazzJVMName))
          else myResult = new ScalaLiteralEvaluator(null, Null)
        case synth: ScSyntheticFunction if synth.isStringPlusMethod && arguments.length == 1=>
          val qualText = qualOption.fold("this")(_.getText)
          val exprText = s"($qualText).concat(_root_.java.lang.String.valueOf(${arguments(0).getText})"
          val expr = ScalaPsiElementFactory.createExpressionFromText(exprText, ref.getManager)
          expr.accept(this)
          myResult
        case synth: ScSyntheticFunction =>
          evaluateSyntheticFunction(synth, qualOption, ref, argEvaluators) //todo: use matched parameters
        case fun: ScFunction if isArrayFunction(fun) =>
          evaluateArrayMethod(fun.name,  qualOption,
            argumentEvaluators(fun, matchedParameters, expr, qualOption, ref, replaceWithImplicit, resolve, arguments))
        case fun: ScFunction =>
          val argEvaluators: Seq[Evaluator] =
            argumentEvaluators(fun, matchedParameters, expr, qualOption, ref, replaceWithImplicit, resolve, arguments)
          functionEvaluator(qualOption, ref, replaceWithImplicit, fun.name, argEvaluators, resolve)
        case method: PsiMethod => //here you can use just arguments
          (ref.bind(), qualOption) match {
            case (Some(r: ScalaResolveResult), Some(qual)) if r.implicitFunction.isDefined =>
              replaceWithImplicitFunction(r.implicitFunction.get, qual, replaceWithImplicit)
            case (_, Some(literal: ScLiteral)) =>
              val litEval = boxEvaluator(ScalaLiteralEvaluator(literal))
              myResult = ScalaMethodEvaluator(litEval, method.name, JVMNameUtil.getJVMSignature(method), boxArguments(argEvaluators, method),
                traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
            case (_, Some(qual)) if method.hasModifierPropertyScala("static") =>
              val eval = new TypeEvaluator(JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(method)))
              val name = method.name
              myResult = ScalaMethodEvaluator(eval, name, JVMNameUtil.getJVMSignature(method), boxArguments(argEvaluators, method),
                traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
            case (_, Some(qual)) =>
              qual.accept(this)
              val name = method.name
              myResult = new ScalaMethodEvaluator(myResult, name, JVMNameUtil.getJVMSignature(method),
                boxArguments(argEvaluators, method),
                traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
            case (Some(r: ScalaResolveResult), None) =>
              val evaluator = thisOrImportedQualifierEvaluator(ref, r)
              val name = method.name
              myResult = new ScalaMethodEvaluator(evaluator, name, JVMNameUtil.getJVMSignature(method),
                boxArguments(argEvaluators, method),
                traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
            case _ =>  //resolve not null => shouldn't be
              throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate method")
          }
        case _ =>
          val argEvaluators: Seq[Evaluator] = arguments.map(arg => {
            arg.accept(this)
            myResult
          })
          qualOption match {
            case Some(qual) =>
              qual.accept(this)
              val name = NameTransformer.encode(ref.refName)
              myResult = new ScalaMethodEvaluator(myResult, name, null, argEvaluators)
            case None =>
              val evaluator = new ScalaThisEvaluator()
              val name = NameTransformer.encode(ref.refName)
              myResult = new ScalaMethodEvaluator(evaluator, name, null, argEvaluators)
          }
      }
    }

    override def visitMatchStatement(ms: ScMatchStmt) {
      throw EvaluateExceptionUtil.createEvaluateException("Match statement is not supported")
    }

    override def visitThrowExpression(throwStmt: ScThrowStmt) {
      throw EvaluateExceptionUtil.createEvaluateException("Throw statement is not supported")
    }

    override def visitAssignmentStatement(stmt: ScAssignStmt) {
      if (stmt.isNamedParameter) {
        stmt.getRExpression match {
          case Some(expr) =>
            expr.accept(this)
          case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate assign statement without expression")
        }
      } else {
        stmt.getLExpression match {
          case call: ScMethodCall =>
            val invokedText = call.getInvokedExpr.getText
            val rExprText = stmt.getRExpression.fold("null")(_.getText)
            val args = (call.args.exprs.map(_.getText) :+ rExprText).mkString("(", ", ", ")")
            val exprText = s"($invokedText).update$args"
            val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, stmt.getContext, stmt)
            expr.accept(this)
          case _ =>
            stmt.getLExpression.accept(this)
            val leftEvaluator = myResult
            stmt.getRExpression match {
              case Some(expr) => expr.accept(this)
              case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate assign statement without expression")
            }
            val rightEvaluator = myResult
            def createAssignEvaluator(leftEvaluator: Evaluator): Boolean = {
              leftEvaluator match {
                case m: ScalaMethodEvaluator =>
                  myResult = m.copy(_methodName = m.methodName + "_$eq", argumentEvaluators = Seq(rightEvaluator)) //todo: signature?
                  true
                case ScalaDuplexEvaluator(first, second) => createAssignEvaluator(first) || createAssignEvaluator(second)
                case _ => myResult = new AssignmentEvaluator(leftEvaluator, rightEvaluator)
                  false
              }
            }
            createAssignEvaluator(leftEvaluator)
        }
      }
    }

    override def visitTypedStmt(stmt: ScTypedStmt) {
      stmt.expr.accept(this)
      super.visitTypedStmt(stmt)
    }

    override def visitMethodCallExpression(parentCall: ScMethodCall) {
      @tailrec
      def collectArguments(call: ScMethodCall, collected: Seq[ScExpression] = Seq.empty, tailString: String = "",
                           matchedParameters: Map[Parameter, Seq[ScExpression]] = Map.empty) {
        if (call.isApplyOrUpdateCall) {
          if (!call.isUpdateCall) {
            val newExprText = s"(${call.getInvokedExpr.getText}).apply${call.args.getText}$tailString"
            val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(newExprText, call.getContext, call)
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
          case gen: ScGenericCall =>
            gen.referencedExpr match {
              case ref: ScReferenceExpression if ref.resolve().isInstanceOf[PsiMethod] =>
                visitCall(ref, ref.qualifier, call.argumentExpressions ++ collected, s => {
                  val exprText = s + "." + ref.refName + call.args.getText + tailString
                  ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, parentCall.getContext,
                    parentCall)
                }, matchedParameters ++ call.matchedParametersMap, parentCall)
              case ref: ScReferenceExpression =>
                ref.getType().getOrAny match {
                  //isApplyOrUpdateCall does not work for generic calls
                  case ScType.ExtractClass(psiClass) if psiClass.findMethodsByName("apply", true).nonEmpty =>
                    val typeArgsText = gen.typeArgs.fold("")(_.getText)
                    val newExprText = s"(${ref.getText}).apply$typeArgsText${call.args.getText}$tailString"
                    val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(newExprText, call.getContext, call)
                    expr.accept(this)
                  case _ => throw EvaluateExceptionUtil.createEvaluateException("Method call is invalid")
                }
              case _ =>
                throw EvaluateExceptionUtil.createEvaluateException("Method call is invalid")

            }
          case _ => throw EvaluateExceptionUtil.createEvaluateException("Method call is invalid")
        }
      }
      collectArguments(parentCall)
      super.visitMethodCallExpression(parentCall)
    }

    override def visitInfixExpression(infix: ScInfixExpr) {
      val operation = infix.operation
      def isUpdate(ref: ScReferenceExpression): Boolean = {
        ref.refName.endsWith("=") &&
                (ref.resolve() match {
                  case n: PsiNamedElement if n.name + "=" == ref.refName => true
                  case _ => false
                })
      }

      if (isUpdate(operation)) {
        val baseExprText = infix.getBaseExpr.getText
        val operationText = operation.refName.dropRight(1)
        val argText = infix.getArgExpr.getText
        val exprText = s"$baseExprText = $baseExprText $operationText $argText"
        val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, infix.getContext, infix)
        expr.accept(this)
      } else {
        val arguments = infix.getArgExpr match {
          case u: ScUnitExpr => Nil
          case b: ScBlock if b.statements.isEmpty => Nil
          case _ => infix.argumentExpressions
        }
        val argumentsText = if (arguments.nonEmpty) infix.getArgExpr.getText else ""
        visitCall(operation, Some(infix.getBaseExpr), arguments, s => {
          val exprText = s"$s ${operation.refName} $argumentsText"
          ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, infix.getContext, infix)
        }, infix.matchedParametersMap, infix)
      }
      super.visitInfixExpression(infix)
    }

    override def visitExpression(expr: ScExpression) {
      //check underscores
      if (ScUnderScoreSectionUtil.isUnderscoreFunction(expr)) {
        throw EvaluateExceptionUtil.createEvaluateException("Anonymous functions are not supported")
      }

      //unwrap implicit conversions
      expr.getImplicitConversions(fromUnder = true)._2 match {
        case Some(fun: ScFunction) =>
          val clazz = fun.containingClass
          val className = if (clazz != null) clazz.qualifiedName + "." else ""
          val newExprText = s"$className${fun.name}(${expr.getText})"
          val newExpr = ScalaPsiElementFactory.createExpressionFromText(newExprText, expr)
          newExpr.accept(this)
          return
        case _ =>
      }

      //boxing and unboxing actions
      def unbox(typeTo: String) {myResult = unaryEvaluator(unboxEvaluator(myResult), typeTo)}
      def box() {myResult = boxEvaluator(myResult)}
      
      import org.jetbrains.plugins.scala.lang.psi.types._
      expr.smartExpectedType() match {
        case Some(Int) => unbox("toInteger")
        case Some(Byte) => unbox("toByte")
        case Some(Long) => unbox("toLong")
        case Some(Boolean) => myResult = unboxEvaluator(myResult)
        case Some(Float) => unbox("toFloat")
        case Some(Short) => unbox("toShort")
        case Some(Double) => unbox("toDouble")
        case Some(Char) => unbox("toCharacter")
        case Some(Unit) => myResult = new BlockStatementEvaluator(Array(myResult, unitEvaluator()))
        case None => //nothing to do
        case _ => box()
      }
    }

    @tailrec
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
          obj.qualifiedName + ".package"
        else obj.getQualifiedNameForDebugger
      val qual = qualName.split('.').map(NameTransformer.encode).mkString(".") + "$"
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
        outerClass = getContextClass(outerClass)
        iterationCount += anonClassCount(outerClass)
      }

      if (outerClass != null)
        new ScalaThisEvaluator(iterationCount)
      else new ScalaThisEvaluator()
    }

    private def traitImplementation(elem: PsiElement): Option[JVMName] = {
      val clazz = getContextClass(elem)
      clazz match {
        case t: ScTrait =>
          Some(DebuggerUtil.getClassJVMName(t, withPostfix = true))
        case _ => None
      }
    }

    private def isLocalFunction(fun: ScFunction): Boolean = {
      !fun.getContext.isInstanceOf[ScTemplateBody]
    }

    private def isInsideLocalFunction(elem: PsiElement): Option[ScFunction] = {
      @tailrec
      def inner(element: PsiElement): Option[ScFunction] = {
        element match {
          case fun: ScFunction
            if isLocalFunction(fun) && !fun.parameters.exists(param => PsiTreeUtil.isAncestor(param, elem, false)) => Some(fun)
          case other if other.getContext != null => inner(other.getContext)
          case _ => None
        }
      }
      inner(elem)
    }

    private def visitReferenceNoParameters(qualifier: Option[ScExpression],
                                           resolve: PsiElement,
                                           ref: ScReferenceExpression, replaceWithImplicit: String => ScExpression) {
      val isLocalValue = isLocalV(resolve)
      val fileName = myContextClass.toOption.flatMap(_.getContainingFile.toOption).map(_.name).orNull

      def evaluateFromParameter(fun: PsiElement, resolve: PsiElement): Evaluator = {
        val name = NameTransformer.encode(resolve.asInstanceOf[PsiNamedElement].name)
        val evaluator = new ScalaLocalVariableEvaluator(name, fileName)
        fun match {
          case funDef: ScFunctionDefinition =>
            def paramIndex(fun: ScFunctionDefinition, context: PsiElement, elem: PsiElement): Int = {
              val locIndex = localParams(fun, context).indexOf(elem)
              val funParams = fun.effectiveParameterClauses.flatMap(_.parameters)
              if (locIndex < 0) funParams.indexOf(elem)
              else locIndex + funParams.size
            }
            val pIndex = paramIndex(funDef, getContextClass(fun), resolve)
            evaluator.setParameterIndex(pIndex)
            evaluator.setMethodName(funDef.name)
          case funExpr: ScFunctionExpr =>
            evaluator.setParameterIndex(funExpr.parameters.indexOf(resolve))
            evaluator.setMethodName("apply")
          case _ => throw EvaluateExceptionUtil.createEvaluateException("Evaluation from parameter not from function definition or function expression")
        }
        myResult = evaluator
        myResult
      }

      def calcLocal(): Boolean = {
        val labeledValue = resolve.getUserData(CodeFragmentFactoryContextWrapper.LABEL_VARIABLE_VALUE_KEY)
        if (labeledValue != null) {
          myResult = new IdentityEvaluator(labeledValue)
          return true
        }

        val isObject = resolve.isInstanceOf[ScObject]

        val namedElement = resolve.asInstanceOf[PsiNamedElement]
        val name = NameTransformer.encode(namedElement.name) + (if (isObject) "$module" else "")
        val containingClass = getContainingClass(namedElement)

        def evaluateLocalVariable(): Evaluator = {
          ScalaPsiUtil.nameContext(namedElement) match {
            case param: ScParameter =>
              param.owner match {
                case fun @ (_: ScFunction | _: ScFunctionExpr) => evaluateFromParameter(fun, param)
                case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate parameter")
              }
            case caseCl: ScCaseClause =>
              if (caseCl.getParent != null) {
                val pattern = caseCl.pattern
                if (pattern.isEmpty) throw EvaluateExceptionUtil.createEvaluateException("Cannot find pattern of case clause")
                caseCl.getParent.getParent match {
                  case matchStmt: ScMatchStmt if namedElement.isInstanceOf[ScPattern] =>
                    val expr = matchStmt.expr
                    if (expr.isEmpty) throw EvaluateExceptionUtil.createEvaluateException("Cannot find expression of match statement")
                    expr.get.accept(this)
                    evaluateSubpatternFromPattern(myResult, pattern.get, namedElement.asInstanceOf[ScPattern])
                    myResult = new ScalaDuplexEvaluator(new ScalaLocalVariableEvaluator(name, fileName), myResult)
                  case block: ScBlockExpr => //it is anonymous function
                    val argEvaluator = new ScalaLocalVariableEvaluator("", fileName)
                    argEvaluator.setMethodName("apply")
                    argEvaluator.setParameterIndex(0)
                    val fromPatternEvaluator = evaluateSubpatternFromPattern(argEvaluator, pattern.get, namedElement.asInstanceOf[ScPattern])
                    myResult = new ScalaDuplexEvaluator(new ScalaLocalVariableEvaluator(name, fileName), fromPatternEvaluator)
                  case _ =>  myResult = new ScalaLocalVariableEvaluator(name, fileName)
                }
              } else throw EvaluateExceptionUtil.createEvaluateException("Invalid case clause")
            case _ => myResult = new ScalaLocalVariableEvaluator(name, fileName)
          }
          if (isObject) {
            myResult = new ScalaFieldEvaluator(myResult, ref => true, "elem") //get from VolatileObjectReference
          }
          myResult
        }

        getContextClass match {
          case null | `containingClass` =>
            evaluateLocalVariable()
            true
          case _ =>
            var iterationCount = 0
            var positionClass = getContextClass
            var outerClass = getContextClass(getContextClass)
            while (outerClass != null && outerClass != containingClass) {
              iterationCount += anonClassCount(outerClass)
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
              myResult = new ScalaDuplexEvaluator(myResult, evaluateLocalVariable())
              true
            } else throw EvaluateExceptionUtil.createEvaluateException("Cannot load local variable from anonymous class")
        }
      }

      resolve match {
        case _ if isLocalValue && isInsideLocalFunction(resolve) == None =>
          calcLocal()
        case _ if isLocalValue =>
          val fun = isInsideLocalFunction(resolve).get
          calcLocal()
          myResult = new ScalaDuplexEvaluator(myResult, evaluateFromParameter(fun, resolve))
        case o: ScObject =>
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
                    case _ =>
                      qual.accept(this)
                      val name = NameTransformer.encode(obj.name)
                      myResult = new ScalaMethodEvaluator(myResult, name, null /* todo? */, Seq.empty,
                        traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                  }
                case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot resolve evaluated reference")
              }
            case None =>
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  val evaluator = thisOrImportedQualifierEvaluator(ref, r)
                  val name = NameTransformer.encode(obj.name)
                  myResult = new ScalaMethodEvaluator(evaluator, name, null /* todo? */, Seq.empty,
                    traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot resolve evaluated reference")
              }
          }
        case _: PsiMethod | _: ScSyntheticFunction =>
          visitCall(ref, qualifier, Nil, replaceWithImplicit, Map.empty, ref)
        case c: ScClassParameter if c.isPrivateThis =>
          //this is field if it's used outside of initialization
          //name of this field ends with $$ + c.getName
          //this is scala "field"
          val named = resolve.asInstanceOf[ScNamedElement]
          qualifier match {
            case Some(qual) =>
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  r.implicitFunction match {
                    case Some(fun) =>
                      replaceWithImplicitFunction(fun, qual, replaceWithImplicit)
                    case _ =>
                      qual.accept(this)
                      val name = NameTransformer.encode(named.name)
                      myResult = new ScalaFieldEvaluator(myResult, _ => true, name, true)
                  }
                case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot resolve evaluated reference")
              }
            case None =>
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  val evaluator = thisOrImportedQualifierEvaluator(ref, r)
                  val name = NameTransformer.encode(named.name)
                  myResult = new ScalaFieldEvaluator(evaluator, _ => true, name, true)
                case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot resolve evaluated reference")
              }
          }
        case _: ScClassParameter | _: ScBindingPattern =>
          //this is scala "field"
          val named = resolve.asInstanceOf[ScNamedElement]
          qualifier match {
            case Some(qual) =>
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  r.implicitFunction match {
                    case Some(fun) =>
                      replaceWithImplicitFunction(fun, qual, replaceWithImplicit)
                    case _ =>
                      qual.accept(this)
                      val name = NameTransformer.encode(named.name)
                      myResult = new ScalaMethodEvaluator(myResult, name, null /* todo */, Seq.empty,
                        traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                  }
                case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot resolve evaluated reference")
              }
            case None =>
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  val evaluator = thisOrImportedQualifierEvaluator(ref, r)
                  val name = NameTransformer.encode(named.name)
                  myResult = new ScalaMethodEvaluator(evaluator, name, null/* todo */, Seq.empty,
                    traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                  getContextClass(named) match {
                    //in some cases compiler uses full qualified names for fields and methods
                    case clazz: ScTemplateDefinition if ScalaPsiUtil.hasStablePath(clazz)
                            && clazz.members.contains(ScalaPsiUtil.nameContext(named)) =>
                      val qualName = clazz.qualifiedName
                      val newName = qualName.split('.').map(NameTransformer.encode).mkString("$") + "$$" + name
                      val reserveEval = new ScalaMethodEvaluator(evaluator, newName, null/* todo */, Seq.empty,
                        traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
                      myResult = new ScalaDuplexEvaluator(myResult, reserveEval)
                    case _ =>
                  }
                case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot resolve evaluated reference")
              }
          }
        case field: PsiField =>
          qualifier match {
            case Some(qual) =>
              if (field.hasModifierPropertyScala("static")) {
                val eval =
                  new TypeEvaluator(JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(field)))
                val name = field.name
                myResult = new ScalaFieldEvaluator(eval, ref => true,name)
              } else {
                ref.bind() match {
                  case Some(r: ScalaResolveResult) =>
                    r.implicitFunction match {
                      case Some(fun) =>
                        replaceWithImplicitFunction(fun, qual, replaceWithImplicit)
                      case _ =>
                        qual.accept(this)
                        val name = field.name
                        myResult = new ScalaFieldEvaluator(myResult,
                          ScalaFieldEvaluator.getFilter(getContainingClass(field)), name)
                    }
                  case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot resolve evaluated reference")
                }
              }
            case None =>
              ref.bind() match {
                case Some(r: ScalaResolveResult) =>
                  val evaluator = thisOrImportedQualifierEvaluator(ref, r)
                  val name = field.name
                  myResult = new ScalaFieldEvaluator(evaluator,
                    ScalaFieldEvaluator.getFilter(getContainingClass(field)), name)
                case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot resolve evaluated reference")
              }
          }
        case pack: ScPackage =>
          //let's try to find package object:
          val qual = (pack.getQualifiedName + ".package$").split('.').map(NameTransformer.encode).mkString(".")
          myResult = stableObjectEvaluator(qual)
        case _ =>
          //unresolved symbol => try to resolve it dynamically
          val name = NameTransformer.encode(ref.refName)
          qualifier match {
            case Some(qual) =>
              qual.accept(this)
              if (myResult == null) {
                throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate unresolved reference expression")
              }
              myResult = new ScalaFieldEvaluator(myResult, ref => true, name)
            case None =>
              myResult = new ScalaLocalVariableEvaluator(name, fileName)
          }
      }
    }

    override def visitVariableDefinition(varr: ScVariableDefinition) {
      throw EvaluateExceptionUtil.createEvaluateException("Evaluation of variables is not supported")
    }

    override def visitTupleExpr(tuple: ScTuple) {
      val exprText = "_root_.scala.Tuple" + tuple.exprs.length + tuple.exprs.map(_.getText).mkString("(", ", ", ")")
      val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, tuple.getContext, tuple)
      expr.accept(this)
      super.visitTupleExpr(tuple)
    }

    override def visitPatternDefinition(pat: ScPatternDefinition) {
      throw EvaluateExceptionUtil.createEvaluateException("Evaluation of values is not supported")
    }

    override def visitTypeDefintion(typedef: ScTypeDefinition) {
      throw EvaluateExceptionUtil.createEvaluateException("Evaluation of local classes is not supported")
    }

    override def visitNewTemplateDefinition(templ: ScNewTemplateDefinition) {
      templ.extendsBlock.templateBody match {
        case Some(tb) => throw EvaluateExceptionUtil.createEvaluateException("Anonymous classes are not supported")
        case _ =>
      }
      templ.extendsBlock.templateParents match {
        case Some(parents: ScClassParents) =>
          if (parents.typeElements.length != 1) {
            throw EvaluateExceptionUtil.createEvaluateException("Anonymous classes are not supported")
          }
          parents.constructor match {
            case Some(constr) =>
              val tp = constr.typeElement.calcType
              ScType.extractClass(tp, Some(templ.getProject)) match {
                case Some(clazz) if clazz.qualifiedName == "scala.Array" =>
                  val typeArgs = constr.typeArgList.fold("")(_.getText)
                  val args = constr.args.fold("(0)")(_.getText)
                  val exprText = s"_root_.scala.Array.ofDim$typeArgs$args"
                  val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, templ.getContext, templ)
                  expr.accept(this)
                case Some(clazz) =>
                  val jvmName = DebuggerUtil.getClassJVMName(clazz)
                  val typeEvaluator = new TypeEvaluator(jvmName)
                  val argumentEvaluators = constr.arguments.flatMap(_.exprs).map(expr => {
                    expr.accept(this)
                    myResult
                  }) //todo: make arguments better, like for method call
                  constr.reference match {
                    case Some(ref) =>
                      ref.resolveAllConstructors match {
                        case Array(ScalaResolveResult(named, subst)) =>
                          val methodSignature = named match {
                            case fun: ScFunction => DebuggerUtil.getFunctionJVMSignature(fun)
                            case constr: ScPrimaryConstructor => DebuggerUtil.getFunctionJVMSignature(constr)
                            case method: PsiMethod => JVMNameUtil.getJVMSignature(method)
                            case clazz: ScClass => clazz.constructor match {
                              case Some(cnstr) => DebuggerUtil.getFunctionJVMSignature(cnstr)
                              case _ => JVMNameUtil.getJVMRawText("()V")
                            }
                            case clazz: PsiClass => JVMNameUtil.getJVMRawText("()V")
                            case _ => JVMNameUtil.getJVMRawText("()V")
                          }
                          myResult = new ScalaMethodEvaluator(typeEvaluator, "<init>", methodSignature,
                            boxArguments(argumentEvaluators, named))
                        case _ =>
                          myResult = new ScalaMethodEvaluator(typeEvaluator, "<init>", null, argumentEvaluators)
                      }
                    case _ =>
                      myResult = new ScalaMethodEvaluator(typeEvaluator, "<init>", null, argumentEvaluators)
                  }
                case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate new expression without class reference")
              }

            case None => throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate expression without constructor call")
          }
        case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate expression without template parents")
      }
      
      super.visitNewTemplateDefinition(templ)
    }

    private def evaluateSubpatternFromPattern(exprEval: Evaluator, pattern: ScPattern, subPattern: ScPattern): Evaluator = {
      def evaluateConstructorOrInfix(exprEval: Evaluator, ref: ScStableCodeReferenceElement, pattern: ScPattern, nextPatternIndex: Int): Evaluator = {
        ref.resolve() match {
          case fun: ScFunctionDefinition =>
            val elem = ref.bind().get.getActualElement //object or case class
            visitReferenceNoParameters(None, elem,
              ScalaPsiElementFactory.createExpressionFromText(ref.getText, ref.getManager).asInstanceOf[ScReferenceExpression],
              (s: String) => {
              val exprText = s + "." + ref.refName
              ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, ref.getContext, ref)
            })
            val refEvaluator = myResult
            val funName = fun.name
            val newEval =
              if (funName == "unapply") {
                val extractEval = new ScalaMethodEvaluator(refEvaluator, funName, DebuggerUtil.getFunctionJVMSignature(fun), Seq(exprEval))
                if (pattern.subpatterns.length == 1) {
                  myResult = new ScalaMethodEvaluator(extractEval, "get", null, Seq.empty)
                  myResult
                }
                else if (pattern.subpatterns.length > 1) {
                  val getEval = new ScalaMethodEvaluator(extractEval, "get", null, Seq.empty)
                  val _nEval = new ScalaFieldEvaluator(getEval, x => true, s"_${nextPatternIndex + 1}")
                  myResult = _nEval
                  myResult
                }
                else throw EvaluateExceptionUtil.createEvaluateException("Cannot extract value from unapply without arguments")
              } else if (funName == "unapplySeq") {
                val extractEval = new ScalaMethodEvaluator(refEvaluator, funName, DebuggerUtil.getFunctionJVMSignature(fun), Seq(exprEval))
                val getEval = new ScalaMethodEvaluator(extractEval, "get", null, Seq.empty)
                ScalaPsiElementFactory.createExpressionFromText("" + nextPatternIndex, pattern.getManager).accept(this)
                val numberEval = myResult
                val applyEval = new ScalaMethodEvaluator(getEval, "apply", null, Seq(numberEval))
                myResult = applyEval
                myResult
              } else throw EvaluateExceptionUtil.createEvaluateException("Pattern reference does not resolve to unapply or unapplySeq")
            val nextPattern = pattern.subpatterns(nextPatternIndex)
            evaluateSubpatternFromPattern(newEval, nextPattern, subPattern)
          case _ => throw EvaluateExceptionUtil.createEvaluateException("Pattern reference does not resolve to unapply or unapplySeq")
        }
      }

      if (pattern == null || subPattern == null)
        throw new IllegalArgumentException("Patterns should not be null")
      val nextPatternIndex: Int = pattern.subpatterns.indexWhere(next => next == subPattern || subPattern.parents.contains(next))
      if (pattern == subPattern) {
        myResult = exprEval
        myResult
      }
      else if (nextPatternIndex < 0)
        throw new IllegalArgumentException("Pattern is not ancestor of subpattern")
      else {
        pattern match {
          case namingPattern: ScNamingPattern => evaluateSubpatternFromPattern(exprEval, namingPattern.named, subPattern)
          case typedPattern: ScTypedPattern => evaluateSubpatternFromPattern(exprEval, pattern.subpatterns.head, subPattern)
          case parenthesized: ScParenthesisedPattern =>
            val withoutPars = parenthesized.subpattern
                    .getOrElse(throw new IllegalStateException("Empty parentheses pattern"))
            evaluateSubpatternFromPattern(exprEval, withoutPars, subPattern)
          case tuplePattern: ScTuplePattern =>
            val nextPattern = tuplePattern.subpatterns(nextPatternIndex)
            val newEval = new ScalaFieldEvaluator(exprEval, x => true, s"_${nextPatternIndex + 1}")
            evaluateSubpatternFromPattern(newEval, nextPattern, subPattern)
          case constrPattern: ScConstructorPattern =>
            val ref: ScStableCodeReferenceElement = constrPattern.ref
            evaluateConstructorOrInfix(exprEval, ref, constrPattern, nextPatternIndex)
          case infixPattern: ScInfixPattern =>
            val ref: ScStableCodeReferenceElement = infixPattern.refernece
            evaluateConstructorOrInfix(exprEval, ref, infixPattern, nextPatternIndex)
            //todo: handle infix with tuple right pattern
          case composite: ScCompositePattern =>
            throw EvaluateExceptionUtil.createEvaluateException("Pattern alternatives cannot bind variables")
          case xmlPattern: ScXmlPattern =>
            throw EvaluateExceptionUtil.createEvaluateException("Xml patterns are not supported") //todo: xml patterns
          case _ => throw EvaluateExceptionUtil.createEvaluateException("This kind of patterns is not supported") //todo: xml patterns
        }
      }
    }

    private def importedQualifierEvaluator(ref: ScReferenceElement, resolveResult: ScalaResolveResult): Evaluator = {
      resolveResult.fromType match {
        case Some(ScDesignatorType(element)) =>
          element match {
            case obj: ScObject => stableObjectEvaluator(obj)
            case cl: PsiClass if cl.getLanguage.isInstanceOf[JavaLanguage] =>
              new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(cl))
            case _ =>
              val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(element.name, ref.getContext, ref)
              expr.accept(this)
              myResult
          }
        case Some(p: ScProjectionType) =>
          def exprToEvaluate(p: ScProjectionType): String = p.projected match {
            case ScDesignatorType(element) => element.name + "." + p.actualElement.name
            case projected: ScProjectionType => exprToEvaluate(projected) + "." + projected.actualElement.name
            case ScThisType(_) => "this." + p.actualElement.name
            case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate imported reference")
          }
          val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprToEvaluate(p), ref.getContext, ref)
          expr.accept(this)
          myResult
        case _ => throw EvaluateExceptionUtil.createEvaluateException("Cannot evaluate imported reference")
      }
    }

    private def thisOrImportedQualifierEvaluator(ref: ScReferenceElement, resolveResult: ScalaResolveResult): Evaluator = {
      if (resolveResult.importsUsed.nonEmpty) importedQualifierEvaluator(ref, resolveResult)
      else thisEvaluator(resolveResult)
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