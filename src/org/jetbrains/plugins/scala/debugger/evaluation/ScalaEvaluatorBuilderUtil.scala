package org.jetbrains.plugins.scala
package debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.CodeFragmentFactoryContextWrapper
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.debugger.engine.{JVMName, JVMNameUtil}
import com.intellij.lang.java.JavaLanguage
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScPatternDefinition, ScFunction, ScFunctionDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaRecursiveElementVisitor, ImplicitParametersOwner, ScPackage}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult

import scala.annotation.tailrec
import scala.reflect.NameTransformer

/**
* Nikolay.Tropin
* 2014-09-28
*/
private[evaluation] trait ScalaEvaluatorBuilderUtil {
  this: EvaluatorBuilderVisitor =>

  import org.jetbrains.plugins.scala.debugger.evaluation.ScalaEvaluatorBuilderUtil._

  def fileName = contextClass.toOption.flatMap(_.getContainingFile.toOption).map(_.name).orNull

  def importedQualifierEvaluator(ref: ScReferenceElement, resolveResult: ScalaResolveResult): Evaluator = {
    resolveResult.fromType match {
      case Some(ScDesignatorType(element)) =>
        element match {
          case obj: ScObject => stableObjectEvaluator(obj)
          case cl: PsiClass if cl.getLanguage.isInstanceOf[JavaLanguage] =>
            new TypeEvaluator(JVMNameUtil.getJVMQualifiedName(cl))
          case _ =>
            val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(element.name, ref.getContext, ref)
            ScalaEvaluator(expr)
        }
      case Some(p: ScProjectionType) =>
        def exprToEvaluate(p: ScProjectionType): String = p.projected match {
          case ScDesignatorType(elem) => elem.name + "." + p.actualElement.name
          case projected: ScProjectionType => exprToEvaluate(projected) + "." + projected.actualElement.name
          case ScThisType(_) => "this." + p.actualElement.name
          case _ => throw EvaluationException("Cannot evaluate imported reference")
        }
        val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprToEvaluate(p), ref.getContext, ref)
        ScalaEvaluator(expr)
      case _ => throw EvaluationException("Cannot evaluate imported reference")
    }
  }

  def thisOrImportedQualifierEvaluator(ref: ScReferenceElement): Evaluator = {
    ref.bind() match {
      case Some(resolveResult: ScalaResolveResult) =>
        if (resolveResult.importsUsed.nonEmpty) importedQualifierEvaluator(ref, resolveResult)
        else thisEvaluator(resolveResult)
      case None => new ScalaThisEvaluator()
    }
  }

  def thisEvaluator(resolveResult: ScalaResolveResult): Evaluator = {
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
    var outerClass: PsiElement = contextClass
    while (outerClass != null && outerClass != containingClass) {
      outerClass = getContextClass(outerClass)
      iterationCount += anonClassCount(outerClass)
    }

    if (outerClass != null)
      new ScalaThisEvaluator(iterationCount)
    else new ScalaThisEvaluator()
  }

  def thisOrSuperEvaluator(refOpt: Option[ScStableCodeReferenceElement], isSuper: Boolean): Evaluator = {

    def thisEval(i: Int) = if (isSuper) new ScalaSuperEvaluator(i) else new ScalaThisEvaluator(i)

    def stableEvaluator(e: Evaluator) = if (isSuper) new ScalaSuperDelegate(e) else e

    def default: Evaluator = {
      val (result, iters) = findContextClass(e => e == null || e.isInstanceOf[PsiClass])
      if (result == null) thisEval(0)
      else thisEval(iters)
    }

    refOpt match {
      case Some(ResolvesTo(clazz: PsiClass)) =>
        clazz match {
          case o: ScObject if isStable(o) => stableEvaluator(stableObjectEvaluator(o))
          case _ =>
            val (result, iters) = findContextClass(e => e == null || e == clazz)
            if (result == null) thisEval(0)
            else thisEval(iters)
        }
      case Some(ref) =>
        val refName = ref.refName
        val (result, iters) = findContextClass {
          case null => true
          case cl: PsiClass if cl.name != null && cl.name != refName => true
          case _ => false
        }

        result match {
          case o: ScObject if isStable(o) => stableEvaluator(stableObjectEvaluator(o))
          case null => default
          case _ => thisEval(iters)
        }
      case _ => default
    }
  }

  def findContextClass(condition: PsiElement => Boolean): (PsiElement, Int) = {
    var current: PsiElement = contextClass
    var iterations = 0
    while (!condition(current)) {
      current = getContextClass(current)
      iterations += anonClassCount(current)
    }
    (current, iterations)
  }

  def localMethodEvaluator(fun: ScFunctionDefinition, argEvaluators: Seq[Evaluator]): Evaluator = {
    val name = NameTransformer.encode(fun.name)
    val containingClass = if (fun.isSynthetic) fun.containingClass else getContainingClass(fun)
    if (contextClass == null) {
      throw EvaluationException("Cannot evaluate local method")
    }
    val thisEvaluator: Evaluator = containingClass match {
      case obj: ScObject if isStable(obj) =>
        stableObjectEvaluator(obj)
      case _ =>
        val (outerClass, iters) = findContextClass(e => e == null || e == containingClass)

        if (outerClass != null) new ScalaThisEvaluator(iters)
        else null
    }
    if (thisEvaluator != null) {
      val locals = DebuggerUtil.localParamsForFunDef(fun)
      val evaluators = argEvaluators ++ locals.map(fromLocalArgEvaluator)
      val signature = DebuggerUtil.getFunctionJVMSignature(fun)
      val positions = DebuggerUtil.getSourcePositions(fun.getNavigationElement)
      val idx = localFunctionIndex(fun)
      new ScalaMethodEvaluator(thisEvaluator, name, signature, evaluators, None, positions, idx)
    }
    else throw EvaluationException("Cannot evaluate local method")
  }


  def stableObjectEvaluator(qual: String): ScalaFieldEvaluator = {
    val jvm = JVMNameUtil.getJVMRawText(qual)
    new ScalaFieldEvaluator(new TypeEvaluator(jvm), ref => ref.name() == qual, "MODULE$")
  }

  def stableObjectEvaluator(obj: ScObject): Evaluator = {
    val qualName =
      if (obj.isPackageObject)
        obj.qualifiedName + ".package"
      else obj.getQualifiedNameForDebugger
    val qual = qualName.split('.').map(NameTransformer.encode).mkString(".") + "$"
    stableObjectEvaluator(qual)
  }

  def objectEvaluator(obj: ScObject, qualEvaluator: () => Evaluator): Evaluator = {
    if (isStable(obj)) stableObjectEvaluator(obj)
    else {
      val objName = NameTransformer.encode(obj.name)
      new ScalaMethodEvaluator(qualEvaluator(), objName, null /* todo? */, Seq.empty,
        traitImplementation(obj), DebuggerUtil.getSourcePositions(obj.getNavigationElement))
    }
  }

  def syntheticFunctionEvaluator(synth: ScSyntheticFunction,
                                 qualOpt: Option[ScExpression],
                                 ref: ScReferenceExpression,
                                 arguments: Seq[ScExpression]): Evaluator = {


    if (synth.isStringPlusMethod && arguments.length == 1) {
      val qualText = qualOpt.fold("this")(_.getText)
      val exprText = s"($qualText).concat(_root_.java.lang.String.valueOf(${arguments(0).getText}))"
      val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, ref.getContext, ref)
      return ScalaEvaluator(expr)
    }

    val name = synth.name
    val argEvaluators = arguments.map(ScalaEvaluator(_))

    def unaryEval(operatorName: String, function: Evaluator => Evaluator): Evaluator = {
      if (argEvaluators.length == 0) {
        val eval = qualOpt match {
          case None => new ScalaThisEvaluator()
          case Some(qual) => ScalaEvaluator(qual)
        }
        function(eval)
      } else throw EvaluationException(s"Wrong number of arguments for method '$operatorName'")
    }
    def unaryEvalForBoxes(operatorName: String, boxesName: String): Evaluator = {
      unaryEval(operatorName, unaryEvaluator(_, boxesName))
    }
    def binaryEval(operatorName: String, function: (Evaluator, Evaluator) => Evaluator): Evaluator = {
      if (argEvaluators.length == 1) {
        val eval = qualOpt match {
          case None => new ScalaThisEvaluator()
          case Some(qual) => ScalaEvaluator(qual)
        }
        function(eval, argEvaluators(0))
      } else throw EvaluationException(s"Wrong number of arguments for method '$operatorName'")
    }
    def binaryEvalForBoxes(operatorName: String, boxesName: String): Evaluator = {
      binaryEval(operatorName, binaryEvaluator(_, _, boxesName))
    }
    def equalsEval(opName: String): Evaluator = {
      val rawText = JVMNameUtil.getJVMRawText("(Ljava/lang/Object;Ljava/lang/Object;)Z")
      binaryEval(name, (l, r) => new ScalaMethodEvaluator(BOXES_RUN_TIME, "equals", rawText, boxed(l, r)))
    }
    def isInstanceOfEval: Evaluator = {
      unaryEval("isInstanceOf", eval => {
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
    }

    def trueEval = expressionFromTextEvaluator("true", ref)
    def falseEval = expressionFromTextEvaluator("false", ref)
    def conditionalOr = binaryEval("||", (first, second) => new ScalaIfEvaluator(first, trueEval, Some(second)))
    def conditionalAnd = binaryEval("&&", (first, second) => new ScalaIfEvaluator(first, second, Some(falseEval)))

    name match {
      case "isInstanceOf" => isInstanceOfEval
      case "asInstanceOf" => unaryEval(name, identity) //todo: primitive type casting?
      case "##" => unaryEval(name, eval => new ScalaMethodEvaluator(BOXES_RUN_TIME, "hashFromObject",
        JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)I"), Seq(boxEvaluator(eval))))
      case "==" => equalsEval("==")
      case "!=" => unaryEvaluator(equalsEval("!="), "takeNot")
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
      case "&&" => conditionalAnd
      case "||" => conditionalOr
      case "toInt" => unaryEvalForBoxes(name, "toInteger")
      case "toChar" => unaryEvalForBoxes(name, "toCharacter")
      case "toShort" => unaryEvalForBoxes(name, "toShort")
      case "toByte" => unaryEvalForBoxes(name, "toByte")
      case "toDouble" => unaryEvalForBoxes(name, "toDouble")
      case "toLong" => unaryEvalForBoxes(name, "toLong")
      case "toFloat" => unaryEvalForBoxes(name, "toFloat")
      case "synchronized" =>
        throw EvaluationException("synchronized statement is not supported")
      case _ =>
        throw EvaluationException("Cannot evaluate synthetic method: " + name)
    }
  }

  def arrayMethodEvaluator(name: String, qual: Option[ScExpression], argEvaluators: Seq[Evaluator]): Evaluator = {
    val qualEval = qual match {
      case Some(q) => ScalaEvaluator(q)
      case None => throw EvaluationException(s"Cannot evaluate method $name: array instance is not found")
    }
    name match {
      case "apply" =>
        if (argEvaluators.length == 1) new ScalaArrayAccessEvaluator(qualEval, argEvaluators(0))
        else throw EvaluationException("Wrong number of parameters for Array.apply method")
      case "length" =>
        if (argEvaluators.length == 0) new ScalaFieldEvaluator(qualEval, _ => true, "length")
        else throw EvaluationException("Wrong number of parameters for Array.length method")
      case "clone" =>
        if (argEvaluators.length == 0) new ScalaMethodEvaluator(qualEval, "clone", null/*todo*/, Nil)
        else throw EvaluationException("Wrong number of parameters for Array.clone method")
      case "update" =>
        if (argEvaluators.length == 2) {
          val leftEval = new ScalaArrayAccessEvaluator(qualEval, argEvaluators(0))
          new AssignmentEvaluator(leftEval, unboxEvaluator(argEvaluators(1)))
        } else throw EvaluationException("Wrong number of parameters for Array.update method")
      case "toString" =>
        if (argEvaluators.length == 0) new ScalaMethodEvaluator(qualEval, "toString", null/*todo*/, Nil)
        else throw EvaluationException("Wrong number of parameters for Array.toString method")
      case _ =>
        throw EvaluationException("Array method not supported")
    }
  }

  def isArrayFunction(fun: ScFunction): Boolean = {
    fun.getContext match {
      case tb: ScTemplateBody =>
        fun.containingClass match {
          case clazz: ScClass if clazz.qualifiedName == "scala.Array" => true
          case _ => false
        }
      case _ => false
    }
  }

  def isClassOfFunction(fun: ScFunction): Boolean = {
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

  def classOfFunctionEvaluator(ref: ScReferenceExpression) = {
    val clazzJVMName = ref.getContext match {
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
    if (clazzJVMName != null) new ClassObjectEvaluator(new TypeEvaluator(clazzJVMName))
    else new ScalaLiteralEvaluator(null, Null)
  }

  def valueClassInstanceEvaluator(value: Evaluator, innerType: ScType, classType: ScType): Evaluator = {
    val valueClassType = new TypeEvaluator(DebuggerUtil.getJVMQualifiedName(classType))
    val innerJvmName = DebuggerUtil.getJVMStringForType(innerType, isParam = true)
    val signature = JVMNameUtil.getJVMRawText(s"($innerJvmName)V")
    new ScalaDuplexEvaluator(new ScalaNewClassInstanceEvaluator(valueClassType, signature, Array(value)), value)
  }

  def repeatedArgEvaluator(exprsForP: Seq[ScExpression], expectedType: ScType, context: PsiElement): Evaluator = {
    def seqEvaluator: Evaluator = {
      val argTypes = exprsForP.map(_.getType().getOrAny)
      val argTypeText =
        if (argTypes.isEmpty) expectedType.canonicalText
        else Bounds.lub(argTypes).canonicalText
      val argsText = if (exprsForP.length > 0) exprsForP.sortBy(_.getTextRange.getStartOffset).map(_.getText).mkString(".+=(", ").+=(", ").result()") else ""
      val exprText = s"_root_.scala.collection.Seq.newBuilder[$argTypeText]$argsText"
      val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, context, context)
      ScalaEvaluator(newExpr)
    }
    if (exprsForP.length == 1) {
      exprsForP(0) match {
        case t: ScTypedStmt if t.isSequenceArg => ScalaEvaluator(t.expr)
        case _ => seqEvaluator
      }
    } else seqEvaluator
  }

  def implicitArgEvaluator(fun: ScMethodLike, param: ScParameter, owner: ImplicitParametersOwner): Evaluator = {
    assert(param.owner == fun)
    val implicitParameters = fun.effectiveParameterClauses.lastOption match {
      case Some(clause) if clause.isImplicit => clause.effectiveParameters
      case _ => Seq.empty
    }
    val i = implicitParameters.indexOf(param)
    owner.findImplicitParameters match {
      case Some(resolveResults) if resolveResults.length == implicitParameters.length =>
        if (resolveResults(i) == null) throw EvaluationException("cannot find implicit parameters to pass")

        val exprText = resolveResults(i) match {
          case ScalaResolveResult(clazz: ScTrait, substitutor) if clazz.qualifiedName == "scala.reflect.ClassManifest" =>
            val argType = substitutor.subst(clazz.getType(TypingContext.empty).get)
            argType match {
              case ScParameterizedType(tp, Seq(paramType)) => classManifestText(paramType)
              case _ =>
                throw EvaluationException("cannot find implicit parameters to pass")
            }
          case ScalaResolveResult(clazz: ScTrait, substitutor) if clazz.qualifiedName == "scala.reflect.ClassTag" =>
            val argType = substitutor.subst(clazz.getType(TypingContext.empty).get)
            argType match {
              case ScParameterizedType(tp, Seq(arg)) => classTagText(arg)
              case _ =>
                throw EvaluationException("cannot find implicit parameters to pass")
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
              case o: ScObject if isStable(o) => o.qualifiedName + "." + elem.name
              case o: ScObject => //todo: It can cover many cases!
                throw EvaluationException("Implicit parameters from dependent objects are not supported")
              case _ => elem.name //from scope
            }
        }
        val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, owner.getContext, owner)
        ScalaEvaluator(newExpr)
      case None =>
        throw EvaluationException("cannot find implicit parameters to pass")
    }

  }

  def parameterEvaluator(fun: PsiElement, resolve: PsiElement): Evaluator = {
    val name = NameTransformer.encode(resolve.asInstanceOf[PsiNamedElement].name)
    val evaluator = new ScalaLocalVariableEvaluator(name, fileName)
    fun match {
      case funDef: ScFunctionDefinition =>
        def paramIndex(fun: ScFunctionDefinition, context: PsiElement, elem: PsiElement): Int = {
          val locIndex = DebuggerUtil.localParamsForFunDef(fun).indexOf(elem)
          val funParams = fun.effectiveParameterClauses.flatMap(_.effectiveParameters)
          if (locIndex < 0) funParams.indexOf(elem)
          else locIndex + funParams.size
        }
        val pIndex = paramIndex(funDef, getContextClass(fun), resolve)
        evaluator.setParameterIndex(pIndex)
        evaluator.setMethodName(funDef.name)
      case funExpr: ScFunctionExpr =>
        evaluator.setParameterIndex(funExpr.parameters.indexOf(resolve))
        evaluator.setMethodName("apply")
      case _ => throw EvaluationException("Evaluation from parameter not from function definition or function expression")
    }
    evaluator
  }

  def javaFieldEvaluator(field: PsiField, ref: ScReferenceExpression): Evaluator = {
    ref.qualifier match {
      case Some(qual) =>
        if (field.hasModifierPropertyScala("static")) {
          val eval = new TypeEvaluator(JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(field)))
          val name = field.name
          new ScalaFieldEvaluator(eval, ref => true,name)
        } else {
          val qualEvaluator = ScalaEvaluator(qual)
          new ScalaFieldEvaluator(qualEvaluator, ScalaFieldEvaluator.getFilter(getContainingClass(field)), field.name)
        }
      case None =>
        val evaluator = thisOrImportedQualifierEvaluator(ref)
        new ScalaFieldEvaluator(evaluator, ScalaFieldEvaluator.getFilter(getContainingClass(field)), field.name)
    }
  }
  
  def javaMethodEvaluator(method: PsiMethod, ref: ScReferenceExpression, arguments: Seq[ScExpression]): Evaluator = {

    def boxArguments(arguments: Seq[Evaluator], method: PsiElement): Seq[Evaluator] = {
      val params = method match {
        case fun: ScMethodLike => fun.effectiveParameterClauses.flatMap(_.parameters)
        case m: PsiMethod => m.getParameterList.getParameters.toSeq
        case _ => return arguments
      }

      arguments.zipWithIndex.map {
        case (arg, i) =>
          if (params.length <= i || isOfPrimitiveType(params(i))) arg
          else boxEvaluator(arg)
      }
    }

    val argEvals = boxArguments(arguments.map(ScalaEvaluator(_)), method)
    val methodPosition = DebuggerUtil.getSourcePositions(method.getNavigationElement)
    val signature = JVMNameUtil.getJVMSignature(method)
    ref.qualifier match {
      case Some(qual @ ExpressionType(tp)) if isPrimitiveScType(tp) =>
        val boxEval = boxEvaluator(ScalaEvaluator(qual))
        ScalaMethodEvaluator(boxEval, method.name, signature, argEvals, None, methodPosition)
      case Some(q) if method.hasModifierPropertyScala("static") =>
        val eval = new TypeEvaluator(JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(method)))
        val name = method.name
        ScalaMethodEvaluator(eval, name, signature, argEvals, None, methodPosition)
      case Some(q) =>
        val name = method.name
        new ScalaMethodEvaluator(ScalaEvaluator(q), name, signature, argEvals, None, methodPosition)
      case _ =>
        val evaluator = thisOrImportedQualifierEvaluator(ref)
        val name = method.name
        new ScalaMethodEvaluator(evaluator, name, signature, argEvals, None, methodPosition)
    }
  }

  def unresolvedMethodEvaluator(ref: ScReferenceExpression, args: Seq[ScExpression]): Evaluator = {
    val argEvals = args.map(ScalaEvaluator(_))
    val name = NameTransformer.encode(ref.refName)
    ref.qualifier match {
      case Some(q) => new ScalaMethodEvaluator(ScalaEvaluator(q), name, null, argEvals)
      case _ => new ScalaMethodEvaluator(thisOrImportedQualifierEvaluator(ref), name, null, argEvals)
    }
  }

  def argumentEvaluators(fun: ScMethodLike, matchedParameters: Map[Parameter, Seq[ScExpression]],
                         call: ScExpression, ref: ScReferenceExpression, arguments: Seq[ScExpression]): Seq[Evaluator] = {

    val clauses = fun.effectiveParameterClauses
    val parameters = clauses.flatMap(_.effectiveParameters).map(new Parameter(_))

    def addForNextClause(previousClausesEvaluators: Seq[Evaluator], clause: ScParameterClause): Seq[Evaluator] = {
      previousClausesEvaluators ++ clause.effectiveParameters.map {
        case param =>
          val p = new Parameter(param)
          val exprsForP = matchedParameters.find(_._1.name == p.name).map(_._2).getOrElse(Seq.empty).filter(_ != null)
          if (p.isByName) throw new NeedCompilationException("cannot evaluate methods with by-name parameters")

          val evaluator =
            if (p.isRepeated) repeatedArgEvaluator(exprsForP, p.expectedType, call)
            else if (exprsForP.length > 0) {
              if (exprsForP.length == 1) ScalaEvaluator(exprsForP(0))
              else {
                throw EvaluationException("Wrong number of matched expressions")
              }
            }
            else if (param.isImplicitParameter) implicitArgEvaluator(fun, param, call)
            else if (p.isDefault) {
              val paramIndex = parameters.indexOf(p) + 1
              val methodName = defaultParameterMethodName(fun, paramIndex)
              val localParams = p.paramInCode.toSeq.flatMap(DebuggerUtil.localParamsForDefaultParam(_))
              val localParamRefs =
                localParams.map(td => ScalaPsiElementFactory.createExpressionWithContextFromText(td.name, call.getContext, call))
              val localEvals = localParamRefs.map(ScalaEvaluator(_))
              functionEvaluator(ref.qualifier, ref, methodName, previousClausesEvaluators ++ localEvals)
            }
            else throw EvaluationException(s"Cannot evaluate parameter ${p.name}")

          if (!isOfPrimitiveType(param)) boxEvaluator(evaluator)
          else evaluator
      }
    }

    val argEvaluators: Seq[Evaluator] = clauses.foldLeft(Seq.empty[Evaluator])(addForNextClause)

    if (argEvaluators.contains(null)) arguments.map(arg => ScalaEvaluator(arg))
    else argEvaluators
  }

  def functionEvaluator(qualOption: Option[ScExpression], ref: ScReferenceExpression,
                        funName: String, argEvaluators: Seq[Evaluator]): Evaluator = {

    def qualEvaluator(r: ScalaResolveResult) = {
      def defaultQualEvaluator = qualifierEvaluator(qualOption, ref)

      r.getActualElement match {
        case o: ScObject if funName == "apply" => objectEvaluator(o, defaultQualEvaluator _)
        case _ => defaultQualEvaluator
      }
    }
    val name = NameTransformer.encode(funName)

    ref.bind() match {
      case Some(r) if r.tuplingUsed => throw EvaluationException("Tupling is unsupported. Use tuple expression.")
      case None => throw EvaluationException(s"Cannot evaluate method $funName")
      case Some(r @ privateTraitMethod(tr, fun)) =>
        val traitTypeEval = new TypeEvaluator(DebuggerUtil.getClassJVMName(tr, withPostfix = true))
        val qualEval = qualEvaluator(r)
        new ScalaMethodEvaluator(traitTypeEval, name, null, qualEval +: argEvaluators)
      case Some(r) =>
        val resolve = r.element
        val qualEval = qualEvaluator(r)
        val signature = resolve match {
          case fun: ScFunction => DebuggerUtil.getFunctionJVMSignature(fun)
          case _ => null
        }
        new ScalaMethodEvaluator(qualEval, name, signature, argEvaluators,
          traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
    }
  }

  def methodCallEvaluator(call: ScExpression, arguments: Seq[ScExpression], matchedParameters: Map[Parameter, Seq[ScExpression]]): Evaluator = {
    val ref = call match {
      case hasDeepestInvokedReference(r) => r
      case _ => throw EvaluationException("Cannot evaluate method call: " + call.getText)
    }

    val qualOption = ref.qualifier
    val resolve = ref.resolve()

    resolve match {
      case fun: ScFunctionDefinition if isLocalFunction(fun) =>
        val args = argumentEvaluators(fun, matchedParameters, call, ref, arguments)
        localMethodEvaluator(fun, args)
      case fun: ScFunction if isClassOfFunction(fun) =>
        classOfFunctionEvaluator(ref)
      case synth: ScSyntheticFunction =>
        syntheticFunctionEvaluator(synth, qualOption, ref, arguments) //todo: use matched parameters
      case fun: ScFunction if isArrayFunction(fun) =>
        val args = argumentEvaluators(fun, matchedParameters, call, ref, arguments)
        arrayMethodEvaluator(fun.name,  qualOption, args)
      case fun: ScFunction =>
        ref match {
          case isInsideValueClass(c) if qualOption == None =>
            val clName = c.name
            val paramName = c.allClauses.flatMap(_.parameters).map(_.name).headOption.getOrElse("$this")
            val text = s"new $clName($paramName).${call.getText}"
            val expr = ScalaPsiElementFactory.createExpressionFromText(text, call.getContext)
            ScalaEvaluator(expr)
          case _ =>
            val args: Seq[Evaluator] = argumentEvaluators(fun, matchedParameters, call, ref, arguments)
            functionEvaluator(qualOption, ref, fun.name, args)
        }
      case method: PsiMethod =>
        javaMethodEvaluator(method, ref, arguments)
      case _ =>
        unresolvedMethodEvaluator(ref, arguments)
    }
  }

  def evaluatorForReferenceWithoutParameters(qualifier: Option[ScExpression],
                                             resolve: PsiElement,
                                             ref: ScReferenceExpression): Evaluator = {
    val isLocalValue = DebuggerUtil.isLocalV(resolve)

    def calcLocal(): Evaluator = {
      val labeledValue = resolve.getUserData(CodeFragmentFactoryContextWrapper.LABEL_VARIABLE_VALUE_KEY)
      if (labeledValue != null) return new IdentityEvaluator(labeledValue)

      val isObject = resolve.isInstanceOf[ScObject]

      val namedElement = resolve.asInstanceOf[PsiNamedElement]
      val name = NameTransformer.encode(namedElement.name) + (if (isObject) "$module" else "")
      val containingClass = getContainingClass(namedElement)

      def localVariableEvaluator(): Evaluator = {
        val eval = ScalaPsiUtil.nameContext(namedElement) match {
          case param: ScParameter =>
            param.owner match {
              case fun @ (_: ScFunction | _: ScFunctionExpr) => parameterEvaluator(fun, param)
              case _ => throw EvaluationException("Cannot evaluate parameter")
            }
          case caseCl: ScCaseClause => patternEvaluator(caseCl, namedElement)
          case LazyVal(_) => localLazyValEvaluator(namedElement)
          case _ => new ScalaLocalVariableEvaluator(name, fileName)
        }

        if (isObject) new ScalaFieldEvaluator(eval, ref => true, "elem") //get from VolatileObjectReference
        else eval
      }

      contextClass match {
        case null | `containingClass` => localVariableEvaluator()
        case _ =>
          var iterationCount = 0
          var positionClass: PsiElement = contextClass
          var outerClass = getContextClass(contextClass)
          while (outerClass != null && outerClass != containingClass) {
            iterationCount += anonClassCount(outerClass)
            outerClass = getContextClass(outerClass)
            positionClass = getContextClass(positionClass)
          }
          if (outerClass != null) {
            val thisEval = new ScalaThisEvaluator(iterationCount)
            val filter = ScalaFieldEvaluator.getFilter(positionClass)
            val fieldEval = new ScalaFieldEvaluator(thisEval, filter, name)
            if (isObject) {
              //todo: calss name() method to initialize this field?
              new ScalaFieldEvaluator(fieldEval, ref => true, "elem") //get from VolatileObjectReference
            }
            else new ScalaDuplexEvaluator(fieldEval, localVariableEvaluator())
          }
          else throw new NeedCompilationException("Cannot load local variable from anonymous class")
      }
    }

    resolve match {
      case isInsideLocalFunction(fun) if isLocalValue =>
        new ScalaDuplexEvaluator(calcLocal(), parameterEvaluator(fun, resolve))
      case p: ScParameter if p.isCallByNameParameter && isLocalValue =>
        val localEval = calcLocal()
        new ScalaMethodEvaluator(localEval, "apply", null, Nil)
      case _ if isLocalValue =>
        calcLocal()
      case obj: ScObject =>
        objectEvaluator(obj, () => qualifierEvaluator(qualifier, ref))
      case _: PsiMethod | _: ScSyntheticFunction =>
        methodCallEvaluator(ref, Nil, Map.empty)
      case cp: ScClassParameter if cp.isCallByNameParameter =>
        val qualEval = qualifierEvaluator(qualifier, ref)
        val name = NameTransformer.encode(cp.name)
        val fieldEval = new ScalaFieldEvaluator(qualEval, _ => true, name, true)
        new ScalaMethodEvaluator(fieldEval, "apply", null, Nil)
      case c: ScClassParameter if c.isPrivateThis =>
        //this is field if it's used outside of initialization
        //name of this field ends with $$ + c.getName
        //this is scala "field"
        val named = resolve.asInstanceOf[ScNamedElement]
        val qualEval = qualifierEvaluator(qualifier, ref)
        val name = NameTransformer.encode(named.name)
        new ScalaFieldEvaluator(qualEval, _ => true, name, true)
      case cp: ScClassParameter if qualifier == None && ValueClassType.isValueClass(cp.containingClass) =>
        //methods of value classes have hidden argument with underlying value
        new ScalaLocalVariableEvaluator("$this", fileName)
      case _: ScClassParameter | _: ScBindingPattern =>
        //this is scala "field"
        val named = resolve.asInstanceOf[ScNamedElement]
        val name = NameTransformer.encode(named.name)
        val qualEval = qualifierEvaluator(qualifier, ref)
        val withSimpleNameEval = new ScalaMethodEvaluator(qualEval, name, null /* todo */, Seq.empty,
          traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
        getContextClass(named) match {
          //in some cases compiler uses full qualified names for fields and methods
          case clazz: ScTemplateDefinition if ScalaPsiUtil.hasStablePath(clazz)
                  && clazz.members.contains(ScalaPsiUtil.nameContext(named)) =>
            val qualName = clazz.qualifiedName
            val newName = qualName.split('.').map(NameTransformer.encode).mkString("$") + "$$" + name
            val reserveEval = new ScalaMethodEvaluator(qualEval, newName, null/* todo */, Seq.empty,
              traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
            new ScalaDuplexEvaluator(withSimpleNameEval, reserveEval)
          case _ => withSimpleNameEval
        }
      case field: PsiField => javaFieldEvaluator(field, ref)
      case pack: ScPackage =>
        //let's try to find package object:
        val qual = (pack.getQualifiedName + ".package$").split('.').map(NameTransformer.encode).mkString(".")
        stableObjectEvaluator(qual)
      case _ =>
        //unresolved symbol => try to resolve it dynamically
        val name = NameTransformer.encode(ref.refName)
        val fieldOrVarEval = qualifier match {
          case Some(qual) => new ScalaFieldEvaluator(ScalaEvaluator(qual), ref => true, name)
          case None => new ScalaLocalVariableEvaluator(name, fileName)
        }
        new ScalaDuplexEvaluator(fieldOrVarEval, unresolvedMethodEvaluator(ref, Seq.empty))
    }
  }

  def qualifierEvaluator(qualifier: Option[ScExpression], ref: ScReferenceExpression): Evaluator = qualifier match {
    case Some(q) => ScalaEvaluator(q)
    case _ => thisOrImportedQualifierEvaluator(ref)
  }

  def patternEvaluator(caseCl: ScCaseClause, namedElement: PsiNamedElement): Evaluator = {
    val name = namedElement.name
    if (caseCl.getParent != null) {
      val pattern = caseCl.pattern
      if (pattern.isEmpty) throw EvaluationException("Cannot find pattern of case clause")
      caseCl.getParent.getParent match {
        case matchStmt: ScMatchStmt if namedElement.isInstanceOf[ScPattern] =>
          val expr = matchStmt.expr
          if (expr.isEmpty) throw EvaluationException("Cannot find expression of match statement")
          val exprEval = ScalaEvaluator(expr.get)
          val fromPatternEvaluator = evaluateSubpatternFromPattern(exprEval, pattern.get, namedElement.asInstanceOf[ScPattern])
          new ScalaDuplexEvaluator(new ScalaLocalVariableEvaluator(name, fileName), fromPatternEvaluator)
        case block: ScBlockExpr => //it is anonymous function
          val argEvaluator = new ScalaLocalVariableEvaluator("", fileName)
          argEvaluator.setMethodName("apply")
          argEvaluator.setParameterIndex(0)
          val fromPatternEvaluator = evaluateSubpatternFromPattern(argEvaluator, pattern.get, namedElement.asInstanceOf[ScPattern])
          new ScalaDuplexEvaluator(new ScalaLocalVariableEvaluator(name, fileName), fromPatternEvaluator)
        case _ => new ScalaLocalVariableEvaluator(name, fileName)
      }
    } else throw EvaluationException("Invalid case clause")
  }

  def assignmentEvaluator(stmt: ScAssignStmt): Evaluator = {
    if (stmt.isNamedParameter) {
      stmt.getRExpression match {
        case Some(expr) => ScalaEvaluator(expr)
        case _ => throw EvaluationException("Cannot evaluate assign statement without expression")
      }
    } else {
      stmt.getLExpression match {
        case call: ScMethodCall =>
          val invokedText = call.getInvokedExpr.getText
          val rExprText = stmt.getRExpression.fold("null")(_.getText)
          val args = (call.args.exprs.map(_.getText) :+ rExprText).mkString("(", ", ", ")")
          val exprText = s"($invokedText).update$args"
          val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText, stmt.getContext, stmt)
          ScalaEvaluator(expr)
        case _ =>
          val leftEvaluator = ScalaEvaluator(stmt.getLExpression)
          val rightEvaluator = stmt.getRExpression match {
            case Some(expr) => ScalaEvaluator(expr)
            case _ => throw EvaluationException("Cannot evaluate assign statement without expression")
          }
          def createAssignEvaluator(leftEvaluator: Evaluator): Option[Evaluator] = {
            leftEvaluator match {
              case m: ScalaMethodEvaluator =>
                Some(m.copy(_methodName = m.methodName + "_$eq", argumentEvaluators = Seq(rightEvaluator))) //todo: signature?
              case ScalaDuplexEvaluator(first, second) =>
                createAssignEvaluator(first) orElse createAssignEvaluator(second)
              case _ => None
            }
          }
          createAssignEvaluator(leftEvaluator).getOrElse(new AssignmentEvaluator(leftEvaluator, rightEvaluator))
      }
    }
  }

  def evaluateSubpatternFromPattern(exprEval: Evaluator, pattern: ScPattern, subPattern: ScPattern): Evaluator = {
    def evaluateConstructorOrInfix(exprEval: Evaluator, ref: ScStableCodeReferenceElement, pattern: ScPattern, nextPatternIndex: Int): Evaluator = {
      ref.resolve() match {
        case fun: ScFunctionDefinition =>
          val elem = ref.bind().get.getActualElement //object or case class
        val qual = ref.qualifier.map(q => ScalaPsiElementFactory.createExpressionWithContextFromText(q.getText, q.getContext, q))
          val refExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(ref.getText, ref.getContext, ref)
          val refEvaluator = evaluatorForReferenceWithoutParameters(qual, elem, refExpr.asInstanceOf[ScReferenceExpression])

          val funName = fun.name
          val newEval =
            if (funName == "unapply") {
              val extractEval = new ScalaMethodEvaluator(refEvaluator, funName, DebuggerUtil.getFunctionJVMSignature(fun), Seq(exprEval))
              if (pattern.subpatterns.length == 1)
                new ScalaMethodEvaluator(extractEval, "get", null, Seq.empty)
              else if (pattern.subpatterns.length > 1) {
                val getEval = new ScalaMethodEvaluator(extractEval, "get", null, Seq.empty)
                new ScalaFieldEvaluator(getEval, x => true, s"_${nextPatternIndex + 1}")
              }
              else throw EvaluationException("Cannot extract value from unapply without arguments")
            } else if (funName == "unapplySeq") {
              val extractEval = new ScalaMethodEvaluator(refEvaluator, funName, DebuggerUtil.getFunctionJVMSignature(fun), Seq(exprEval))
              val getEval = new ScalaMethodEvaluator(extractEval, "get", null, Seq.empty)
              val indexExpr = ScalaPsiElementFactory.createExpressionFromText("" + nextPatternIndex, pattern.getManager)
              val indexEval = ScalaEvaluator(indexExpr)
              new ScalaMethodEvaluator(getEval, "apply", null, Seq(indexEval))
            } else throw EvaluationException("Pattern reference does not resolve to unapply or unapplySeq")
          val nextPattern = pattern.subpatterns(nextPatternIndex)
          evaluateSubpatternFromPattern(newEval, nextPattern, subPattern)
        case _ => throw EvaluationException("Pattern reference does not resolve to unapply or unapplySeq")
      }
    }

    if (pattern == null || subPattern == null)
      throw new IllegalArgumentException("Patterns should not be null")
    val nextPatternIndex: Int = pattern.subpatterns.indexWhere(next => next == subPattern || subPattern.parents.contains(next))

    if (pattern == subPattern) exprEval
    else if (nextPatternIndex < 0) throw new IllegalArgumentException("Pattern is not ancestor of subpattern")
    else {
      pattern match {
        case naming: ScNamingPattern => evaluateSubpatternFromPattern(exprEval, naming.named, subPattern)
        case typed: ScTypedPattern => evaluateSubpatternFromPattern(exprEval, pattern.subpatterns.head, subPattern)
        case par: ScParenthesisedPattern =>
          val withoutPars = par.subpattern.getOrElse(throw new IllegalStateException("Empty parentheses pattern"))
          evaluateSubpatternFromPattern(exprEval, withoutPars, subPattern)
        case tuple: ScTuplePattern =>
          val nextPattern = tuple.subpatterns(nextPatternIndex)
          val newEval = new ScalaFieldEvaluator(exprEval, x => true, s"_${nextPatternIndex + 1}")
          evaluateSubpatternFromPattern(newEval, nextPattern, subPattern)
        case constr: ScConstructorPattern =>
          val ref: ScStableCodeReferenceElement = constr.ref
          evaluateConstructorOrInfix(exprEval, ref, constr, nextPatternIndex)
        case infix: ScInfixPattern =>
          val ref: ScStableCodeReferenceElement = infix.refernece
          evaluateConstructorOrInfix(exprEval, ref, infix, nextPatternIndex)
        //todo: handle infix with tuple right pattern
        case _: ScCompositePattern => throw EvaluationException("Pattern alternatives cannot bind variables")
        case _: ScXmlPattern => throw EvaluationException("Xml patterns are not supported") //todo: xml patterns
        case _ => throw EvaluationException(s"This kind of patterns is not supported: ${pattern.getText}") //todo: xml patterns
      }
    }
  }

  def newTemplateDefinitionEvaluator(templ: ScNewTemplateDefinition): Evaluator = {
    templ.extendsBlock.templateParents match {
      case Some(parents: ScClassParents) =>
        if (parents.typeElements.length != 1) {
          throw new NeedCompilationException("Anonymous classes are not supported")
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
                ScalaEvaluator(expr)
              case Some(clazz) =>
                val jvmName = DebuggerUtil.getClassJVMName(clazz)
                val typeEvaluator = new TypeEvaluator(jvmName)
                val argumentEvaluators = constructorArgumentsEvaluators(templ, constr, clazz)
                constr.reference.map(_.resolve()) match {
                  case Some(named: PsiNamedElement) =>
                    val signature = DebuggerUtil.constructorSignature(named)
                    new ScalaMethodEvaluator(typeEvaluator, "<init>", signature, argumentEvaluators)
                  case _ =>
                    new ScalaMethodEvaluator(typeEvaluator, "<init>", null, argumentEvaluators)
                }
              case _ => throw EvaluationException("Cannot evaluate new expression without class reference")
            }

          case None => throw EvaluationException("Cannot evaluate expression without constructor call")
        }
      case _ => throw EvaluationException("Cannot evaluate expression without template parents")
    }
  }

  def constructorArgumentsEvaluators(newTd: ScNewTemplateDefinition,
                                     constr: ScConstructor,
                                     clazz: PsiClass): Seq[Evaluator] = {
    val constrDef = constr.reference match {
      case Some(ResolvesTo(elem)) => elem
      case _ => throw EvaluationException("Could not resolve constructor")
    }
    val explicitArgs = constr.arguments.flatMap(_.exprs)
    val explEvaluators =
      for {
        arg <- explicitArgs
      } yield {
        val eval = ScalaEvaluator(arg)
        val param = ScalaPsiUtil.parameterOf(arg).flatMap(_.psiParam)
        if (param.exists(!isOfPrimitiveType(_))) boxEvaluator(eval)
        else eval
      }
    constrDef match {
      case scMethod: ScMethodLike =>
        val scClass = scMethod.containingClass.asInstanceOf[ScClass]
        val contextClass = getContextClass(scClass)
        val implicitParams = scMethod.parameterList.params.filter(_.isImplicitParameter)

        val implicitsEvals =
          for {
            typeElem <- constr.simpleTypeElement.toSeq
            p <- implicitParams
          } yield {
            val eval = implicitArgEvaluator(scMethod, p, typeElem)
            if (isOfPrimitiveType(p)) eval
            else boxEvaluator(eval)
          }
        val outerThis = contextClass match {
          case obj: ScObject if isStable(obj) => None
          case null => None
          case _ => Some(new ScalaThisEvaluator())
        }
        val locals = DebuggerUtil.localParamsForConstructor(scClass)
        outerThis ++: explEvaluators ++: implicitsEvals ++: locals.map(fromLocalArgEvaluator)
      case _ => explEvaluators
    }
  }
  
  def fromLocalArgEvaluator(local: ScTypedDefinition): Evaluator = {
    val name = local.asInstanceOf[PsiNamedElement].name
    val elemAt = position.getElementAt
    val ref = ScalaPsiElementFactory.createExpressionWithContextFromText(name, elemAt, elemAt)
    val refEval = ScalaEvaluator(ref)

    if (local.isInstanceOf[ScObject]) {
      val qual = "scala.runtime.VolatileObjectRef"
      val typeEvaluator = new TypeEvaluator(JVMNameUtil.getJVMRawText(qual))
      val signature = JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)V")
      new ScalaNewClassInstanceEvaluator(typeEvaluator, signature, Array(refEval))
    }
    else refEval
  }


  def expressionFromTextEvaluator(string: String, context: PsiElement): Evaluator = {
    val expr = ScalaPsiElementFactory.createExpressionWithContextFromText(string, context.getContext, context)
    ScalaEvaluator(expr)
  }

  def localLazyValEvaluator(named: PsiNamedElement): Evaluator = {
    val name = named.name
    val localRefName = s"$name$$lzy"
    val localRefEval = new ScalaLocalVariableEvaluator(localRefName, fileName)
    val lzyIndex = lazyValIndex(named)
    val bitmapName = "bitmap$" + (lzyIndex / 8)
    val bitmapEval = new ScalaLocalVariableEvaluator(bitmapName, fileName)
    val localFunIndex = localFunctionIndex(named)
    val methodName = s"$name$$$localFunIndex"
    new ScalaMethodEvaluator(new ScalaThisEvaluator(), methodName, null, Seq(localRefEval, bitmapEval))
  }
}

object ScalaEvaluatorBuilderUtil {
  private val BOXES_RUN_TIME = new TypeEvaluator(JVMNameUtil.getJVMRawText("scala.runtime.BoxesRunTime"))
  private val BOXED_UNIT = new TypeEvaluator(JVMNameUtil.getJVMRawText("scala.runtime.BoxedUnit"))
  def boxEvaluator(eval: Evaluator): Evaluator = new ScalaBoxingEvaluator(eval)
  def boxed(evaluators: Evaluator*): Seq[Evaluator] = evaluators.map(boxEvaluator)
  def unboxEvaluator(eval: Evaluator): Evaluator = new UnBoxingEvaluator(eval)
  def notEvaluator(eval: Evaluator): Evaluator = {
    val rawText = JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)Ljava/lang/Object;")
    unboxEvaluator(new ScalaMethodEvaluator(BOXES_RUN_TIME, "takeNot", rawText, boxed(eval)))
  }
  def eqEvaluator(left: Evaluator, right: Evaluator): Evaluator = {
    new ScalaEqEvaluator(left, right)
  }

  def neEvaluator(left: Evaluator, right: Evaluator): Evaluator = {
    notEvaluator(eqEvaluator(left, right))
  }

  def unitEvaluator(): Evaluator = {
    new ScalaFieldEvaluator(BOXED_UNIT, _ => true, "UNIT")
  }

  def unaryEvaluator(eval: Evaluator, boxesRunTimeName: String): Evaluator = {
    val rawText = JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)Ljava/lang/Object;")
    unboxEvaluator(new ScalaMethodEvaluator(BOXES_RUN_TIME, boxesRunTimeName, rawText, boxed(eval)))
  }

  def binaryEvaluator(left: Evaluator, right: Evaluator, boxesRunTimeName: String): Evaluator = {
    val rawText = JVMNameUtil.getJVMRawText("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
    unboxEvaluator(new ScalaMethodEvaluator(BOXES_RUN_TIME, boxesRunTimeName, rawText, boxed(left, right)))
  }

  object hasDeepestInvokedReference {
    @tailrec
    final def unapply(expr: ScExpression): Option[ScReferenceExpression] = {
      expr match {
        case call: ScMethodCall => unapply(call.deepestInvokedExpr)
        case genCall: ScGenericCall => unapply(genCall.referencedExpr)
        case ref: ScReferenceExpression => Some(ref)
        case _ => None
      }
    }
  }

  def classTagText(arg: ScType): String = {
    import org.jetbrains.plugins.scala.lang.psi.types._
    arg match {
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
  }

  def classManifestText(scType: ScType): String = {
    import org.jetbrains.plugins.scala.lang.psi.types._
    scType match {
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
        "_root_.scala.reflect.ClassManifest.arrayType(" + classManifestText(arg) + ")"
      case ScParameterizedType(ScDesignatorType(clazz: ScClass), Seq(arg))

        if clazz.qualifiedName == "scala.Array" =>
        "_root_.scala.reflect.ClassManifest.arrayType(" + classManifestText(arg) + ")"
      /*case ScParameterizedType(des, args) =>
        ScType.extractClass(des, Option(expr.getProject)) match {
          case Some(clazz) =>
            "_root_.scala.reflect.ClassManifest.classType(" +
          case _ => "null"
        }*/   //todo:
      case _ => ScType.extractClass(scType) match {
        case Some(clss) => "_root_.scala.reflect.ClassManifest.classType(classOf[_root_." +
                clss.qualifiedName + "])"
        case _ => "_root_.scala.reflect.ClassManifest.classType(classOf[_root_.java.lang." +
                "Object])"
      }
    }
  }

  def isOfPrimitiveType(param: PsiParameter) = param match { //todo specialized type parameters
    case p: ScParameter =>
      val tp: ScType = p.getType(TypingContext.empty).getOrAny
      isPrimitiveScType(tp)
    case p: PsiParameter =>
      val tp = param.getType
      import com.intellij.psi.PsiType._
      Set[PsiType](BOOLEAN, INT, CHAR, DOUBLE, FLOAT, LONG, BYTE, SHORT).contains(tp)
    case _ => false
  }
  
  def isPrimitiveScType(tp: ScType) = {
    import org.jetbrains.plugins.scala.lang.psi.types._
    Set[ScType](Boolean, Int, Char, Double, Float, Long, Byte, Short).contains(tp)
  }

  object implicitlyConvertedTo {
    def unapply(expr: ScExpression): Option[ScExpression] = {
      val implicits = expr.getImplicitConversions(fromUnder = true)
      implicits._2 match {
        case Some(fun: ScFunction) =>
          val exprText = expr.getText
          val callText = s"${fun.name}($exprText)"
          val newExprText = fun.containingClass match {
            case o: ScObject if isStable(o) => s"${o.qualifiedName}.$callText"
            case o: ScObject => //todo: It can cover many cases!
              throw EvaluationException("Implicit conversions from dependent objects are not supported")
            case _ => callText //from scope
          }
          Some(ScalaPsiElementFactory.createExpressionWithContextFromText(newExprText, expr.getContext, expr))
        case _ => None
      }
    }
  }

  @tailrec
  final def isStable(o: ScObject): Boolean = {
    val context = getContextClass(o)
    if (context == null) return true
    context match {
      case o: ScObject => isStable(o)
      case _ => false
    }
  }

  def getContextClass(elem: PsiElement): PsiElement = {
    elem.contexts.find(isGenerateClass).orNull
  }

  def isGenerateClass(elem: PsiElement): Boolean = {
    elem match {
      case clazz: PsiClass => true
      case f: ScFunctionExpr => true
      case (_: ScExpression) childOf (_: ScForStatement) => true
      case e: ScExpression if ScUnderScoreSectionUtil.underscores(e).length > 0 => true
      case b: ScBlockExpr if b.isAnonymousFunction => true
      case (g: ScGuard) childOf (_: ScEnumerators) => true
      case (g: ScGenerator) childOf (enums: ScEnumerators) if enums.generators.headOption != Some(g) => true
      case e: ScEnumerator => true
      case (expr: ScExpression) childOf (argLisg: ScArgumentExprList) if ScalaPsiUtil.parameterOf(expr).exists(_.isByName) => true
      case _ => false
    }
  }

  def anonClassCount(elem: PsiElement): Int = {
    elem match {
      case f: ScForStatement =>
        f.enumerators.fold(1)(e => e.enumerators.length + e.generators.length + e.guards.length) //todo: non irrefutable patterns?
      case _ => 1
    }
  }

  def getContainingClass(elem: PsiElement): PsiElement = {
    elem.parentsInFile.find(isGenerateClass).getOrElse(getContextClass(elem))
  }

  def localFunctionIndex(named: PsiNamedElement): Int = {
    elementsWithSameNameIndex(named, {
      case f: ScFunction if f.isLocal && f.name == named.name => true
      case Both(ScalaPsiUtil.inNameContext(LazyVal(_)), lzy: ScBindingPattern) if lzy.name == named.name => true
      case _ => false
    })
  }

  def lazyValIndex(named: PsiNamedElement): Int = {
    elementsWithSameNameIndex(named, {
      case Both(ScalaPsiUtil.inNameContext(LazyVal(_)), lzy: ScBindingPattern) if lzy.name == named.name => true
      case _ => false
    })
  }

  def defaultParameterMethodName(method: ScMethodLike, paramIndex: Int): String = {
    method match {
      case fun: ScFunction if !fun.isConstructor =>
        val suffix: String = if (!fun.isLocal) "" else "$" + localFunctionIndex(fun)
        fun.name + "$default$" + paramIndex + suffix
      case _ if method.isConstructor =>  "$lessinit$greater$default$" + paramIndex + "()"
    }
  }
  
  def elementsWithSameNameIndex(named: PsiNamedElement, condition: PsiElement => Boolean): Int = {
    val containingClass = getContainingClass(named)
    val depthFirstIterator = containingClass.depthFirst {
      case `containingClass` => true
      case elem if isGenerateClass(elem) => false
      case _ => true
    }
    val sameNameElements = depthFirstIterator.filter(condition).toList
    sameNameElements.indexOf(named) + 1
  }

  def traitImplementation(elem: PsiElement): Option[JVMName] = {
    val clazz = getContextClass(elem)
    clazz match {
      case t: ScTrait =>
        Some(DebuggerUtil.getClassJVMName(t, withPostfix = true))
      case _ => None
    }
  }

  def isLocalFunction(fun: ScFunction): Boolean = {
    !fun.getContext.isInstanceOf[ScTemplateBody]
  }

  object isInsideValueClass {
    def unapply(elem: PsiElement): Option[ScClass] = {
      getContextClass(elem) match {
        case c: ScClass if ValueClassType.isValueClass(c) => Some(c)
        case _ => None
      }
    }
  }

  object isInsideLocalFunction {
    def unapply(elem: PsiElement): Option[ScFunction] = {
      @tailrec
      def inner(element: PsiElement): Option[ScFunction] = {
        element match {
          case null => None
          case fun: ScFunction if isLocalFunction(fun) &&
                  !fun.parameters.exists(param => PsiTreeUtil.isAncestor(param, elem, false)) =>
            Some(fun)
          case other if other.getContext != null => inner(other.getContext)
          case _ => None
        }
      }
      inner(elem)
    }
  }

  object privateTraitMethod {
    def unapply(r: ScalaResolveResult): Option[(ScTrait, ScFunctionDefinition)] = {
      r.getElement match {
        case Both(fun: ScFunctionDefinition, ContainingClass(tr: ScTrait)) if fun.isPrivate => Some(tr, fun)
        case _ => None
      }
    }
  }
}
