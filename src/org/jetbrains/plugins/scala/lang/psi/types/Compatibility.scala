package org.jetbrains.plugins.scala
package lang
package psi
package types

import api.statements.params.{ScParameters, ScParameter}
import api.statements.{ScFun, ScFunction}
import psi.impl.toplevel.synthetic.ScSyntheticFunction
import api.expr._
import api.toplevel.typedef.ScClass
import api.base.types.ScSequenceArg
import com.intellij.psi._
import api.base.ScPrimaryConstructor
import impl.compiled.ClsParameterImpl
import result.{TypeResult, Success, TypingContext}
import api.toplevel.imports.usages.ImportUsed
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import com.intellij.openapi.progress.ProgressManager
import search.GlobalSearchScope
import collection.Seq

/**
 * @author ven
 */
object Compatibility {
  private var seqClass: Option[PsiClass] = None  
  
  def compatibleWithViewApplicability(l : ScType, r : ScType): Boolean = {
    if (r conforms l) {
      true
    }
    else {
      false //todo check view applicability
    }
  }

  case class Expression(expr: ScExpression) {
    var typez: ScType = null
    def this(tp: ScType) = {
      this(null: ScExpression)
      typez = tp
    }
    def getTypeAfterImplicitConversion(expected: Option[ScType], checkImplicits: Boolean): (TypeResult[ScType], collection.Set[ImportUsed]) = {
      if (expr != null) {
        val expressionTypeResult = expr.getTypeAfterImplicitConversion(Some(expected), checkImplicits)
        (expressionTypeResult.tr, expressionTypeResult.importsUsed)
      }
      else (Success(typez, None), Set.empty)
    }
  }

  object Expression {
    implicit def scExpression2Expression(expr: ScExpression): Expression = Expression(expr)
    implicit def seq2ExpressionSeq(seq: Seq[ScExpression]): Seq[Expression] = seq.map(Expression(_))
    implicit def args2ExpressionArgs(list: List[Seq[ScExpression]]): List[Seq[Expression]] = {
      list.map(_.map(Expression(_)))
    }
  }

  def seqClassFor(expr: ScTypedStmt): PsiClass = {
    seqClass.getOrElse {
      JavaPsiFacade.getInstance(expr.getProject).findClass("scala.collection.Seq", expr.getResolveScope)
    }
  }

  // provides means for dependency injection in tests
  def mockSeqClass(aClass: PsiClass) {
    seqClass = Some(aClass)
  }
  
  def checkConformance(checkNames: Boolean,
                                 parameters: Seq[Parameter],
                                 exprs: Seq[Expression],
                                 checkWithImplicits: Boolean,
                                 checkWeakConformance: Boolean = false): (Boolean, ScUndefinedSubstitutor) = {
    val r = checkConformanceExt(checkNames, parameters, exprs, checkWithImplicits, checkWeakConformance)
    (r._1.isEmpty, r._2)
  }  
  
  def clashedAssignmentsIn(exprs: Seq[Expression]): Seq[ScAssignStmt] = {
    val pairs = for(Expression(assignment @ NamedAssignStmt(name)) <- exprs) yield (name, assignment)
    val names = pairs.unzip._1
    val clashedNames = names.diff(names.distinct)
    pairs.filter(p => clashedNames.contains(p._1)).map(_._2)    
  }
  
  def checkConformanceExt(checkNames: Boolean,
                               parameters: Seq[Parameter],
                               exprs: Seq[Expression],
                               checkWithImplicits: Boolean,
                               checkWeakConformance: Boolean = false): (Seq[ApplicabilityProblem], ScUndefinedSubstitutor) = {
    ProgressManager.checkCanceled
    var undefSubst = new ScUndefinedSubstitutor

    val clashedAssignments = clashedAssignmentsIn(exprs)
    val unresolved = for(Expression(assignment @ NamedAssignStmt(name)) <- exprs;
                         if !parameters.exists(_.name == name)) yield assignment
        
    if(!unresolved.isEmpty || !clashedAssignments.isEmpty) {
      val problems = unresolved.map(new UnresolvedParameter(_)) ++
              clashedAssignments.map(new ParameterSpecifiedMultipleTimes(_))
      return (problems, new ScUndefinedSubstitutor) 
    }
    
    //optimization:
    val hasRepeated = parameters.exists(_.isRepeated)
    val maxParams = if(hasRepeated) scala.Int.MaxValue else parameters.length
        
    val excess = exprs.length - maxParams
        
    if (excess > 0) {
      val arguments = exprs.takeRight(excess).map(_.expr)
      return (arguments.map(ExcessArgument(_)), undefSubst)
    }
    
    val minParams = parameters.count(p => !p.isDefault && !p.isRepeated)
    if (exprs.length < minParams) 
      return (Seq(new ApplicabilityProblem("4")), undefSubst)

    if (parameters.length == 0) 
      return (if(exprs.length == 0) Seq.empty else Seq(new ApplicabilityProblem("5")), undefSubst)
    
    var k = 0
    var namedMode = false //todo: optimization, when namedMode enabled, exprs.length <= parameters.length
    val used = new Array[Boolean](parameters.length)
    var problems: List[ApplicabilityProblem] = Nil
    
    while (k < parameters.length.min(exprs.length)) {
      def doNoNamed(expr: Expression): List[ApplicabilityProblem] = {
        if (namedMode) {
          return List(new PositionalAfterNamedArgument(expr.expr))
        }
        else {
          val getIt = used.indexOf(false)
          used(getIt) = true
          val param: Parameter = parameters(getIt)
          val paramType = param.paramType
          val typeResult = expr.getTypeAfterImplicitConversion(Some(paramType), checkWithImplicits)._1
          typeResult.toOption.toList.flatMap { exprType =>
            if (!Conformance.conforms(paramType, exprType, checkWeakConformance)) {
              List(new TypeMismatch(expr.expr, paramType))
            } else {
              undefSubst += Conformance.undefinedSubst(paramType, exprType, checkWeakConformance)
              List.empty
            }
          }
        }
      }

      exprs(k) match {
        case Expression(expr: ScTypedStmt) if expr.getLastChild.isInstanceOf[ScSequenceArg] => {
          val seqClass: PsiClass = seqClassFor(expr)
          if (seqClass != null) {
            val getIt = used.indexOf(false)
            used(getIt) = true
            val param: Parameter = parameters(getIt)
            val paramType = param.paramType
            
            if (!param.isRepeated) 
              problems ::= new ExpansionForNonRepeatedParameter(expr)
            
            val tp = ScParameterizedType(ScDesignatorType(seqClass), Seq(paramType))
            
            for (exprType <- expr.getTypeAfterImplicitConversion(Some(Some(tp)), checkWithImplicits).tr) yield {
              if (!Conformance.conforms(tp, exprType, checkWeakConformance)) 
                return (Seq(new TypeMismatch(expr, tp)), undefSubst)
              else {
                undefSubst += Conformance.undefinedSubst(tp, exprType, checkWeakConformance)
              }
            }
          } else {
            problems :::= doNoNamed(Expression(expr)).reverse 
          }
        }
        case Expression(assign@NamedAssignStmt(name)) => {
          val ind = parameters.findIndexOf(_.name == name)
          if (ind == -1 || used(ind) == true) {
            problems :::= doNoNamed(Expression(assign)).reverse
          }
          else {
            if (!checkNames) return (Seq(new ApplicabilityProblem("9")), undefSubst)
            namedMode = true
            used(ind) = true
            val param: Parameter = parameters(ind)
            assign.getRExpression match {
              case Some(expr: ScExpression) => {
                val paramType = param.paramType
                for (exprType <- expr.getTypeAfterImplicitConversion(Some(Some(paramType)), checkWithImplicits).tr)
                
                if (!Conformance.conforms(paramType, exprType, checkWeakConformance)) 
                  problems ::= TypeMismatch(expr, paramType)
                else 
                  undefSubst += Conformance.undefinedSubst(paramType, exprType, checkWeakConformance)
              }
              case _ => return (Seq(new ApplicabilityProblem("11")), undefSubst)
            }
          }
        }
        case expr: Expression => {
          problems :::= doNoNamed(expr).reverse
        }
      }
      k = k + 1
    }
    
    if(!problems.isEmpty) return (problems.reverse, undefSubst) 
    
    if (exprs.length == parameters.length) return (Seq.empty, undefSubst)
    else if (exprs.length > parameters.length) {
      if (namedMode) return (Seq(new ApplicabilityProblem("12")), undefSubst)
      if (!parameters.last.isRepeated) return (Seq(new ApplicabilityProblem("13")), undefSubst)
      val paramType: ScType = parameters.last.paramType
      while (k < exprs.length) {
        for (exprType <- exprs(k).getTypeAfterImplicitConversion(Some(paramType), checkWithImplicits)._1) {
          if (!Conformance.conforms(paramType, exprType, checkWeakConformance)) 
            return (Seq(new ApplicabilityProblem("14")), undefSubst)
          else 
            undefSubst = Conformance.undefinedSubst(paramType, exprType, checkWeakConformance)
        }
        k = k + 1
      }
    }
    else {
      if (exprs.length == parameters.length - 1 && !namedMode && parameters.last.isRepeated) 
        return (Seq.empty, undefSubst)
      
      val missed = for ((parameter: Parameter, b) <- parameters.zip(used);
                        if (!b && !parameter.isDefault)) yield MissedValueParameter(parameter) 
      
      if(!missed.isEmpty) return (missed, undefSubst) 
    }
    return (Seq.empty, undefSubst)
  }

  def toParameter(p: ScParameter, substitutor: ScSubstitutor) = {
    val t = substitutor.subst(p.getType(TypingContext.empty).getOrElse(Nothing))
    Parameter(p.getName, t, p.isDefaultParam, p.isRepeatedParameter)
  }

  // TODO refactor a lot of duplication out of this method 
  def compatible(named: PsiNamedElement, 
                 substitutor: ScSubstitutor,
                 argClauses: List[Seq[Expression]],
                 checkWithImplicits: Boolean,
                 scope: GlobalSearchScope, 
                 checkWeakConformance: Boolean = false): (Seq[ApplicabilityProblem], ScUndefinedSubstitutor) = {
    val exprs: Seq[Expression] = argClauses.headOption match {case Some(seq) => seq case _ => Seq.empty}
    named match {
      case synthetic: ScSyntheticFunction => {
        checkConformanceExt(false, synthetic.paramTypes.map {tp: ScType => Parameter("", substitutor.subst(tp), false,
          false)}, exprs, checkWithImplicits, checkWeakConformance)
      }
      case fun: ScFunction => {
        
        if(fun.isProcedure && !argClauses.isEmpty)
          return (Seq(new DoesNotTakeParameters), new ScUndefinedSubstitutor) 
                  
        val parameters: Seq[ScParameter] = fun.paramClauses.clauses.firstOption.toList.flatMap(_.parameters) 
        
        val clashedAssignments = clashedAssignmentsIn(exprs)
        val unresolved = for(Expression(assignment @ NamedAssignStmt(name)) <- exprs;
                             if !parameters.exists(_.name == name)) yield assignment
        
        if(!unresolved.isEmpty || !clashedAssignments.isEmpty) {
          val problems = unresolved.map(new UnresolvedParameter(_)) ++
                  clashedAssignments.map(new ParameterSpecifiedMultipleTimes(_))
          return (problems, new ScUndefinedSubstitutor) 
        }
        
        //optimization:
        val hasRepeated = parameters.exists(_.isRepeatedParameter)
        val maxParams = if(hasRepeated) scala.Int.MaxValue else parameters.length
        
        val excess = exprs.length - maxParams
        
        if (excess > 0) {
          val arguments = exprs.takeRight(excess).map(_.expr)
          return (arguments.map(ExcessArgument(_)), new ScUndefinedSubstitutor)
        }
        
        val obligatory = parameters.filter(p => !p.isDefaultParam && !p.isRepeatedParameter)
        val shortage = obligatory.size - exprs.length  
        if (shortage > 0) 
          return (obligatory.takeRight(shortage).map(p => MissedValueParameter(toParameter(p, substitutor))), new ScUndefinedSubstitutor)

        val res = checkConformanceExt(true, parameters.map(toParameter(_, substitutor)),
          exprs, checkWithImplicits, checkWeakConformance)
        
        if (!res._1.isEmpty) 
          return res
        
        var undefinedSubst = res._2
        
        var i = 1
        while (i < argClauses.length.min(fun.paramClauses.clauses.length)) {
          val t = checkConformance(true, fun.paramClauses.clauses.apply(i).parameters.map({param: ScParameter =>
            Parameter(param.getName, {substitutor.subst(param.getType(TypingContext.empty).getOrElse(Nothing))
          }, param.isDefaultParam, param.isRepeatedParameter)}), argClauses.apply(i),
            checkWithImplicits, checkWeakConformance)
          undefinedSubst += t._2
          i += 1
        }
        return (Seq.empty, undefinedSubst)
      }
      case constructor: ScPrimaryConstructor => {
        val parameters: Seq[ScParameter] =
          if (constructor.parameterList.clauses.length == 0) Seq.empty
          else constructor.parameterList.clauses.apply(0).parameters

        //optimization:
        val hasRepeated = parameters.find(_.isRepeatedParameter) != None
        val maxParams = parameters.length
        if (exprs.length > maxParams && !hasRepeated) return (Seq(new ApplicabilityProblem("16")), new ScUndefinedSubstitutor)
        val minParams = parameters.count(p => !p.isDefaultParam && !p.isRepeatedParameter)
        if (exprs.length < minParams) return (Seq(new ApplicabilityProblem("17")), new ScUndefinedSubstitutor)

        val res = checkConformanceExt(true, parameters.map{param: ScParameter => Parameter(param.getName, {
          substitutor.subst(param.getType(TypingContext.empty).getOrElse(Nothing))
        }, param.isDefaultParam, param.isRepeatedParameter)}, exprs, checkWithImplicits, checkWeakConformance)
        var undefinedSubst = res._2
        if (!res._1.isEmpty) return res
        var i = 1
        while (i < argClauses.length.min(constructor.parameterList.clauses.length)) {
          val t = checkConformance(true, constructor.parameterList.clauses.apply(i).parameters.
                  map({param: ScParameter => Parameter(param.getName, {
            substitutor.subst(param.getType(TypingContext.empty).getOrElse(Nothing))
          }, param.isDefaultParam, param.isRepeatedParameter)}), argClauses.apply(i),
            checkWithImplicits, checkWeakConformance)
          undefinedSubst += t._2
          i += 1
        }
        return (Seq.empty, undefinedSubst)
      }
      case method: PsiMethod => {
        val parameters: Seq[PsiParameter] = method.getParameterList.getParameters.toSeq

        //optimization:
        val hasRepeated = parameters.find(_.isVarArgs) != None
        if (exprs.length > parameters.length && !hasRepeated) return (Seq(new ApplicabilityProblem("18")), new ScUndefinedSubstitutor)
        if (exprs.length < parameters.length - (if (hasRepeated) 1 else 0)) return (Seq(new ApplicabilityProblem("19")), new ScUndefinedSubstitutor)

        checkConformanceExt(false, parameters.map {param: PsiParameter => Parameter("", {
          val tp = substitutor.subst(ScType.create(param.getType, method.getProject, scope))
          if (param.isVarArgs) tp match {
            case ScParameterizedType(_, args) if args.length == 1 => args(0)
            case JavaArrayType(arg) => arg
            case _ => tp
          }
          else tp
        }, false, param.isVarArgs)}, exprs, checkWithImplicits, checkWeakConformance)
      }
      case cc: ScClass if cc.isCase => {
        val parameters: Seq[ScParameter] = {
          cc.clauses match {
            case Some(params: ScParameters) if params.clauses.length != 0 => params.clauses.apply(0).parameters
            case _ => Seq.empty
          }
        }

        //optimization:
        val hasRepeated = parameters.find(_.isRepeatedParameter) != None
        val maxParams = parameters.length
        if (exprs.length > maxParams && !hasRepeated) return (Seq(new ApplicabilityProblem("20")), new ScUndefinedSubstitutor)
        val minParams = parameters.count(p => !p.isDefaultParam && !p.isRepeatedParameter)
        if (exprs.length < minParams) return (Seq(new ApplicabilityProblem("21")), new ScUndefinedSubstitutor)

        checkConformanceExt(true, parameters.map{param: ScParameter => Parameter(param.getName, {
          substitutor.subst(param.getType(TypingContext.empty).getOrElse(Nothing))
        }, param.isDefaultParam, param.isRepeatedParameter)}, exprs, checkWithImplicits, checkWeakConformance)
      }

      case _ => (Seq(new ApplicabilityProblem("22")), new ScUndefinedSubstitutor)
    }
  }
}
