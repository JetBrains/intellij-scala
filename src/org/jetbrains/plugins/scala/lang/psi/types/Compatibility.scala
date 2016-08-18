package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsParameterImpl
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTrait
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.implicits.ImplicitCollector
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.macroAnnotations.{CachedMappedWithRecursionGuard, ModCount}

import scala.collection.mutable.ArrayBuffer
import scala.collection.{Seq, Set}

/**
 * @author ven
 */
object Compatibility {
  @TestOnly
  var seqClass: Option[PsiClass] = None

  private implicit val typeSystem = ScalaTypeSystem

  case class Expression(expr: ScExpression) {
    var typez: ScType = null
    var place: PsiElement = null
    def this(tp: ScType) = {
      this(null: ScExpression)
      typez = tp
    }
    def this(tp: ScType, place: PsiElement) {
      this(tp)
      this.place = place
    }


    @CachedMappedWithRecursionGuard(place, (Success(typez, None), Set.empty), ModCount.getBlockModificationCount)
    private def eval(typez: ScType, expectedOption: Option[ScType]): (TypeResult[ScType], Set[ImportUsed]) = {
      expectedOption match {
        case Some(expected) if typez.conforms(expected) => (Success(typez, None), Set.empty)
        case Some(expected) =>
          val defaultResult: (TypeResult[ScType], Set[ImportUsed]) = (Success(typez, None), Set.empty)
          val functionType = FunctionType(expected, Seq(typez))(place.getProject, place.getResolveScope)
          val results = new ImplicitCollector(place, functionType, functionType, None,
            isImplicitConversion = true, isExtensionConversion = false).collect()
          if (results.length == 1) {
            val res = results.head
            val paramType = InferUtil.extractImplicitParameterType(res)
            paramType match {
              case FunctionType(rt, Seq(_)) => (Success(rt, Some(place)), res.importsUsed)
              case _ =>
                ScalaPsiManager.instance(place.getProject)
                  .getCachedClass("scala.Function1", place.getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
                  .collect {
                    case function1: ScTrait =>
                      ScParameterizedType(ScalaType.designator(function1),
                        function1.typeParameters.map(tp => UndefinedType(TypeParameterType(tp), 1)))
                  } match {
                  case Some(funTp: ScParameterizedType) =>
                    val secondArg = funTp.typeArguments(1)
                    paramType.conforms(funTp, new ScUndefinedSubstitutor())._2.getSubstitutor match {
                      case Some(subst) =>
                        val rt = subst.subst(secondArg)
                        if (rt.isInstanceOf[UndefinedType]) defaultResult
                        else {
                          (Success(rt, Some(place)), res.importsUsed)
                        }
                      case None => defaultResult
                    }
                  case _ => defaultResult
                }
            }
          } else defaultResult
        case _ => (Success(typez, None), Set.empty)
      }
    }

    def getTypeAfterImplicitConversion(checkImplicits: Boolean, isShape: Boolean,
                                       expectedOption: Option[ScType]): (TypeResult[ScType], collection.Set[ImportUsed]) = {
      if (expr != null) {
        val expressionTypeResult = expr.getTypeAfterImplicitConversion(checkImplicits, isShape, expectedOption)
        (expressionTypeResult.tr, expressionTypeResult.importsUsed)
      } else {
        import scala.collection.Set

        def default: (Success[ScType], Set[ImportUsed]) = (Success(typez, None), Set.empty)

        if (isShape || !checkImplicits || place == null) default
        else eval(typez, expectedOption)
      }
    }
  }

  object Expression {
    import scala.language.implicitConversions

    implicit def scExpression2Expression(expr: ScExpression): Expression = Expression(expr)
    implicit def seq2ExpressionSeq(seq: Seq[ScExpression]): Seq[Expression] = seq.map(Expression(_))
    implicit def args2ExpressionArgs(list: List[Seq[ScExpression]]): List[Seq[Expression]] = {
      list.map(_.map(Expression(_)))
    }
  }

  def seqClassFor(expr: ScTypedStmt): Option[PsiClass] = seqClass match {
    case result@Some(_) if ApplicationManager.getApplication.isUnitTestMode => result
    case _@Some(_) => throw new RuntimeException("Illegal state for seqClass variable")
    case _ => ScalaPsiManager.instance(expr.getProject)
      .getCachedClass("scala.collection.Seq", expr.getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
  }

  def checkConformance(checkNames: Boolean,
                       parameters: Seq[Parameter],
                       exprs: Seq[Expression],
                       checkWithImplicits: Boolean): (Boolean, ScUndefinedSubstitutor) = {
    val r = checkConformanceExt(checkNames, parameters, exprs, checkWithImplicits, isShapesResolve = false)
    (r.problems.isEmpty, r.undefSubst)
  }

  def clashedAssignmentsIn(exprs: Seq[Expression]): Seq[ScAssignStmt] = {
    val pairs = for(Expression(assignment @ NamedAssignStmt(name)) <- exprs) yield (name, assignment)
    val names = pairs.unzip._1
    val clashedNames = names.diff(names.distinct)
    pairs.filter(p => clashedNames.contains(p._1)).map(_._2)
  }

  case class ConformanceExtResult(problems: Seq[ApplicabilityProblem],
                                  undefSubst: ScUndefinedSubstitutor = new ScUndefinedSubstitutor,
                                  defaultParameterUsed: Boolean = false,
                                  matchedArgs: Seq[(Parameter, ScExpression)] = Seq(),
                                  matchedTypes: Seq[(Parameter, ScType)] = Seq())

  def collectSimpleProblems(exprs: Seq[Expression], parameters: Seq[Parameter]): Seq[ApplicabilityProblem] = {
    val problems = new ArrayBuffer[ApplicabilityProblem]()
    exprs.foldLeft(parameters) { (parameters, expression) =>
      if (expression.expr == null) parameters.tail
      else expression.expr match {
        case a: ScAssignStmt if a.assignName.nonEmpty =>
          parameters.find(_.name == a.assignName.get) match {
            case Some(parameter) =>
              parameters.filter(_ ne parameter)
            case None => parameters.tail
          }
        case _ => parameters.tail
      }
    }.foreach { param =>
      if (!param.isRepeated && !param.isDefault) {
        problems += MissedValueParameter(param)
      }
    }
    problems
  }

  def checkConformanceExt(checkNames: Boolean,
                          parameters: Seq[Parameter],
                          exprs: Seq[Expression],
                          checkWithImplicits: Boolean,
                          isShapesResolve: Boolean): ConformanceExtResult = {
    ProgressManager.checkCanceled()
    var undefSubst = new ScUndefinedSubstitutor

    val clashedAssignments = clashedAssignmentsIn(exprs)

    if(clashedAssignments.nonEmpty) {
      val problems = clashedAssignments.map(ParameterSpecifiedMultipleTimes(_))
      return ConformanceExtResult(problems)
    }

    //optimization:
    val hasRepeated = parameters.exists(_.isRepeated)
    val maxParams: Int = if(hasRepeated) scala.Int.MaxValue else parameters.length

    val excess = exprs.length - maxParams

    if (excess > 0) {
      val arguments = exprs.takeRight(excess).map(_.expr)
      return ConformanceExtResult(arguments.map(ExcessArgument), undefSubst)
    }

    val minParams = parameters.count(p => !p.isDefault && !p.isRepeated)
    if (exprs.length < minParams) {
      return ConformanceExtResult(collectSimpleProblems(exprs, parameters), undefSubst)
    }

    if (parameters.isEmpty)
      return ConformanceExtResult(if(exprs.isEmpty) Seq.empty else Seq(new ApplicabilityProblem("5")), undefSubst)

    var k = 0
    var namedMode = false //todo: optimization, when namedMode enabled, exprs.length <= parameters.length
    val used = new Array[Boolean](parameters.length)
    var problems: List[ApplicabilityProblem] = Nil
    var matched: List[(Parameter, ScExpression)] = Nil
    var matchedTypes: List[(Parameter, ScType)] = Nil
    var defaultParameterUsed = false

    def doNoNamed(expr: Expression): List[ApplicabilityProblem] = {
      if (namedMode) {
        List(PositionalAfterNamedArgument(expr.expr))
      }
      else {
        val getIt = used.indexOf(false)
        used(getIt) = true
        val param: Parameter = parameters(getIt)
        val paramType = param.paramType
        val expectedType = param.expectedType
        val typeResult =
          expr.getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(expectedType))._1
        typeResult.toOption.toList.flatMap { exprType =>
          val conforms = exprType.weakConforms(paramType)
          matched ::=(param, expr.expr)
          matchedTypes ::=(param, exprType)
          if (!conforms) {
            List(TypeMismatch(expr.expr, paramType))
          } else {
            undefSubst += exprType.conforms(paramType, new ScUndefinedSubstitutor(), checkWeak = true)._2
            List.empty
          }
        }
      }
    }

    while (k < parameters.length.min(exprs.length)) {
      exprs(k) match {
        case Expression(expr: ScTypedStmt) if expr.isSequenceArg =>
          seqClassFor(expr) match {
            case Some(seq) =>
              val getIt = used.indexOf(false)
              used(getIt) = true
              val param: Parameter = parameters(getIt)

              if (!param.isRepeated)
                problems ::= ExpansionForNonRepeatedParameter(expr)

              val tp = ScParameterizedType(ScalaType.designator(seq), Seq(param.paramType))
              val expectedType = ScParameterizedType(ScalaType.designator(seq), Seq(param.expectedType))

              for (exprType <- expr.getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(expectedType)).tr) yield {
                val conforms = exprType.weakConforms(tp)
                if (!conforms) {
                  return ConformanceExtResult(Seq(TypeMismatch(expr, tp)), undefSubst, defaultParameterUsed, matched, matchedTypes)
                } else {
                  matched ::= (param, expr)
                  matchedTypes ::= (param, exprType)
                  undefSubst += exprType.conforms(tp, new ScUndefinedSubstitutor(), checkWeak = true)._2
                }
              }
            case _ =>
              problems :::= doNoNamed(Expression(expr)).reverse
          }
        case Expression(assign@NamedAssignStmt(name)) =>
          val index = parameters.indexWhere { p =>
            ScalaNamesUtil.equivalent(p.name, name) ||
              p.deprecatedName.exists(ScalaNamesUtil.equivalent(_, name))
          }
          if (index == -1 || used(index)) {
            def extractExpression(assign: ScAssignStmt): ScExpression = {
              if (ScUnderScoreSectionUtil.isUnderscoreFunction(assign)) assign
              else assign.getRExpression.getOrElse(assign)
            }
            problems :::= doNoNamed(Expression(extractExpression(assign))).reverse
          } else {
            if (!checkNames)
              return ConformanceExtResult(Seq(new ApplicabilityProblem("9")), undefSubst, defaultParameterUsed, matched, matchedTypes)
            used(index) = true
            val param: Parameter = parameters(index)
            if (index != k) {
              namedMode = true
            }

            assign.getRExpression match {
              case Some(expr: ScExpression) =>
                val (paramType, expectedType) = expr match  {
                  case typedStmt: ScTypedStmt if typedStmt.isSequenceArg =>
                    seqClassFor(typedStmt) match {
                      case Some(seq) =>
                        if (!param.isRepeated)
                          problems ::= ExpansionForNonRepeatedParameter(expr)

                        (ScParameterizedType(ScalaType.designator(seq), Seq(param.paramType)),
                          ScParameterizedType(ScalaType.designator(seq), Seq(param.expectedType)))
                      case _ =>
                        (param.paramType, param.expectedType)
                    }
                  case _ => (param.paramType, param.expectedType)
                }

                for (exprType <- expr.getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(expectedType)).tr) yield {
                  val conforms = exprType.weakConforms(paramType)
                  if (!conforms) {
                    problems ::= TypeMismatch(expr, paramType)
                  } else {
                    matched ::= (param, expr)
                    matchedTypes ::= (param, exprType)
                    undefSubst += exprType.conforms(paramType, new ScUndefinedSubstitutor(), checkWeak = true)._2
                  }
                }
              case _ =>
                return ConformanceExtResult(Seq(new ApplicabilityProblem("11")), undefSubst, defaultParameterUsed, matched, matchedTypes)
            }
          }
        case expr: Expression =>
          problems :::= doNoNamed(expr).reverse
      }
      k = k + 1
    }

    if(problems.nonEmpty) return ConformanceExtResult(problems.reverse, undefSubst, defaultParameterUsed, matched, matchedTypes)

    if (exprs.length == parameters.length) return ConformanceExtResult(Seq.empty, undefSubst, defaultParameterUsed, matched, matchedTypes)
    else if (exprs.length > parameters.length) {
      if (namedMode)
        return ConformanceExtResult(Seq(new ApplicabilityProblem("12")), undefSubst, defaultParameterUsed, matched, matchedTypes)
      if (!parameters.last.isRepeated)
        return ConformanceExtResult(Seq(new ApplicabilityProblem("13")), undefSubst, defaultParameterUsed, matched, matchedTypes)
      val paramType: ScType = parameters.last.paramType
      val expectedType: ScType = parameters.last.expectedType
      while (k < exprs.length) {
        for (exprType <- exprs(k).getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(expectedType))._1) {
          val conforms = exprType.weakConforms(paramType)
          if (!conforms) {
            return ConformanceExtResult(Seq(ElementApplicabilityProblem(exprs(k).expr, exprType, paramType)),
              undefSubst, defaultParameterUsed, matched, matchedTypes)
          } else {
            matched ::= (parameters.last, exprs(k).expr)
            matchedTypes ::= (parameters.last, exprType)
            undefSubst += exprType.conforms(paramType, new ScUndefinedSubstitutor(), checkWeak = true)._2
          }
        }
        k = k + 1
      }
    }
    else {
      if (exprs.length == parameters.length - 1 && !namedMode && parameters.last.isRepeated)
        return ConformanceExtResult(Seq.empty, undefSubst, defaultParameterUsed, matched, matchedTypes)

      val missed = for ((parameter: Parameter, b) <- parameters.zip(used)
                        if !b && !parameter.isDefault) yield MissedValueParameter(parameter)
      defaultParameterUsed = parameters.zip(used).exists { case (param, bool) => !bool && param.isDefault}
      if (missed.nonEmpty) return ConformanceExtResult(missed, undefSubst, defaultParameterUsed, matched, matchedTypes)
      else {
        // inspect types default values
        val pack = parameters.zip(used)
        for ((param, use) <- pack if param.isDefault && !use) {
          val paramType: ScType = param.paramType
          val defaultExpr = param.paramInCode.flatMap(_.getDefaultExpression)
          param.defaultType match {
            case Some(defaultTp) if defaultTp.conforms(paramType) =>
              defaultExpr.foreach(expr => matched ::= (param, expr))
              matchedTypes ::=(param, defaultTp)
              undefSubst += defaultTp.conforms(paramType, new ScUndefinedSubstitutor())._2
            case Some(defaultTp) =>
                return ConformanceExtResult(Seq(DefaultTypeParameterMismatch(defaultTp, paramType)), undefSubst,
                  defaultParameterUsed = true, matched, matchedTypes)
            case _ =>
          }
        }
      }
    }
    ConformanceExtResult(Seq.empty, undefSubst, defaultParameterUsed, matched, matchedTypes)
  }

  def toParameter(p: ScParameter, substitutor: ScSubstitutor): Parameter = {
    val t = substitutor.subst(p.getType(TypingContext.empty).getOrNothing)
    val default = p.getDefaultExpression.flatMap(_.getType().toOption.map(substitutor.subst))
    Parameter(p.name, p.deprecatedName, t, t, p.isDefaultParam, p.isRepeatedParameter, p.isCallByNameParameter,
      p.index, Some(p), default)
  }

  def toParameter(p: PsiParameter): Parameter = {
    val tp = p.paramType(exact = false)
    Parameter(if (p.isInstanceOf[ClsParameterImpl]) "" else p.name, None, tp, tp, false, p.isVarArgs, false, p.index,
      p match {
        case param: ScParameter => Some(param)
        case _ => None
      })
  }

  // TODO refactor a lot of duplication out of this method
  def compatible(named: PsiNamedElement,
                 substitutor: ScSubstitutor,
                 argClauses: List[Seq[Expression]],
                 checkWithImplicits: Boolean,
                 scope: GlobalSearchScope,
                 isShapesResolve: Boolean): ConformanceExtResult = {
    val exprs: Seq[Expression] = argClauses.headOption match {case Some(seq) => seq case _ => Seq.empty}
    named match {
      case synthetic: ScSyntheticFunction =>
        if (synthetic.paramClauses.isEmpty)
          return ConformanceExtResult(Seq(new DoesNotTakeParameters))

        checkConformanceExt(checkNames = false, parameters = synthetic.paramClauses.head.map { p =>
          p.copy(paramType = substitutor.subst(p.paramType))
        }, exprs = exprs, checkWithImplicits = checkWithImplicits, isShapesResolve = isShapesResolve)
      case fun: ScFunction =>
        if(!fun.hasParameterClause && argClauses.nonEmpty)
          return ConformanceExtResult(Seq(new DoesNotTakeParameters))

        val parameters: Seq[ScParameter] = fun.effectiveParameterClauses.headOption.toList.flatMap(_.effectiveParameters)

        val clashedAssignments = clashedAssignmentsIn(exprs)

        if(clashedAssignments.nonEmpty) {
          val problems = clashedAssignments.map(ParameterSpecifiedMultipleTimes(_))
          return ConformanceExtResult(problems)
        }

        //optimization:
        val hasRepeated = parameters.exists(_.isRepeatedParameter)
        val maxParams: Int = if(hasRepeated) scala.Int.MaxValue else parameters.length

        val excess = exprs.length - maxParams

        if (excess > 0) {
          val arguments = exprs.takeRight(excess).map(_.expr)
          return ConformanceExtResult(arguments.map(ExcessArgument))
        }

        val obligatory = parameters.filter(p => !p.isDefaultParam && !p.isRepeatedParameter)
        val shortage = obligatory.size - exprs.length
        val params = parameters.map(toParameter(_, substitutor))
        if (shortage > 0) {
          return ConformanceExtResult(collectSimpleProblems(exprs, params))
        }

        val res = checkConformanceExt(checkNames = true, parameters = params,
          exprs = exprs, checkWithImplicits = checkWithImplicits, isShapesResolve = isShapesResolve)
        res
      case constructor: ScPrimaryConstructor =>
        val parameters: Seq[ScParameter] = constructor.effectiveFirstParameterSection

        val clashedAssignments = clashedAssignmentsIn(exprs)

        if(clashedAssignments.nonEmpty) {
          val problems = clashedAssignments.map(ParameterSpecifiedMultipleTimes(_))
          return ConformanceExtResult(problems)
        }

        //optimization:
        val hasRepeated = parameters.exists(_.isRepeatedParameter)
        val maxParams: Int = if(hasRepeated) scala.Int.MaxValue else parameters.length

        val excess = exprs.length - maxParams

        if (excess > 0) {
          val part = exprs.takeRight(excess).map(_.expr)
          return ConformanceExtResult(part.map(ExcessArgument))
        }

        val obligatory = parameters.filter(p => !p.isDefaultParam && !p.isRepeatedParameter)
        val shortage = obligatory.size - exprs.length

        if (shortage > 0) {
          val part = obligatory.takeRight(shortage).map { p =>
            val t = p.getType(TypingContext.empty).getOrAny
            Parameter(p.name, p.deprecatedName, t, t, p.isDefaultParam, p.isRepeatedParameter,
              p.isCallByNameParameter, p.index, Some(p), p.getDefaultExpression.flatMap(_.getType().toOption))
          }
          return ConformanceExtResult(part.map(MissedValueParameter(_)))
        }

        val res = checkConformanceExt(checkNames = true, parameters = parameters.map {
          param: ScParameter => toParameter(param, substitutor)
        }, exprs = exprs, checkWithImplicits = checkWithImplicits, isShapesResolve = isShapesResolve)
        res

      case method: PsiMethod =>
        val parameters: Seq[PsiParameter] = method.getParameterList.getParameters.toSeq

        val excess = exprs.length - parameters.length

        //optimization:
        val hasRepeated = parameters.exists(_.isVarArgs)
        if (excess > 0 && !hasRepeated) {
          val arguments = exprs.takeRight(excess).map(_.expr)
          return ConformanceExtResult(arguments.map(ExcessArgument))
        }

        val obligatory = parameters.filterNot(_.isVarArgs)
        val shortage = obligatory.size - exprs.length
        if (shortage > 0)
          return ConformanceExtResult(obligatory.takeRight(shortage).map(p => MissedValueParameter(toParameter(p))))

        checkConformanceExt(checkNames = false, parameters = parameters.map {
          case param: PsiParameter => new Parameter("", None, substitutor.subst(param.paramType()),
            false, param.isVarArgs, false, -1)
        }, exprs = exprs, checkWithImplicits = checkWithImplicits, isShapesResolve = isShapesResolve)

      case _ => ConformanceExtResult(Seq(new ApplicabilityProblem("22")))
    }
  }
}
