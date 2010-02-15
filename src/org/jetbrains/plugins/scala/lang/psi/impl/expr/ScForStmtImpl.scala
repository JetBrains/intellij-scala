package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.psi.tree.TokenSet
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType
import api.statements.ScFunction
import com.intellij.psi._
import com.intellij.psi.scope._
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import com.intellij.openapi.progress.ProgressManager
import api.toplevel.imports.usages.ImportUsed
import lang.resolve.{MethodResolveProcessor, CompletionProcessor, ScalaResolveResult}
import types.result.{Success, Failure, TypeResult, TypingContext}
import types._
import collection.mutable.ArrayBuffer
import implicits.{ImplicitParametersCollector, ScImplicitlyConvertible}
import nonvalue.{Parameter, TypeParameter, ScMethodType, ScTypePolymorphicType}

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScForStatementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScForStatement {

  override def toString: String = "ForStatement"

  def isYield: Boolean = findChildByType(ScalaTokenTypes.kYIELD) != null

  def enumerators: Option[ScEnumerators] = findChild(classOf[ScEnumerators])

  // Binding patterns in reverse order
  def patterns: Seq[ScPattern] = enumerators match {
    case None => return Seq.empty
    case Some(x) => return x.namings.reverse.map((n: Patterned) => n.pattern)
  }

  type Patterned = {
    def pattern: ScPattern
  }

  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    val enumerators: ScEnumerators = this.enumerators match {
      case None => return true
      case Some(x) => x
    }
    if (lastParent == enumerators) return true
    enumerators.processDeclarations(processor, state, null, place)
  }


  override protected def innerType(ctx: TypingContext): TypeResult[ScType] = {

    val failure = Failure("Cannot create expression", Some(this))
    val exprText = new StringBuilder
    val (enums, gens, guards) = enumerators match {
      case None => return Failure("No enumerators", Some(this))
      case Some(x) => {
        (x.enumerators, x.generators, x.guards)
      }
    }
    if (guards.length == 0 && enums.length == 0 && gens.length == 1) {
      val gen = gens(0)
      if (gen.rvalue == null)  return failure
      exprText.append("(").append(gen.rvalue.getText).append(")").append(".").append(if (isYield) "map" else "foreach")
              .append(" { case ").
      append(gen.pattern.getText).append( "=> ")
      body match {
        case Some(x) => exprText.append(x.getText)
        case _ => exprText.append("{}")
      }
      exprText.append(" } ")
    } else if (gens.length > 0){
      val gen = gens(0)
      if (gen.rvalue == null) return failure
      var next = gens(0).getNextSibling
      while (next != null && !next.isInstanceOf[ScGuard] && !next.isInstanceOf[ScEnumerator] &&
              !next.isInstanceOf[ScGenerator]) next = next.getNextSibling
      next match {
        case null =>
        case guard: ScGuard => //todo:
        case gen2: ScGenerator => {
          exprText.append("(").append(gen.rvalue.getText).append(")").append(".").append(if (isYield) "flatMap " else "foreach ").append("{case ").
            append(gen.pattern.getText).append(" => ").append("for {")
          while (next != null) {
            exprText.append(next.getText)
            next = next.getNextSibling
          }
          exprText.append("\n} ")
          if (isYield) exprText.append("yield ")
          body match {
            case Some(x) => exprText append x.getText
            case _ => exprText append "{}"
          }
          exprText.append("\n}")
        }
        case enum: ScEnumerator => //todo:
        case _ =>
      }
    }
    try {
      if (exprText.toString == "") return failure
      val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(exprText.toString, this.getContext)
      return newExpr.getNonValueType(ctx)
    }
    catch {
      case e: Exception => return failure
    }
  }
}

/* todo: remove it, this is just an idea.
val failure = Failure("Cannot create expression", Some(this))
    val gen = enumerators.getOrElse(return failure).generators.firstOption.getOrElse(return failure)
    val isYield = gen.getParent.getParent.asInstanceOf[ScForStatement].isYield
    var next = gen.getNextSibling
    if (gen.rvalue == null) return failure
    //var tp = gen.rvalue.getType(TypingContext.empty).getOrElse(return None) //todo: now it's not used
    while (next != null && !next.isInstanceOf[ScGenerator]) next = next.getNextSibling
    val nextGen = next != null
    val refName = {
      (nextGen, isYield) match {
        case (true, true) => "flatMap"
        case (true, false) => "foreach"
        case (false, true) => "map"
        case (false, false) => "foreach"
      }
    }
    import Compatibility.Expression
    val retType = body match {case Some(x) => x.getType(TypingContext.empty).getOrElse(types.Any) case _ => types.Any}
    val exprType = new ScFunctionType(retType, Seq(gen.pattern.expectedType.getOrElse(types.Nothing)), getProject)
    val processor = new MethodResolveProcessor(gen.rvalue, refName, List(Seq(new Expression(exprType))), Seq.empty, None)
    def processTypes(e: ScExpression) {
      ProgressManager.checkCanceled
      val result = e.getType(TypingContext.empty).getOrElse(return) //do not resolve if Type is unknown
      processor.processType(result, e, ResolveState.initial)
      if (processor.candidates.length == 0 || (processor.isInstanceOf[CompletionProcessor] &&
              processor.asInstanceOf[CompletionProcessor].collectImplicits)) {
        for (t <- e.getImplicitTypes) {
          ProgressManager.checkCanceled
          val importsUsed = e.getImportsForImplicit(t)
          var state = ResolveState.initial.put(ImportUsed.key, importsUsed)
          e.getClazzForType(t) match {
            case Some(cl: PsiClass) => state = state.put(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY, cl)
            case _ =>
          }
          processor.processType(t, e, state)
        }
      }
    }
    processTypes(gen.rvalue)
    if (processor.candidates.length != 1) return failure
    else {
      val res = processor.candidates.apply(0)
      res match {
        case ScalaResolveResult(method: ScFunction, subst) => {
          var res = subst.subst(method.polymorphicType)
          res match {
            case ScFunctionType(retType: ScType, params: Seq[ScType]) => retType
            case ScMethodType(retType, _, _) => retType
            case ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams) => {
              val exprs: Seq[Expression] = Seq(new Expression(exprType))
              res = ScalaPsiUtil.localTypeInference(retType, params, exprs, typeParams)
            }
            case _ => return failure
          }
          /*res match {
            case t@ScTypePolymorphicType(ScMethodType(retType, params, impl), typeParams) if impl => {
              val s: ScSubstitutor = typeParams.foldLeft(ScSubstitutor.empty) {
                (subst: ScSubstitutor, tp: TypeParameter) =>
                  subst.bindT(tp.name, new ScUndefinedType(new ScTypeParameterType(tp.ptp, ScSubstitutor.empty)))
              }
              val exprs = new ArrayBuffer[Expression]
              val iterator = params.iterator
              while (iterator.hasNext) {
                val param = iterator.next
                val paramType = s.subst(param.paramType) //we should do all of this with information known before
                val collector = new ImplicitParametersCollector(this, paramType)
                val results = collector.collect
                if (results.length == 1) {
                  results(0) match {
                    case ScalaResolveResult(patt: ScBindingPattern, subst) => {
                      exprs += new Expression(subst.subst(patt.getType(TypingContext.empty).get))
                    }
                    case ScalaResolveResult(fun: ScFunction, subst) => {
                      val funType = {
                        if (fun.parameters.length == 0 || fun.paramClauses.clauses.apply(0).isImplicit) {
                          subst.subst(fun.getType(TypingContext.empty).get) match {
                            case ScFunctionType(ret, _) => ret
                            case x => x
                          }
                        }
                        else subst.subst(fun.getType(TypingContext.empty).get)
                      }
                      exprs += new Expression(funType)
                    }
                  }
                } else exprs += new Expression(Any)
              }
              val subst = t.polymorphicTypeSubstitutor
              res = ScalaPsiUtil.localTypeInference(retType, params, exprs.toSeq, typeParams, subst)
            }
            case _ =>
          }

          res match {
            case ScMethodType(retType, params, impl) if impl => res = retType
            case ScTypePolymorphicType(internal, typeParams) if expectedType != None => {
              res = ScalaPsiUtil.localTypeInference(internal, Seq(Parameter("", internal.inferValueType, false, false)),
                Seq(new Expression(expectedType.get)), typeParams)
            }
            case _ =>
          }*/
          Success(res, Some(this))
        }
        case _ => return failure
      }
    }
    */