package org.jetbrains.plugins.scala
package debugger
package evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.CodeFragmentFactoryContextWrapper
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.debugger.engine.{JVMName, JVMNameUtil}
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Condition
import com.intellij.psi._
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.debugger.ScalaPositionManager.{InsideAsync, isCompiledWithIndyLambdas}
import org.jetbrains.plugins.scala.debugger.TopLevelMembers.{hasTopLevelMembers, topLevelMemberClassName}
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator._
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil
import org.jetbrains.plugins.scala.debugger.evaluation.util.DebuggerUtil.isAtLeast212
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElementExt
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner, ScNamedElement, ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ImplicitArgumentsOwner, ScPackage, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScDesignatorType, ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.CachedInUserData

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.reflect.NameTransformer

/**
* Nikolay.Tropin
* 2014-09-28
*/
private[evaluation] trait ScalaEvaluatorBuilderUtil {
  this: ScalaEvaluatorBuilder =>

  import ScalaEvaluatorBuilderUtil._
  private val stdTypes = projectContext.stdTypes
  import stdTypes._

  def fileName: String = contextClass.toOption.flatMap(_.getContainingFile.toOption).map(_.name).orNull

  def importedQualifierEvaluator(ref: ScReference, resolveResult: ScalaResolveResult): Evaluator = {
    val message = ScalaBundle.message("cannot.evaluate.imported.reference")
    resolveResult.fromType match {
      case Some(ScDesignatorType(element)) =>
        element match {
          case obj: ScObject => stableObjectEvaluator(obj)
          case cl: PsiClass if cl.getLanguage.isInstanceOf[JavaLanguage] =>
            new ScalaTypeEvaluator(JVMNameUtil.getJVMQualifiedName(cl))
          case _ =>
            val expr = createExpressionWithContextFromText(element.name, ref.getContext, ref)
            evaluatorFor(expr)
        }
      case Some(p: ScProjectionType) =>
        def exprToEvaluate(p: ScProjectionType): String = p.projected match {
          case ScDesignatorType(elem) => elem.name + "." + p.actualElement.name
          case projected: ScProjectionType => exprToEvaluate(projected) + "." + projected.actualElement.name
          case ScThisType(cl) if contextClass == cl => s"this.${p.actualElement.name}"
          case ScThisType(cl) => s"${cl.name}.this.${p.actualElement.name}"
          case _ => throw EvaluationException(message)
        }
        val expr = createExpressionWithContextFromText(exprToEvaluate(p), ref.getContext, ref)
        evaluatorFor(expr)
      case _ => throw EvaluationException(message)
    }
  }

  def thisOrImportedQualifierEvaluator(ref: ScReference): Evaluator = {
    ref.bind() match {
      case Some(resolveResult) =>
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
      case Some(tp) =>
        val project = elem.getProject
        tp.extractClass match {
          case Some(x) => x
          case None => getContextClass(elem)
      }
      case _ => getContextClass(elem)
    }
    containingClass match {
      case o: ScObject if isStable(o) =>
        return stableObjectEvaluator(o)
      case _ =>
    }
    val (outerClass, iterationCount) = findContextClass(e => e == null || e == containingClass)

    if (outerClass != null)
      new ScalaThisEvaluator(iterationCount)
    else new ScalaThisEvaluator()
  }

  def thisOrSuperEvaluator(refOpt: Option[ScStableCodeReference], isSuper: Boolean): Evaluator = {

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
          case cl: PsiClass if cl.name != null && cl.name == refName => true
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

  def findContextClass(stopCondition: PsiElement => Boolean): (PsiElement, Int) = {
    var current: PsiElement = contextClass
    var iterations = 0
    while (!stopCondition(current)) {
      iterations += anonClassCount(current)
      current = getContextClass(current)
    }
    (current, iterations)
  }

  def localMethodEvaluator(fun: ScFunctionDefinition, argEvaluators: Seq[Evaluator]): Evaluator = {
    def localFunName() = {
      val transformed = NameTransformer.encode(fun.name)
      fun match {
        case InsideAsync(call) if !fun.parentOfType(classOf[ScFunctionDefinition]).exists(call.isAncestorOf(_)) =>
          transformed + "$macro"
        case _ => transformed
      }
    }

    val name = localFunName()
    val containingClass = if (fun.isSynthetic) fun.containingClass else getContextClass(fun)
    val message = ScalaBundle.message("cannot.evaluate.local.method")
    if (contextClass == null) {
      throw EvaluationException(message)
    }
    val thisEvaluator: Evaluator = containingClass match {
      case obj: ScObject if isStable(obj) =>
        stableObjectEvaluator(obj)
      case _: ScTrait =>
        thisOrSuperEvaluator(None, isSuper = true)
      case _: ScalaFile | _: ScPackaging if fun.isTopLevel =>
        stableObjectEvaluator(topLevelMemberClassName(fun))
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
      ScalaMethodEvaluator(thisEvaluator, name, signature, evaluators, traitImplementation(fun), positions, idx)
    }
    else throw EvaluationException(message)
  }


  def stableObjectEvaluator(qual: String): ScalaFieldEvaluator = {
    val jvm = JVMNameUtil.getJVMRawText(qual)
    ScalaFieldEvaluator(new TypeEvaluator(jvm), "MODULE$")
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
      ScalaMethodEvaluator(qualEvaluator(), objName, null /* todo? */ , Seq.empty,
        traitImplementation(obj), DebuggerUtil.getSourcePositions(obj.getNavigationElement))
    }
  }

  def syntheticFunctionEvaluator(synth: ScSyntheticFunction,
                                 qualOpt: Option[ScExpression],
                                 ref: ScReferenceExpression,
                                 arguments: Iterable[ScExpression]): Evaluator = {
    if (synth.isStringPlusMethod && arguments.size == 1) {
      val qualText = qualOpt.fold("this")(_.getText)
      val exprText = s"($qualText).concat(_root_.java.lang.String.valueOf(${arguments.head.getText}))"
      val expr = createExpressionWithContextFromText(exprText, ref.getContext, ref)
      return evaluatorFor(expr)
    }

    val name = synth.name
    val argEvaluators = arguments.map(evaluatorFor(_))

    def unaryEval(operatorName: String, function: Evaluator => Evaluator): Evaluator = {
      if (argEvaluators.isEmpty) {
        val eval = qualOpt match {
          case None => new ScalaThisEvaluator()
          case Some(qual) => evaluatorFor(qual)
        }
        function(eval)
      } else throw EvaluationException(ScalaBundle.message("wrong.number.of.arguments", operatorName))
    }
    def unaryEvalForBoxes(operatorName: String, boxesName: String): Evaluator = {
      unaryEval(operatorName, unaryEvaluator(_, boxesName))
    }
    def binaryEval(operatorName: String, function: (Evaluator, Evaluator) => Evaluator): Evaluator = {
      if (argEvaluators.size == 1) {
        val eval = qualOpt match {
          case None => new ScalaThisEvaluator()
          case Some(qual) => evaluatorFor(qual)
        }
        function(eval, argEvaluators.head)
      } else throw EvaluationException(ScalaBundle.message("wrong.number.of.arguments", operatorName))
    }
    def binaryEvalForBoxes(operatorName: String, boxesName: String): Evaluator = {
      binaryEval(operatorName, binaryEvaluator(_, _, boxesName))
    }
    def equalsEval(opName: String): Evaluator = {
      val rawText = JVMNameUtil.getJVMRawText("(Ljava/lang/Object;Ljava/lang/Object;)Z")
      binaryEval(name, (l, r) => ScalaMethodEvaluator(BOXES_RUN_TIME, "equals", rawText, boxed(l, r)))
    }
    def isInstanceOfEval: Evaluator = {
      unaryEval("isInstanceOf", eval => {
        val tp = ref.getParent match {
          case gen: ScGenericCall =>
          gen.typeArgs.typeArgs match {
            case Seq(arg) => arg.calcType
            case _ => Nothing
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
      case "##" => unaryEval(name, eval => ScalaMethodEvaluator(BOXES_RUN_TIME, "hashFromObject",
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
        throw EvaluationException(ScalaBundle.message("synchronized.statement.is.not.supported"))
      case _ =>
        throw EvaluationException(ScalaBundle.message("cannot.evaluate.synthetic.method", name))
    }
  }

  def arrayMethodEvaluator(name: String, qual: Option[ScExpression], argEvaluators: Seq[Evaluator]): Evaluator = {
    val qualEval = qual match {
      case Some(q) => evaluatorFor(q)
      case None => throw EvaluationException(ScalaBundle.message("array.instance.is.not.found", name))
    }
    val message = ScalaBundle.message("wrong.number.of.arguments", s"Array.$name")
    name match {
      case "apply" =>
        if (argEvaluators.length == 1) new ScalaArrayAccessEvaluator(qualEval, argEvaluators.head)
        else throw EvaluationException(message)
      case "length" =>
        if (argEvaluators.isEmpty) ScalaFieldEvaluator(qualEval, "length")
        else throw EvaluationException(message)
      case "clone" =>
        if (argEvaluators.isEmpty) ScalaMethodEvaluator(qualEval, "clone", null /*todo*/ , Nil)
        else throw EvaluationException(message)
      case "update" =>
        if (argEvaluators.length == 2) {
          val leftEval = new ScalaArrayAccessEvaluator(qualEval, argEvaluators.head)
          new AssignmentEvaluator(leftEval, unboxEvaluator(argEvaluators(1)))
        } else throw EvaluationException(message)
      case "toString" =>
        if (argEvaluators.isEmpty) ScalaMethodEvaluator(qualEval, "toString", null /*todo*/ , Nil)
        else throw EvaluationException(message)
      case _ =>
        throw EvaluationException(ScalaBundle.message("array.method.not.supported"))
    }
  }

  def isArrayFunction(fun: ScFunction): Boolean = {
    fun.getContext match {
      case _: ScTemplateBody =>
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
      case _: ScTemplateBody =>
        fun.containingClass match {
          case clazz: PsiClass if clazz.qualifiedName == "scala.Predef" => true
          case _ => false
        }
      case _ => false
    }
  }

  def classOfFunctionEvaluator(ref: ScReferenceExpression): Evaluator = {
    val maybeClazzJVMName = ref.getContext match {
      case gen: ScGenericCall =>
        gen.arguments.head.`type`().toOption
          .flatMap(_.extractClass)
          .map(DebuggerUtil.getClassJVMName(_))
      case _ => None
    }

    maybeClazzJVMName match {
      case Some(clazzName) => new ClassObjectEvaluator(new TypeEvaluator(clazzName))
      case _ => ScalaLiteralEvaluator.empty
    }
  }

  def valueClassInstanceEvaluator(value: Evaluator, innerType: ScType, classType: ScType): Evaluator = {
    val valueClassType = new ScalaTypeEvaluator(DebuggerUtil.getJVMQualifiedName(classType))
    val innerJvmName = DebuggerUtil.getJVMStringForType(innerType, isParam = true)
    val signature = JVMNameUtil.getJVMRawText(s"($innerJvmName)V")
    ScalaDuplexEvaluator(new ScalaNewClassInstanceEvaluator(valueClassType, signature, Array(value)), value)
  }

  def repeatedArgEvaluator(exprsForP: Seq[ScExpression], expectedType: ScType, context: PsiElement): Evaluator = {
    def seqEvaluator: Evaluator = {
      val argTypes = exprsForP.map(_.`type`().getOrAny)
      val argType =
        if (argTypes.isEmpty) expectedType else argTypes.lub()
      val argTypeText = argType.canonicalText

      val argsText =
        if (exprsForP.nonEmpty) exprsForP.sortBy(_.startOffset).map(_.getText).mkString(".+=(", ").+=(", ")")
        else ""

      val exprText = s"_root_.scala.collection.Seq.newBuilder[$argTypeText]$argsText.result()"
      val newExpr = createExpressionWithContextFromText(exprText, context, context)
      evaluatorFor(newExpr)
    }
    if (exprsForP.length == 1) {
      exprsForP.head match {
        case t: ScTypedExpression if t.isSequenceArg => evaluatorFor(t.expr)
        case _ => seqEvaluator
      }
    } else seqEvaluator
  }

  def implicitArgEvaluator(fun: ScMethodLike, param: ScParameter, owner: ImplicitArgumentsOwner): Evaluator = {
    assert(param.owner == fun)
    val implicitParameters = fun.effectiveParameterClauses.lastOption match {
      case Some(clause) if clause.isImplicit => clause.effectiveParameters
      case _ => Seq.empty
    }
    val i = implicitParameters.indexOf(param)
    val cannotFindMessage = ScalaBundle.message("cannot.find.implicit.parameters")
    owner.findImplicitArguments match {
      case Some(resolveResults) if resolveResults.length == implicitParameters.length =>
        if (resolveResults(i) == null) throw EvaluationException(cannotFindMessage)

        val exprText = resolveResults(i) match {
          case ScalaResolveResult(clazz: ScTrait, substitutor) if clazz.qualifiedName == "scala.reflect.ClassManifest" =>
            val argType = substitutor(clazz.`type`().get)
            argType match {
              case ParameterizedType(_, Seq(paramType)) => classManifestText(paramType)
              case _ =>
                throw EvaluationException(cannotFindMessage)
            }
          case ScalaResolveResult(clazz: ScTrait, substitutor) if clazz.qualifiedName == "scala.reflect.ClassTag" =>
            val argType = substitutor(clazz.`type`().get)
            argType match {
              case ParameterizedType(_, Seq(arg)) => classTagText(arg)
              case _ =>
                throw EvaluationException(cannotFindMessage)
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
              case _: ScObject => //todo: It can cover many cases!
                throw EvaluationException(ScalaBundle.message("implicit.parameters.from.dependent.objects"))
              case _ => elem.name //from scope
            }
        }
        val newExpr = createExpressionWithContextFromText(exprText, owner.getContext, owner)
        evaluatorFor(newExpr)
      case None =>
        throw EvaluationException(cannotFindMessage)
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
      case _ => throw EvaluationException(ScalaBundle.message("cannot.evaluate.parameter", name))
    }
    evaluator
  }

  def javaFieldEvaluator(field: PsiField, ref: ScReferenceExpression): Evaluator = {
    ref.qualifier match {
      case Some(qual) =>
        if (field.hasModifierPropertyScala("static")) {
          val eval = new ScalaTypeEvaluator(JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(field)))
          val name = field.name
          ScalaFieldEvaluator(eval, name)
        } else {
          val qualEvaluator = evaluatorFor(qual)
          ScalaFieldEvaluator(qualEvaluator, field.name)
        }
      case None =>
        val evaluator = thisOrImportedQualifierEvaluator(ref)
        ScalaFieldEvaluator(evaluator, field.name)
    }
  }

  def javaMethodEvaluator(method: PsiMethod, ref: ScReferenceExpression, arguments: Seq[ScExpression]): Evaluator = {

    def boxArguments(arguments: Seq[Evaluator], method: PsiElement): Seq[Evaluator] = {
      val params = method match {
        case fun: ScMethodLike => fun.effectiveParameterClauses.flatMap(_.parameters)
        case m: PsiMethod => m.parameters
        case _ => return arguments
      }

      arguments.zipWithIndex.map {
        case (arg, i) =>
          if (params.length <= i || isOfPrimitiveType(params(i))) arg
          else boxEvaluator(arg)
      }
    }

    val argEvals = boxArguments(arguments.map(evaluatorFor(_)), method)
    val methodPosition = DebuggerUtil.getSourcePositions(method.getNavigationElement)
    val signature = JVMNameUtil.getJVMSignature(method)
    ref.qualifier match {
      case Some(qual@Typeable(tp)) if tp.isPrimitive =>
        val boxEval = boxEvaluator(evaluatorFor(qual))
        ScalaMethodEvaluator(boxEval, method.name, signature, argEvals, None, methodPosition)
      case Some(_) if method.hasModifierPropertyScala("static") =>
        val eval = new ScalaTypeEvaluator(JVMNameUtil.getContextClassJVMQualifiedName(SourcePosition.createFromElement(method)))
        val name = method.name
        ScalaMethodEvaluator(eval, name, signature, argEvals, None, methodPosition)
      case Some(q) =>
        val name = method.name
        ScalaMethodEvaluator(evaluatorFor(q), name, signature, argEvals, None, methodPosition)
      case _ =>
        val evaluator = thisOrImportedQualifierEvaluator(ref)
        val name = method.name
        ScalaMethodEvaluator(evaluator, name, signature, argEvals, None, methodPosition)
    }
  }

  def unresolvedMethodEvaluator(ref: ScReferenceExpression, args: Seq[ScExpression]): Evaluator = {
    val argEvals = args.map(evaluatorFor(_))
    val name = NameTransformer.encode(ref.refName)
    ref.qualifier match {
      case Some(q) => ScalaMethodEvaluator(evaluatorFor(q), name, null, argEvals)
      case _ => ScalaMethodEvaluator(thisOrImportedQualifierEvaluator(ref), name, null, argEvals)
    }
  }

  def argumentEvaluators(fun: ScMethodLike, matchedParameters: Map[Parameter, Seq[ScExpression]],
                         call: ScExpression, ref: ScReferenceExpression, arguments: Seq[ScExpression]): Seq[Evaluator] = {

    val clauses = fun.effectiveParameterClauses
    val parameters = clauses.flatMap(_.effectiveParameters).map(Parameter(_))

    def addForNextClause(previousClausesEvaluators: Seq[Evaluator], clause: ScParameterClause): Seq[Evaluator] = {
      def isDefaultExpr(expr: ScExpression) = expr match {
        case ChildOf(p: ScParameter) => p.isDefaultParam
        case _ => false
      }
      previousClausesEvaluators ++ clause.effectiveParameters.map {
        param =>
          val p = Parameter(param)
          val exprsForP = matchedParameters.find(_._1.name == p.name).map(_._2).getOrElse(Seq.empty).filter(_ != null)
          if (p.isByName) throw new NeedCompilationException(ScalaBundle.message("method.with.by-name.parameters"))

          val evaluator =
            if (p.isRepeated) repeatedArgEvaluator(exprsForP, p.expectedType, call)
            else if (exprsForP.size > 1) throw EvaluationException(ScalaBundle.message("wrong.number.of.expressions"))
            else if (exprsForP.length == 1 && !isDefaultExpr(exprsForP.head)) evaluatorFor(exprsForP.head)
            else if (param.isImplicitParameter) implicitArgEvaluator(fun, param, call)
            else if (p.isDefault) {
              val paramIndex = parameters.indexOf(p)
              val methodName = defaultParameterMethodName(fun, paramIndex)
              val localParams = p.paramInCode.toSeq.flatMap(DebuggerUtil.localParamsForDefaultParam(_))
              val localParamRefs =
                localParams.map(td => createExpressionWithContextFromText(td.name, call.getContext, call))
              val localEvals = localParamRefs.map(evaluatorFor(_))
              functionEvaluator(ref.qualifier, ref, methodName, previousClausesEvaluators ++ localEvals, isDefaultArg = true)
            }
            else throw EvaluationException(ScalaBundle.message("cannot.evaluate.parameter", p.name))

          if (!isOfPrimitiveType(param)) boxEvaluator(evaluator)
          else evaluator
      }
    }

    val argEvaluators: Seq[Evaluator] = clauses.foldLeft(Seq.empty[Evaluator])(addForNextClause)

    if (argEvaluators.contains(null)) arguments.map(arg => evaluatorFor(arg))
    else argEvaluators
  }

  def functionEvaluator(qualOption: Option[ScExpression], ref: ScReferenceExpression,
                        funName: String, argEvaluators: Seq[Evaluator], isDefaultArg: Boolean = false): Evaluator = {

    def qualEvaluator(r: ScalaResolveResult) = {
      def defaultQualEvaluator = qualifierEvaluator(qualOption, ref)

      r.getActualElement match {
        case o: ScObject if funName == "apply" => objectEvaluator(o, () => defaultQualEvaluator)
        case _ => defaultQualEvaluator
      }
    }
    val name = NameTransformer.encode(funName)

    ref.bind() match {
      case Some(r) if r.tuplingUsed => throw EvaluationException(ScalaBundle.message("tupling.not.supported"))
      case None => throw EvaluationException(ScalaBundle.message("cannot.evaluate.method", funName))
      case Some(r @ traitMethod(tr, fun)) if fun.isPrivate || fun.isLocal || isDefaultArg =>
        val traitTypeEval = new ScalaTypeEvaluator(DebuggerUtil.getClassJVMName(tr, withPostfix = true))
        val qualEval = qualEvaluator(r)
        val withTraitImpl = ScalaMethodEvaluator(traitTypeEval, name, null, qualEval +: argEvaluators)
        val withDefault = ScalaMethodEvaluator(qualEval, name, null, argEvaluators, traitImplementation(fun))
        ScalaDuplexEvaluator(withTraitImpl, withDefault)
      case Some(ScalaResolveResult(fun: ScFunction, _)) if fun.isTopLevel =>
        val objectEval = stableObjectEvaluator(topLevelMemberClassName(fun))
        val signature = DebuggerUtil.getFunctionJVMSignature(fun)
        ScalaMethodEvaluator(objectEval, name, signature, argEvaluators,
          None, DebuggerUtil.getSourcePositions(fun.getNavigationElement))
      case Some(r) =>
        val resolve = r.element
        val qualEval = qualEvaluator(r)
        val signature = resolve match {
          case fun: ScFunction => DebuggerUtil.getFunctionJVMSignature(fun)
          case _ => null
        }
        ScalaMethodEvaluator(qualEval, name, signature, argEvaluators,
          traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
    }
  }

  def methodCallEvaluator(call: ScExpression, arguments: Seq[ScExpression], matchedParameters: Map[Parameter, Seq[ScExpression]]): Evaluator = {
    val ref = call match {
      case hasDeepestInvokedReference(r) => r
      case _ => throw EvaluationException(ScalaBundle.message("cannot.evaluate.method", call.getText))
    }

    val qualOption = ref.qualifier
    val resolve = ref.resolve()

    resolve match {
      case fun: ScFunctionDefinition if fun.isLocal =>
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
          case isInsideValueClass(c) if qualOption.isEmpty =>
            val clName = c.name
            val paramName = c.allClauses.flatMap(_.parameters).map(_.name).headOption.getOrElse("$this")
            val text = s"new $clName($paramName).${call.getText}"
            val expr = createExpressionFromText(text, call.getContext)
            evaluatorFor(expr)
          case _ =>
            val args = argumentEvaluators(fun, matchedParameters, call, ref, arguments)
            functionEvaluator(qualOption, ref, fun.name, args)
        }
      case method: PsiMethod =>
        javaMethodEvaluator(method, ref, arguments)
      case _ =>
        unresolvedMethodEvaluator(ref, arguments)
    }
  }

  def byNameParamEvaluator(ref: ScReferenceExpression, p: ScParameter, computeValue: Boolean): Evaluator =  {
    val paramEval = p match {
      case cp: ScClassParameter if cp.isCallByNameParameter =>
        val qualEval = qualifierEvaluator(ref.qualifier, ref)
        val name = NameTransformer.encode(cp.name)
        ScalaFieldEvaluator(qualEval, name, classPrivateThisField = true)
      case _: ScParameter if p.isCallByNameParameter =>
        calcLocal(p)
      case _ => throw EvaluationException(ScalaBundle.message("by.name.parameter.expected"))
    }
    if (computeValue) ScalaMethodEvaluator(paramEval, "apply", null, Nil)
    else paramEval
  }

  private def withOuterFieldEvaluator(containingClass: PsiElement, name: String, @Nls message: String) = {
    val (innerClass, iterationCount) = findContextClass { e =>
      e == null || {val nextClass = getContextClass(e); nextClass == null || nextClass == containingClass}
    }
    if (innerClass == null) throw EvaluationException(message)
    val thisEval = new ScalaThisEvaluator(iterationCount)
    ScalaFieldEvaluator(thisEval, name)
  }

  private def calcLocal(named: PsiNamedElement): Evaluator = {
    val name = NameTransformer.encode(named.name)
    val containingClass = getContextClass(named)

    val localVariableEvaluator: Evaluator = ScalaPsiUtil.nameContext(named) match {
      case param: ScParameter =>
        param.owner match {
          case fun@(_: ScFunction | _: ScFunctionExpr) => parameterEvaluator(fun, param)
          case _ =>
            // TODO: could also be primary constructor or extension method... maybe need handling too?
            throw EvaluationException(ScalaBundle.message("cannot.evaluate.parameter", param.name))
        }
      case caseCl: ScCaseClause => patternEvaluator(caseCl, named)
      case _: ScGenerator | _: ScForBinding if position != null && isNotUsedEnumerator(named, position.getElementAt) =>
        throw EvaluationException(ScalaBundle.message("not.used.from.for.statement", name))
      case LazyVal(_) => localLazyValEvaluator(named)
      case InsideAsync(_) =>
        val simpleLocal = new ScalaLocalVariableEvaluator(name, fileName)
        val fieldMacro = ScalaFieldEvaluator(new ScalaThisEvaluator(), name + "$macro")
        ScalaDuplexEvaluator(simpleLocal, fieldMacro)
      case _ => new ScalaLocalVariableEvaluator(name, fileName)
    }

    containingClass match {
      case `contextClass` | _: ScGenerator | _: ScForBinding => localVariableEvaluator
      case _ if contextClass == null => localVariableEvaluator
      case _ =>
        val fieldEval = withOuterFieldEvaluator(containingClass, name, ScalaBundle.message("cannot.evaluate.local.variable", name))
        ScalaDuplexEvaluator(fieldEval, localVariableEvaluator)
    }
  }


  def evaluatorForReferenceWithoutParameters(qualifier: Option[ScExpression],
                                             resolve: PsiElement,
                                             ref: ScReferenceExpression): Evaluator = {

    def calcLocalObject(obj: ScObject) = {
      val containingClass = getContextClass(obj)
      val name = NameTransformer.encode(obj.name) + "$module"
      if (containingClass == contextClass) {
        new ScalaLocalVariableEvaluator(name, fileName)
      } else {
        val fieldEval = withOuterFieldEvaluator(containingClass, name, ScalaBundle.message("cannot.evaluate.local.object", name))
        fieldEval
      }
    }

    val labeledOrSynthetic = labeledOrSyntheticEvaluator(ref, resolve)
    if (labeledOrSynthetic.isDefined) return labeledOrSynthetic.get

    val isLocalValue = DebuggerUtil.isLocalV(resolve)

    resolve match {
      case isInsideLocalFunction(fun) && (named: PsiNamedElement) if isLocalValue =>
        ScalaDuplexEvaluator(calcLocal(named), parameterEvaluator(fun, resolve))
      case p: ScParameter if p.isCallByNameParameter =>
        byNameParamEvaluator(ref, p, computeValue = true)
      case obj: ScObject if isLocalValue => calcLocalObject(obj)
      case named: PsiNamedElement if isLocalValue =>
        calcLocal(named)
      case obj: ScObject =>
        objectEvaluator(obj, () => qualifierEvaluator(qualifier, ref))
      case _: PsiMethod | _: ScSyntheticFunction =>
        methodCallEvaluator(ref, Nil, Map.empty)
      case privateThisField(_) =>
        val named = resolve.asInstanceOf[ScNamedElement]
        val qualEval = qualifierEvaluator(qualifier, ref)
        val name = NameTransformer.encode(named.name)
        ScalaFieldEvaluator(qualEval, name, classPrivateThisField = true)
      case cp: ScClassParameter if qualifier.isEmpty && ValueClassType.isValueClass(cp.containingClass) =>
        //methods of value classes have hidden argument with underlying value
        new ScalaLocalVariableEvaluator("$this", fileName)
      case _: ScClassParameter | _: ScBindingPattern =>
        //this is scala "field"
        val named = resolve.asInstanceOf[ScNamedElement]
        val name = NameTransformer.encode(named.name)
        val qualEval = qualifierEvaluator(qualifier, ref)
        val withSimpleNameEval = ScalaMethodEvaluator(qualEval, name, null /* todo */ , Seq.empty,
          traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
        getContextClass(named) match {
          //in some cases compiler uses full qualified names for fields and methods
          case clazz: ScTemplateDefinition if ScalaPsiUtil.hasStablePath(clazz)
                  && clazz.members.contains(ScalaPsiUtil.nameContext(named)) =>
            val qualName = clazz.qualifiedName
            val newName = qualName.split('.').map(NameTransformer.encode).mkString("$") + "$$" + name
            val reserveEval = ScalaMethodEvaluator(qualEval, newName, null /* todo */ , Seq.empty,
              traitImplementation(resolve), DebuggerUtil.getSourcePositions(resolve.getNavigationElement))
            ScalaDuplexEvaluator(withSimpleNameEval, reserveEval)
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
          case Some(qual) => ScalaFieldEvaluator(evaluatorFor(qual), name)
          case None => new ScalaLocalVariableEvaluator(name, fileName)
        }
        ScalaDuplexEvaluator(fieldOrVarEval, unresolvedMethodEvaluator(ref, Seq.empty))
    }
  }

  def labeledOrSyntheticEvaluator(ref: ScReferenceExpression, resolve: PsiElement): Option[Evaluator] = {
    if (resolve == null) return None

    val labeledValue = resolve.getUserData(CodeFragmentFactoryContextWrapper.LABEL_VARIABLE_VALUE_KEY)
    if (labeledValue != null)
      return Some(new IdentityEvaluator(labeledValue))

    val isSynthetic = codeFragment.isAncestorOf(resolve)
    if (isSynthetic && ref.qualifier.isEmpty)
      Some(syntheticVariableEvaluator(ref.refName))
    else None
  }

  def qualifierEvaluator(qualifier: Option[ScExpression], ref: ScReferenceExpression): Evaluator = qualifier match {
    case Some(q) => evaluatorFor(q)
    case _ => thisOrImportedQualifierEvaluator(ref)
  }

  def patternEvaluator(caseCl: ScCaseClause, namedElement: PsiNamedElement): Evaluator = {
    val name = namedElement.name
    if (caseCl.getParent != null) {
      val pattern = caseCl.pattern
      if (pattern.isEmpty) throw EvaluationException(ScalaBundle.message("cannot.find.pattern"))
      caseCl.getParent.getParent match {
        case matchStmt: ScMatch if namedElement.isInstanceOf[ScPattern] =>
          val expr = matchStmt.expression match {
            case None => throw EvaluationException(ScalaBundle.message("cannot.find.expression.of.match"))
            case Some(e) => e
          }

          val exprEval =
            try evaluatorFor(expr)
            catch {
              case _: NeedCompilationException =>
                new ScalaCompilingEvaluator(expr, ScalaCodeFragment(expr.getText)(caseCl.getProject))
            }

          val fromPatternEvaluator = evaluateSubpatternFromPattern(exprEval, pattern.get, namedElement.asInstanceOf[ScPattern])
          ScalaDuplexEvaluator(new ScalaLocalVariableEvaluator(name, fileName), fromPatternEvaluator)
        case _: ScBlockExpr => //it is anonymous function
          val argEvaluator = new ScalaLocalVariableEvaluator("", fileName)
          argEvaluator.setMethodName("apply")
          argEvaluator.setParameterIndex(0)
          val fromPatternEvaluator = evaluateSubpatternFromPattern(argEvaluator, pattern.get, namedElement.asInstanceOf[ScPattern])
          ScalaDuplexEvaluator(new ScalaLocalVariableEvaluator(name, fileName), fromPatternEvaluator)
        case _ => new ScalaLocalVariableEvaluator(name, fileName)
      }
    } else throw EvaluationException(ScalaBundle.message("invalid.case.clause"))
  }

  def assignmentEvaluator(stmt: ScAssignment): Evaluator = {
    val message = ScalaBundle.message("assignent.without.expression")
    if (stmt.isNamedParameter) {
      stmt.rightExpression match {
        case Some(expr) => evaluatorFor(expr)
        case _ => throw EvaluationException(message)
      }
    } else {
      stmt.leftExpression match {
        case call: ScMethodCall =>
          val invokedText = call.getInvokedExpr.getText
          val rExprText = stmt.rightExpression.fold("null")(_.getText)
          val args = (call.args.exprs.map(_.getText) :+ rExprText).mkString("(", ", ", ")")
          val exprText = s"($invokedText).update$args"
          val expr = createExpressionWithContextFromText(exprText, stmt.getContext, stmt)
          evaluatorFor(expr)
        case _ =>
          val leftEvaluator = evaluatorFor(stmt.leftExpression)
          val rightEvaluator = stmt.rightExpression match {
            case Some(expr) => evaluatorFor(expr)
            case _ => throw EvaluationException(message)
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
    def evaluateConstructorOrInfix(exprEval: Evaluator, ref: ScStableCodeReference, pattern: ScPattern, nextPatternIndex: Int): Evaluator = {
      ref.resolve() match {
        case fun: ScFunctionDefinition =>
          val elem = ref.bind().get.getActualElement //object or case class
        val qual = ref.qualifier.map(q => createExpressionWithContextFromText(q.getText, q.getContext, q))
          val refExpr = createExpressionWithContextFromText(ref.getText, ref.getContext, ref)
          val refEvaluator = evaluatorForReferenceWithoutParameters(qual, elem, refExpr.asInstanceOf[ScReferenceExpression])

          val funName = fun.name
          val newEval =
            if (funName == "unapply") {
              val extractEval = ScalaMethodEvaluator(refEvaluator, funName, DebuggerUtil.getFunctionJVMSignature(fun), Seq(exprEval))
              if (pattern.subpatterns.length == 1)
                ScalaMethodEvaluator(extractEval, "get", null, Seq.empty)
              else if (pattern.subpatterns.length > 1) {
                val getEval = ScalaMethodEvaluator(extractEval, "get", null, Seq.empty)
                ScalaFieldEvaluator(getEval, s"_${nextPatternIndex + 1}")
              }
              else throw EvaluationException(ScalaBundle.message("unapply.without.arguments"))
            } else if (funName == "unapplySeq") {
              val extractEval = ScalaMethodEvaluator(refEvaluator, funName, DebuggerUtil.getFunctionJVMSignature(fun), Seq(exprEval))
              val getEval = ScalaMethodEvaluator(extractEval, "get", null, Seq.empty)
              val indexExpr = createExpressionFromText("" + nextPatternIndex)(pattern.getManager)
              val indexEval = evaluatorFor(indexExpr)
              ScalaMethodEvaluator(getEval, "apply", JVMNameUtil.getJVMRawText("(I)Ljava/lang/Object;"), Seq(indexEval))
            } else throw EvaluationException(ScalaBundle.message("pattern.doesnot.resolves.to.unapply", ref.refName))
          val nextPattern = pattern.subpatterns(nextPatternIndex)
          evaluateSubpatternFromPattern(newEval, nextPattern, subPattern)
        case _ => throw EvaluationException(ScalaBundle.message("pattern.doesnot.resolves.to.unapply", ref.refName))
      }
    }

    if (pattern == null || subPattern == null)
      throw new IllegalArgumentException("Patterns should not be null")
    val nextPatternIndex: Int = pattern.subpatterns.indexWhere(next => subPattern.withParentsInFile.contains(next))

    if (pattern == subPattern) exprEval
    else if (nextPatternIndex < 0) throw new IllegalArgumentException("Pattern is not ancestor of subpattern")
    else {
      pattern match {
        case naming: ScNamingPattern => evaluateSubpatternFromPattern(exprEval, naming.named, subPattern)
        case _: ScTypedPattern => evaluateSubpatternFromPattern(exprEval, pattern.subpatterns.head, subPattern)
        case par: ScParenthesisedPattern =>
          val withoutPars = par.innerElement.getOrElse(throw new IllegalStateException("Empty parentheses pattern"))
          evaluateSubpatternFromPattern(exprEval, withoutPars, subPattern)
        case tuple: ScTuplePattern =>
          val nextPattern = tuple.subpatterns(nextPatternIndex)
          val newEval = ScalaFieldEvaluator(exprEval, s"_${nextPatternIndex + 1}")
          evaluateSubpatternFromPattern(newEval, nextPattern, subPattern)
        case constr: ScConstructorPattern =>
          val ref: ScStableCodeReference = constr.ref
          evaluateConstructorOrInfix(exprEval, ref, constr, nextPatternIndex)
        case infix: ScInfixPattern =>
          val ref: ScStableCodeReference = infix.operation
          evaluateConstructorOrInfix(exprEval, ref, infix, nextPatternIndex)
        //todo: handle infix with tuple right pattern
        case _: ScCompositePattern => throw EvaluationException(ScalaBundle.message("pattern.alternatives.cannot.bind.vars"))
        case _: ScXmlPattern => throw EvaluationException(ScalaBundle.message("xml.patterns.not.supported")) //todo: xml patterns
        case _ => throw EvaluationException(ScalaBundle.message("kind.of.patterns.not.supported", pattern.getText)) //todo: xml patterns
      }
    }
  }

  def newTemplateDefinitionEvaluator(templ: ScNewTemplateDefinition): Evaluator = {
    templ.extendsBlock.templateParents match {
      case Some(parents) =>
        if (parents.typeElements.length != 1) {
          throw new NeedCompilationException(ScalaBundle.message("anon.classes.not.supported"))
        }
        parents.constructorInvocation match {
          case Some(constrInvocation) =>
            constrInvocation.typeElement.calcType.extractClass match {
              case Some(clazz) if clazz.qualifiedName == "scala.Array" =>
                val typeArgs = constrInvocation.typeArgList.fold("")(_.getText)
                val args = constrInvocation.args.fold("(0)")(_.getText)
                val exprText = s"_root_.scala.Array.ofDim$typeArgs$args"
                val expr = createExpressionWithContextFromText(exprText, templ.getContext, templ)
                evaluatorFor(expr)
              case Some(clazz) =>
                val jvmName = DebuggerUtil.getClassJVMName(clazz)
                val typeEvaluator = new ScalaTypeEvaluator(jvmName)
                val argumentEvaluators = constructorArgumentsEvaluators(templ, constrInvocation, clazz)
                constrInvocation.reference.map(_.resolve()) match {
                  case Some(named: PsiNamedElement) =>
                    val signature = DebuggerUtil.constructorSignature(named)
                    ScalaMethodEvaluator(typeEvaluator, "<init>", signature, argumentEvaluators)
                  case _ =>
                    ScalaMethodEvaluator(typeEvaluator, "<init>", null, argumentEvaluators)
                }
              case _ => throw EvaluationException(ScalaBundle.message("new.expression.without.class.reference"))
            }

          case None => throw EvaluationException(ScalaBundle.message("new.expression.without.constructor.call"))
        }
      case _ => throw EvaluationException(ScalaBundle.message("new.expression.without.template.parents"))
    }
  }

  def constructorArgumentsEvaluators(newTd: ScNewTemplateDefinition,
                                     constrInvocation: ScConstructorInvocation,
                                     clazz: PsiClass): Seq[Evaluator] = {
    val constrDef = constrInvocation.reference match {
      case Some(ResolvesTo(elem)) => elem
      case _ => throw EvaluationException(ScalaBundle.message("could.not.resolve.constructor"))
    }
    val explicitArgs = constrInvocation.arguments.flatMap(_.exprs)
    val explEvaluators =
      for {
        arg <- explicitArgs
      } yield {
        val eval = evaluatorFor(arg)
        val param = ScalaPsiUtil.parameterOf(arg).flatMap(_.psiParam)
        if (param.exists(!isOfPrimitiveType(_))) boxEvaluator(eval)
        else eval
      }
    constrDef match {
      case scMethod: ScMethodLike =>
        val scClass = scMethod.containingClass.asInstanceOf[ScClass]
        val containingClass = getContextClass(scClass)
        val implicitParams = scMethod.parameterList.params.filter(_.isImplicitParameter)

        val implicitsEvals =
          for {
            p <- implicitParams
          } yield {
            val eval = implicitArgEvaluator(scMethod, p, constrInvocation)
            if (isOfPrimitiveType(p)) eval
            else boxEvaluator(eval)
          }
        val (outerClass, iters) = findContextClass(e => e == null || e == containingClass)
        val outerThis = outerClass match {
          case obj: ScObject if isStable(obj) => None
          case null => None
          case _ => Some(new ScalaThisEvaluator(iters))
        }
        val locals = DebuggerUtil.localParamsForConstructor(scClass)
        outerThis ++: explEvaluators ++: implicitsEvals ++: locals.map(fromLocalArgEvaluator)
      case _ => explEvaluators
    }
  }

  def fromLocalArgEvaluator(local: ScTypedDefinition): Evaluator = {
    val name = local.asInstanceOf[PsiNamedElement].name
    val elemAt = position.getElementAt
    val ref = createExpressionWithContextFromText(name, elemAt, elemAt)
    val refEval = evaluatorFor(ref)

    FromLocalArgEvaluator(refEval)
  }


  def expressionFromTextEvaluator(string: String, context: PsiElement): Evaluator = {
    val expr = createExpressionWithContextFromText(string, context.getContext, context)
    evaluatorFor(expr)
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
    ScalaMethodEvaluator(new ScalaThisEvaluator(), methodName, null, Seq(localRefEval, bitmapEval))
  }

  def ifStmtEvaluator(stmt: ScIf): Evaluator = {
    val condEvaluator = stmt.condition match {
      case Some(cond) => evaluatorFor(cond)
      case None => throw EvaluationException(ScalaBundle.message("if.statement.without.condition"))
    }
    val ifBranch = stmt.thenExpression match {
      case Some(th) => evaluatorFor(th)
      case None => throw EvaluationException(ScalaBundle.message("if.statement.without.if.branch"))
    }
    val elseBranch = stmt.elseExpression.map(evaluatorFor(_))
    new ScalaIfEvaluator(condEvaluator, ifBranch, elseBranch)
  }

  def literalEvaluator(literal: ScLiteral): Evaluator = literal match {
    case interpolated: ScInterpolatedStringLiteral =>
      interpolated.desugaredExpression
          .fold(ScalaLiteralEvaluator(literal, literal.getValue): Evaluator) {
            case (_, call) => evaluatorFor(call)
          }
    case _ =>
      literal.getValue match {
        case symbol: Symbol =>
          val expr = createExpressionFromText(
            s"""Symbol("${symbol.name}")""",
            literal.getContext
          )
          evaluatorFor(expr)
        case value =>
          ScalaLiteralEvaluator(literal, value)
      }
  }

  def whileStmtEvaluator(ws: ScWhile): Evaluator = {
    val condEvaluator = ws.condition match {
      case Some(cond) => evaluatorFor(cond)
      case None => throw EvaluationException(ScalaBundle.message("while.statement.without.condition"))
    }
    val iterationEvaluator = ws.expression match {
      case Some(body) => evaluatorFor(body)
      case None => throw EvaluationException(ScalaBundle.message("while.statement.without.body"))
    }

    new WhileStatementEvaluator(condEvaluator, iterationEvaluator, null)
  }

  def doStmtEvaluator(doSt: ScDo): Evaluator = {
    val condEvaluator = doSt.condition match {
      case Some(cond) => evaluatorFor(cond)
      case None =>
        throw EvaluationException(ScalaBundle.message("do.statement.without.condition"))
    }
    val iterationEvaluator = doSt.body match {
      case Some(body) => evaluatorFor(body)
      case None =>
        throw EvaluationException(ScalaBundle.message("do.statement.without.body"))
    }
    new ScalaDoStmtEvaluator(condEvaluator, iterationEvaluator)
  }

  def scMethodCallEvaluator(methodCall: ScMethodCall): Evaluator = {
    def applyCall(invokedText: String, argsText: String) = {
      val newExprText = s"($invokedText).apply$argsText"
      createExpressionWithContextFromText(newExprText, methodCall.getContext, methodCall)
    }

    @tailrec
    def collectArgumentsAndBuildEvaluator(call: ScMethodCall,
                                          collected: Seq[ScExpression] = Seq.empty,
                                          tailString: String = "",
                                          matchedParameters: Map[Parameter, Seq[ScExpression]] = Map.empty): Evaluator = {
      if (call.isApplyOrUpdateCall) {
        if (!call.isUpdateCall) {
          val expr = applyCall(call.getInvokedExpr.getText, call.args.getText + tailString)
          return evaluatorFor(expr)
        } else {
          //should be handled on assignment
          throw new NeedCompilationException(ScalaBundle.message("update.method.is.not.supported"))
        }
      }
      val message = ScalaBundle.message("cannot.evaluate.method", call.getText)

      import MethodInvocation.matchedParametersMap
      call match {
        case ScMethodCall(_: ScReferenceExpression, argumentExpressions) =>
          methodCallEvaluator(methodCall, argumentExpressions ++ collected, matchedParameters ++ matchedParametersMap(call))
        case ScMethodCall(newCall: ScMethodCall, argumentExpressions) =>
          collectArgumentsAndBuildEvaluator(newCall, argumentExpressions ++ collected, call.args.getText + tailString,
            matchedParameters ++ matchedParametersMap(call))
        case ScMethodCall(ScGenericCall(ScReferenceExpression(_: PsiMethod), _), argumentExpressions) =>
          methodCallEvaluator(methodCall, argumentExpressions ++ collected, matchedParameters ++ matchedParametersMap(call))
        case ScMethodCall(gen@ScGenericCall(ref, _), _) =>
          ref.`type`().getOrAny match {
            //isApplyOrUpdateCall does not work for generic calls
            case ExtractClass(psiClass) if psiClass.findMethodsByName("apply", true).nonEmpty =>
              val typeArgsText = gen.typeArgs.getText.mkString
              val expr = applyCall(ref.getText, s"$typeArgsText${call.args.getText}$tailString")
              evaluatorFor(expr)
            case _ => throw EvaluationException(message)
          }
        case _ => throw EvaluationException(message)
      }
    }

    methodCall match {
      case hasDeepestInvokedReference(ScReferenceExpression.withQualifier(implicitlyConvertedTo(expr))) =>
        val copy = methodCall.copy().asInstanceOf[ScMethodCall]
        copy match {
          case hasDeepestInvokedReference(ScReferenceExpression.withQualifier(q)) =>
            q.replaceExpression(expr, removeParenthesis = false)
            evaluatorFor(copy)
          case _ =>
            val message = ScalaBundle.message("method.call.implicitly.converted.qualifier", methodCall.getText)
            throw EvaluationException(message)
        }
      case _ =>
        //todo: handle partially applied functions
        collectArgumentsAndBuildEvaluator(methodCall)
    }
  }

  def infixExpressionEvaluator(infix: ScInfixExpr): Evaluator = {
    object isUpdate {
      private val Regex = "(.+)=$".r

      def unapply(operation: ScReferenceExpression): Option[String] = operation.refName match {
        case Regex(name) =>
          operation.resolve() match {
            case named: PsiNamedElement if named.name == name => Some(name)
            case _ => None
          }
        case _ => None
      }
    }

    val newExpression = infix match {
      case ScInfixExpr.withAssoc(ElementText(baseText), isUpdate(operation), ElementText(argumentText)) =>
        val exprText = s"$baseText = $baseText $operation $argumentText"
        createExpressionWithContextFromText(exprText, infix.getContext, infix)
      case _ =>
        createEquivMethodCall(infix)
    }

    evaluatorFor(newExpression)
  }

  def blockExprEvaluator(block: ScBlock): Evaluator = {
    withNewSyntheticVariablesHolder {
      val evaluators = block.statements.filter(!_.isInstanceOf[ScImportStmt]).map(evaluatorFor)
      new ScalaBlockExpressionEvaluator(evaluators)
    }
  }

  def postfixExprEvaluator(p: ScPostfixExpr): Evaluator = {
    val equivRef = createEquivQualifiedReference(p)
    evaluatorFor(equivRef)
  }

  def prefixExprEvaluator(p: ScPrefixExpr): Evaluator = {
    val newExprText = s"(${p.operand.getText}).unary_${p.operation.refName}"
    val newExpr = createExpressionWithContextFromText(newExprText, p.getContext, p)
    evaluatorFor(newExpr)
  }

  def refExpressionEvaluator(ref: ScReferenceExpression): Evaluator = {
    ref.qualifier match {
      case Some(implicitlyConvertedTo(e)) =>
        val copy = ref.copy().asInstanceOf[ScReferenceExpression]
        copy.qualifier.get.replaceExpression(e, removeParenthesis = false)
        evaluatorFor(copy)
      case _ =>
        val resolve: PsiElement = ref.resolve()
        evaluatorForReferenceWithoutParameters(ref.qualifier, resolve, ref)
    }
  }

  def tupleEvaluator(tuple: ScTuple): Evaluator = {
    val exprText = "_root_.scala.Tuple" + tuple.exprs.length + tuple.exprs.map(_.getText).mkString("(", ", ", ")")
    val expr = createExpressionWithContextFromText(exprText, tuple.getContext, tuple)
    evaluatorFor(expr)
  }

  def valOrVarDefinitionEvaluator(pList: ScPatternList, expr: ScExpression): ScalaBlockExpressionEvaluator = {
    val evaluatorsBuilder = ArraySeq.newBuilder[Evaluator]
    val exprEval = new ScalaCachingEvaluator(evaluatorFor(expr))
    evaluatorsBuilder += exprEval
    for {
      pattern <- pList.patterns
      binding <- pattern.bindings
    } {
      val name = binding.name
      createSyntheticVariable(name)
      val leftEval = syntheticVariableEvaluator(name)
      val rightEval = evaluateSubpatternFromPattern(exprEval, pattern, binding)
      evaluatorsBuilder += new AssignmentEvaluator(leftEval, rightEval)
    }
    new ScalaBlockExpressionEvaluator(evaluatorsBuilder.result())
  }

  def variableDefinitionEvaluator(vd: ScVariableDefinition): Evaluator = {
    vd.expr match {
      case None => throw EvaluationException(ScalaBundle.message("variable.definition.needs.right.hand.side", vd.getText))
      case Some(e) => valOrVarDefinitionEvaluator(vd.pList, e)
    }
  }

  def patternDefinitionEvaluator(pd: ScPatternDefinition)
                                : Evaluator = {
    pd.expr match {
      case None => throw EvaluationException(ScalaBundle.message("value.definition.needs.right.hand.side", pd.getText))
      case Some(e) => valOrVarDefinitionEvaluator(pd.pList, e)
    }
  }

  def postProcessExpressionEvaluator(expr: ScExpression, evaluator: Evaluator): Evaluator = {

    //boxing and unboxing actions
    def unbox(typeTo: String) = unaryEvaluator(unboxEvaluator(evaluator), typeTo)
    def box() = boxEvaluator(evaluator)
    def valueClassInstance(eval: Evaluator) = {
      expr match {
        case _: ScNewTemplateDefinition => eval
        case Typeable(_: ValType) => eval
        case Typeable(tp@ValueClassType(inner)) =>
          valueClassInstanceEvaluator(eval, inner, tp)
        case _ => eval
      }
    }

    val unboxed = expr.smartExpectedType() match {
      case Some(Int) => unbox("toInteger")
      case Some(Byte) => unbox("toByte")
      case Some(Long) => unbox("toLong")
      case Some(Boolean) => unboxEvaluator(evaluator)
      case Some(Float) => unbox("toFloat")
      case Some(Short) => unbox("toShort")
      case Some(Double) => unbox("toDouble")
      case Some(Char) => unbox("toCharacter")
      case Some(Unit) => new BlockStatementEvaluator(Array(evaluator, unitEvaluator()))
      case None => evaluator
      case _ => box()
    }

    valueClassInstance(unboxed)
  }

  def classTagText(arg: ScType): String = {
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
      case JavaArrayType(argument) =>
        "_root_.scala.reflect.ClassManifest.arrayType(" + classManifestText(argument) + ")"
      case ParameterizedType(ScDesignatorType(clazz: ScClass), Seq(arg))

        if clazz.qualifiedName == "scala.Array" =>
        "_root_.scala.reflect.ClassManifest.arrayType(" + classManifestText(arg) + ")"
      /*case ScParameterizedType(des, args) =>
        ScType.extractClass(des, Option(expr.getProject)) match {
          case Some(clazz) =>
            "_root_.scala.reflect.ClassManifest.classType(" +
          case _ => "null"
        }*/   //todo:
      case _ => scType.extractClass match {
        case Some(clss) => "_root_.scala.reflect.ClassManifest.classType(classOf[_root_." +
          clss.qualifiedName + "])"
        case _ => "_root_.scala.reflect.ClassManifest.classType(classOf[_root_.java.lang." +
          "Object])"
      }
    }
  }

  def isOfPrimitiveType(param: PsiParameter): Boolean = param match { //todo specialized type parameters
    case p: ScParameter =>
      val tp: ScType = p.`type`().getOrAny
      tp.isPrimitive
    case _: PsiParameter =>
      val tp = param.getType
      import com.intellij.psi.PsiType._
      Set[PsiType](BOOLEAN, INT, CHAR, DOUBLE, FLOAT, LONG, BYTE, SHORT).contains(tp)
    case _ => false
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
    unboxEvaluator(ScalaMethodEvaluator(BOXES_RUN_TIME, "takeNot", rawText, boxed(eval)))
  }
  def eqEvaluator(left: Evaluator, right: Evaluator): Evaluator = {
    new ScalaEqEvaluator(left, right)
  }

  def neEvaluator(left: Evaluator, right: Evaluator): Evaluator = {
    notEvaluator(eqEvaluator(left, right))
  }

  def unitEvaluator(): Evaluator = {
    ScalaFieldEvaluator(BOXED_UNIT, "UNIT")
  }

  def unaryEvaluator(eval: Evaluator, boxesRunTimeName: String): Evaluator = {
    val rawText = JVMNameUtil.getJVMRawText("(Ljava/lang/Object;)Ljava/lang/Object;")
    unboxEvaluator(ScalaMethodEvaluator(BOXES_RUN_TIME, boxesRunTimeName, rawText, boxed(eval)))
  }

  def binaryEvaluator(left: Evaluator, right: Evaluator, boxesRunTimeName: String): Evaluator = {
    val rawText = JVMNameUtil.getJVMRawText("(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;")
    unboxEvaluator(ScalaMethodEvaluator(BOXES_RUN_TIME, boxesRunTimeName, rawText, boxed(left, right)))
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

  object implicitlyConvertedTo {
    def unapply(expr: ScExpression): Option[ScExpression] = {
      expr.implicitElement(fromUnderscore = true).collect {
        case function: ScFunction => function
      }.map { fun =>
        val callText = s"${fun.name}(${expr.getText})"
        val newExprText = fun.containingClass match {
          case o: ScObject if isStable(o) => s"${o.qualifiedName}.$callText"
          case _: ScObject => //todo: It can cover many cases!
            throw EvaluationException(ScalaBundle.message("implicit.conversions.from.dependent.objects"))
          case _ => callText //from scope
        }
        createExpressionWithContextFromText(newExprText, expr.getContext, expr)
      }
    }
  }

  @tailrec
  final def isStable(o: ScObject): Boolean = {
    val context = PsiTreeUtil.getParentOfType(o, classOf[ScTemplateDefinition], classOf[ScExpression])
    if (context == null) return true
    context match {
      case o: ScObject => isStable(o)
      case _ => false
    }
  }

  def getContextClass(elem: PsiElement, strict: Boolean = true): PsiElement = {
    if (!strict && isGenerateClass(elem)) elem
    else elem.contexts.find(isGenerateClass).orNull
  }

  def isGenerateClass(elem: PsiElement): Boolean =
    isGenerateNonAnonfunClass(elem) ||
      hasTopLevelMembers(elem) ||
      isGenerateAnonfun(elem)

  private def isGenerateAnonfun(elem: PsiElement): Boolean =
    if (isCompiledWithIndyLambdas(elem.getContainingFile))
      isPartialFunction(elem) || isAnonfunInsideSuperCall(elem)
    else isGenerateAnonfun211(elem)

  def isGenerateNonAnonfunClass(elem: PsiElement): Boolean = {
    elem match {
      case newTd: ScNewTemplateDefinition if !DebuggerUtil.generatesAnonClass(newTd) => false
      case _: PsiClass => true
      case _ => false
    }
  }

  def isAnonfunInsideSuperCall(elem: PsiElement): Boolean = {
    def isInsideSuperCall(td: ScTypeDefinition) = {
      val extBlock = Option(td).map(_.extendsBlock).orNull
      PsiTreeUtil.getParentOfType(elem, classOf[ScEarlyDefinitions], classOf[ScConstructorInvocation]) match {
        case ed: ScEarlyDefinitions if ed.getParent == extBlock => true
        case c: ScConstructorInvocation if c.getParent.getParent == extBlock => true
        case _ => false
      }
    }

    val containingClass = PsiTreeUtil.getParentOfType(elem, classOf[ScTypeDefinition])
    isGenerateAnonfun211(elem) && isInsideSuperCall(containingClass)
  }

  def isGenerateAnonfun211(elem: PsiElement): Boolean = {

    @CachedInUserData(elem, BlockModificationTracker(elem))
    def isAnonfunCached: Boolean = {
      elem match {
        case e: ScExpression if ScUnderScoreSectionUtil.underscores(e).nonEmpty => true
        case b: ScBlock if b.isAnonymousFunction => false //handled in isGenerateAnonfunSimple
        case e: ScExpression if ScalaPsiUtil.isByNameArgument(e) || ScalaPsiUtil.isArgumentOfFunctionType(e) => true
        case ScalaPsiUtil.MethodValue(_) => true
        case ChildOf(argExprs: ScArgumentExprList) && InsideAsync(call)
          if call.args == argExprs => true
        case _ => false
      }
    }

    def isGenerateAnonfunWithCache: Boolean = {
      if (elem == null || !elem.isValid || DumbService.isDumb(elem.getProject)) false
      else isAnonfunCached
    }


    def isGenerateAnonfunSimple: Boolean = {
      elem match {
        case _: ScFunctionExpr => true
        case (_: ScExpression) childOf (_: ScFor) => true
        case (_: ScGuard) childOf (_: ScEnumerators) => true
        case (g: ScGenerator) childOf (enums: ScEnumerators) if !enums.generators.headOption.contains(g) => true
        case _: ScForBinding => true
        case _ => false
      }
    }

    isGenerateAnonfunSimple || isPartialFunction(elem) || isGenerateAnonfunWithCache
  }

  def isPartialFunction(elem: PsiElement): Boolean = elem match {
    case (_: ScCaseClauses) childOf (b: ScBlockExpr) if b.isAnonymousFunction => true
    case _ => false
  }

  def anonClassCount(elem: PsiElement): Int = { //todo: non irrefutable patterns?
    elem match {
      case (_: ScExpression) childOf (f: ScFor) =>
        f.enumerators.fold(1)(e => e.generators.length)
      case (e: ScEnumerator) childOf (enums: ScEnumerators) =>
        enums.children.takeWhile(_ != e).count(_.isInstanceOf[ScGenerator])
      case _ => 1
    }
  }

  private def localFunctionIndex(named: PsiNamedElement): Int = {
    elementsWithSameNameIndex(named, {
      case f: ScFunction if f.isLocal => true
      case ScalaPsiUtil.inNameContext(LazyVal(_)) => true
      case _ => false
    })
  }

  private def lazyValIndex(named: PsiNamedElement): Int = {
    elementsWithSameNameIndex(named, {
      case ScalaPsiUtil.inNameContext(LazyVal(_)) => true
      case _ => false
    })
  }

  private def defaultParameterMethodName(method: ScMethodLike, paramIndex: Int): String = {
    method match {
      case fun: ScFunction if !fun.isConstructor =>
        def hasDefaultParamAtIndex(f: ScFunction, paramIndex: Int): Boolean = {
          val paramAtIndex = f.effectiveParameterClauses
            .flatMap(_.effectiveParameters)
            .lift(paramIndex)

          paramAtIndex.exists(_.isDefaultParam)
        }

        def localFunctionSuffix: String = {
          if (!fun.isLocal) ""
          else "$" + elementsWithSameNameIndex(fun, {
            case f: ScFunction if f.isLocal && hasDefaultParamAtIndex(f, paramIndex) => true
            case _ => false
          })
        }

        fun.name + "$default$" + (paramIndex + 1) + localFunctionSuffix
      case _ if method.isConstructor =>  "$lessinit$greater$default$" + (paramIndex + 1) + "()"
    }
  }

  private def elementsWithSameNameIndex(named: PsiNamedElement, condition: PsiElement => Boolean): Int = {
    val containingClass = getContextClass(named)
    if (containingClass == null) return -1

    val name = named.name

    val traverser = SyntaxTraverser.psiTraverser(containingClass).forceIgnore {
      case `containingClass` => false
      case elem if isGenerateClass(elem) => true
      case _ => false
    }
    val sameNameCondition: Condition[PsiNamedElement] = {
      n => n.name == name && condition(n)
    }
    val traversal =
      if (isAtLeast212(named)) traverser.postOrderDfsTraversal()
      else traverser.preOrderDfsTraversal()

    val sameNameElements =
      traversal
        .filter(classOf[PsiNamedElement])
        .filter(sameNameCondition)

    sameNameElements.indexOf(_ == named) + 1
  }

  def traitImplementation(elem: PsiElement): Option[JVMName] = {
    val clazz = getContextClass(elem)
    clazz match {
      case t: ScTrait =>
        Some(DebuggerUtil.getClassJVMName(t, withPostfix = true))
      case _ => None
    }
  }

  def isNotUsedEnumerator(named: PsiNamedElement, place: PsiElement): Boolean = {
    named match {
      case ScalaPsiUtil.inNameContext(enum @ (_: ScForBinding | _: ScGenerator)) =>
        enum.getParent.getParent match {
          case ScFor(enums, body) =>
            enums.namings.map(_.pattern) match {
              case Seq(_: ScReferencePattern) => return false //can always evaluate from single simple generator
              case _ =>
            }

            def insideBody = PsiTreeUtil.isAncestor(body, place, false)
            def isNotUsed = ReferencesSearch.search(named, new LocalSearchScope(body)).findFirst() == null

            insideBody && isNotUsed

          case _ => false
        }
      case _ => false
    }
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
          case fun: ScFunction if fun.isLocal &&
                  !fun.parameters.exists(param => PsiTreeUtil.isAncestor(param, elem, false)) =>
            Some(fun)
          case other if other.getContext != null => inner(other.getContext)
          case _ => None
        }
      }
      inner(elem)
    }
  }

  object traitMethod {
    def unapply(r: ScalaResolveResult): Option[(ScTrait, ScFunctionDefinition)] = {
      r.getElement match {
        case fun: ScFunctionDefinition =>
          fun.getContainingClassLoose match {
            case tr: ScTrait => Some(tr, fun)
            case _ => None
          }
        case _ => None
      }
    }
  }

  object privateThisField {
    def unapply(elem: PsiElement): Option[ScNamedElement] = {
      elem match {
        case c: ScClassParameter if c.isPrivateThis => Some(c)
        case (bp: ScBindingPattern) && ScalaPsiUtil.inNameContext(v @ (_: ScVariable | _: ScValue)) =>
          v match {
            case mo: ScModifierListOwner if mo.getModifierList.accessModifier.exists(am => am.isPrivate && am.isThis) => Some(bp)
            case _ => None
          }
        case _ => None
      }
    }
  }
}
