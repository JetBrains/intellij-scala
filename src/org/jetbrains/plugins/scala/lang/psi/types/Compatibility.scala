package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsParameterImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible.ImplicitResolveResult
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.processor.MostSpecificUtil

import scala.collection.Seq
import scala.collection.mutable.ArrayBuffer

/**
 * @author ven
 */
object Compatibility {
  @TestOnly
  var seqClass: Option[PsiClass] = None
  
  def compatibleWithViewApplicability(l : ScType, r : ScType): Boolean = r conforms l

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

    def getTypeAfterImplicitConversion(checkImplicits: Boolean, isShape: Boolean,
                                       expectedOption: Option[ScType]): (TypeResult[ScType], collection.Set[ImportUsed]) = {
      if (expr != null) {
        val expressionTypeResult = expr.getTypeAfterImplicitConversion(checkImplicits, isShape, expectedOption)
        (expressionTypeResult.tr, expressionTypeResult.importsUsed)
      } else {
        import scala.collection.Set

        def default: (Success[ScType], Set[ImportUsed]) = {
          (Success(typez, None), Set.empty)
        }

        if (isShape || !checkImplicits || place == null) return default

        def eval(place: PsiElement, data: (ScType, Option[ScType])): (Success[ScType], Set[ImportUsed]) = {
          expectedOption match {
            case Some(expected) if typez.conforms(expected) => (Success(typez, None), Set.empty)
            case Some(expected) =>
              val convertible = new ScImplicitlyConvertible(place, p => Some(typez))
              val firstPart = convertible.implicitMapFirstPart(Some(expected), fromUnder = false, exprType = Some(typez))
              var f: Seq[ImplicitResolveResult] =
                firstPart.filter(_.tp.conforms(expected))
              if (f.length == 0) {
                f = convertible.implicitMapSecondPart(Some(expected), fromUnder = false, exprType = Some(typez)).
                        filter(_.tp.conforms(expected))
              }
              if (f.length == 1) (Success(f(0).getTypeWithDependentSubstitutor, Some(place)), f(0).importUsed)
              else if (f.length == 0) (Success(typez, None), Set.empty)
              else {
                MostSpecificUtil(place, 1).mostSpecificForImplicit(f.toSet) match {
                  case Some(innerRes) => (Success(innerRes.getTypeWithDependentSubstitutor, Some(place)), innerRes.importUsed)
                  case None => (Success(typez, None), Set.empty)
                }
              }
            case _ => (Success(typez, None), Set.empty)
          }
        }

        CachesUtil.getMappedWithRecursionPreventingWithRollback(place, (typez, expectedOption),
          CachesUtil.TYPE_OF_SPECIAL_EXPR_AFTER_IMPLICIT_KEY, eval, default, PsiModificationTracker.MODIFICATION_COUNT)
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

  def seqClassFor(expr: ScTypedStmt): PsiClass = {
    seqClass match {
      case Some(clazz) =>
        if (ApplicationManager.getApplication.isUnitTestMode) clazz
        else throw new RuntimeException("Illegal state for seqClass variable")
      case _ =>
        ScalaPsiManager.instance(expr.getProject).getCachedClass("scala.collection.Seq",
          expr.getResolveScope, ScalaPsiManager.ClassCategory.TYPE)
    }
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

  def checkConformanceExt(checkNames: Boolean,
                          parameters: Seq[Parameter],
                          exprs: Seq[Expression],
                          checkWithImplicits: Boolean,
                          isShapesResolve: Boolean): ConformanceExtResult = {
    ProgressManager.checkCanceled()
    var undefSubst = new ScUndefinedSubstitutor

    val clashedAssignments = clashedAssignmentsIn(exprs)
        
    if(clashedAssignments.nonEmpty) {
      val problems = clashedAssignments.map(new ParameterSpecifiedMultipleTimes(_))
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
      val count = minParams - exprs.length
      val problems = new ArrayBuffer[ApplicabilityProblem]()
      parameters.reverseIterator.foreach { param =>
        if (!param.isRepeated && !param.isDefault && problems.length < count) {
          problems += new MissedValueParameter(param)
        }
      }
      return ConformanceExtResult(problems.toSeq, undefSubst)
    }

    if (parameters.length == 0) 
      return ConformanceExtResult(if(exprs.length == 0) Seq.empty else Seq(new ApplicabilityProblem("5")), undefSubst)
    
    var k = 0
    var namedMode = false //todo: optimization, when namedMode enabled, exprs.length <= parameters.length
    val used = new Array[Boolean](parameters.length)
    var problems: List[ApplicabilityProblem] = Nil
    var matched: List[(Parameter, ScExpression)] = Nil
    var matchedTypes: List[(Parameter, ScType)] = Nil
    var defaultParameterUsed = false

    while (k < parameters.length.min(exprs.length)) {
      val exprK = exprs(k)

      def doNoNamed(expr: Expression): List[ApplicabilityProblem] = {
        if (namedMode) {
          List(new PositionalAfterNamedArgument(expr.expr))
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
            {
              val conforms = Conformance.conforms(paramType, exprType, checkWeak = true)
              matched ::= (param, expr.expr)
              matchedTypes ::= (param, exprType)
              if (!conforms) {
                List(new TypeMismatch(expr.expr, paramType))
              } else {
                undefSubst += Conformance.undefinedSubst(paramType, exprType, checkWeak = true)
                List.empty
              }
            }
          }
        }
      }

      exprK match {
        case Expression(expr: ScTypedStmt) if expr.isSequenceArg =>
          val seqClass: PsiClass = seqClassFor(expr)
          if (seqClass != null) {
            val getIt = used.indexOf(false)
            used(getIt) = true
            val param: Parameter = parameters(getIt)
            val paramType = param.paramType

            if (!param.isRepeated)
              problems ::= new ExpansionForNonRepeatedParameter(expr)

            val tp = ScParameterizedType(ScType.designator(seqClass), Seq(paramType))

            val expectedType = ScParameterizedType(ScType.designator(seqClass), Seq(param.expectedType))

            for (exprType <- expr.getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(expectedType)).tr) yield {
              val conforms = Conformance.conforms(tp, exprType, checkWeak = true)
              if (!conforms) {
                return ConformanceExtResult(Seq(new TypeMismatch(expr, tp)), undefSubst, defaultParameterUsed, matched, matchedTypes)
              } else {
                matched ::= (param, expr)
                matchedTypes ::= (param, exprType)
                undefSubst += Conformance.undefinedSubst(tp, exprType, checkWeak = true)
              }
            }
          } else {
            problems :::= doNoNamed(Expression(expr)).reverse
          }
        case Expression(assign@NamedAssignStmt(name)) =>
          val ind = parameters.indexWhere { p =>
            ScalaPsiUtil.memberNamesEquals(p.name, name) ||
              p.deprecatedName.exists(ScalaPsiUtil.memberNamesEquals(_, name))
          }
          if (ind == -1 || used(ind)) {
            def extractExpression(assign: ScAssignStmt): ScExpression = {
              if (ScUnderScoreSectionUtil.isUnderscoreFunction(assign)) assign
              else assign.getRExpression.getOrElse(assign)
            }
            problems :::= doNoNamed(Expression(extractExpression(assign))).reverse
          } else {
            if (!checkNames)
              return ConformanceExtResult(Seq(new ApplicabilityProblem("9")), undefSubst, defaultParameterUsed, matched, matchedTypes)
            used(ind) = true
            val param: Parameter = parameters(ind)
            if (ind != k) {
              namedMode = true
            }
            assign.getRExpression match {
              case Some(expr: ScExpression) =>
                val paramType = param.paramType
                val expectedType = param.expectedType
                for (exprType <- expr.getTypeAfterImplicitConversion(checkWithImplicits, isShapesResolve, Some(expectedType)).tr) {
                  val conforms = Conformance.conforms(paramType, exprType, checkWeak = true)
                  if (!conforms) {
                    problems ::= TypeMismatch(expr, paramType)
                  } else {
                    matched ::= (param, expr)
                    matchedTypes ::= (param, exprType)
                    undefSubst += Conformance.undefinedSubst(paramType, exprType, checkWeak = true)
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
          val conforms = Conformance.conforms(paramType, exprType, checkWeak = true)
          if (!conforms) {
            return ConformanceExtResult(Seq(new ElementApplicabilityProblem(exprs(k).expr, exprType, paramType)), undefSubst, defaultParameterUsed, matched, matchedTypes)
          } else {
            matched ::= (parameters.last, exprs(k).expr)
            matchedTypes ::= (parameters.last, exprType)
            undefSubst += Conformance.undefinedSubst(paramType, exprType, checkWeak = true)
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
      if(missed.nonEmpty) return ConformanceExtResult(missed, undefSubst, defaultParameterUsed, matched, matchedTypes)
    }
    ConformanceExtResult(Seq.empty, undefSubst, defaultParameterUsed, matched, matchedTypes)
  }

  def toParameter(p: ScParameter, substitutor: ScSubstitutor) = {
    val t = substitutor.subst(p.getType(TypingContext.empty).getOrNothing)
    new Parameter(p.name, p.deprecatedName, t, t, p.isDefaultParam, p.isRepeatedParameter, p.isCallByNameParameter, p.index, Some(p))
  }

  def toParameter(p: PsiParameter) = {
    val tp = p.paramType
    new Parameter(if (p.isInstanceOf[ClsParameterImpl]) "" else p.name, None, tp, tp, false, p.isVarArgs, false, p.index, p match {
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
          val problems = clashedAssignments.map(new ParameterSpecifiedMultipleTimes(_))
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
        if (shortage > 0)
          return ConformanceExtResult(obligatory.takeRight(shortage).
                  map(p => MissedValueParameter(toParameter(p, substitutor))))

        val res = checkConformanceExt(checkNames = true, parameters = parameters.map(toParameter(_, substitutor)),
          exprs = exprs, checkWithImplicits = checkWithImplicits, isShapesResolve = isShapesResolve)

        res
      case constructor: ScPrimaryConstructor =>
        val parameters: Seq[ScParameter] = constructor.effectiveFirstParameterSection

        val clashedAssignments = clashedAssignmentsIn(exprs)

        if(clashedAssignments.nonEmpty) {
          val problems = clashedAssignments.map(new ParameterSpecifiedMultipleTimes(_))
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
            new Parameter(p.name, p.deprecatedName, t, t, p.isDefaultParam, p.isRepeatedParameter,
              p.isCallByNameParameter, p.index, Some(p))
          }
          return ConformanceExtResult(part.map(new MissedValueParameter(_)))
        }

        val res = checkConformanceExt(checkNames = true, parameters = parameters.map {
          param: ScParameter => {
            val paramType: ScType = substitutor.subst(param.getType(TypingContext.empty).getOrNothing)
            new Parameter(param.name, param.deprecatedName,
              paramType, paramType,
              param.isDefaultParam, param.isRepeatedParameter, param.isRepeatedParameter, param.index, Some(param))
          }
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
          case param: PsiParameter => new Parameter("", None, substitutor.subst(param.exactParamType()),
            false, param.isVarArgs, false, -1)
        }, exprs = exprs, checkWithImplicits = checkWithImplicits, isShapesResolve = isShapesResolve)
      case _ => ConformanceExtResult(Seq(new ApplicabilityProblem("22")))
    }
  }
}
