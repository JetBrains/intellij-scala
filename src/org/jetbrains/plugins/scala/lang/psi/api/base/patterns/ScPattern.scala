package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import collection.mutable.ArrayBuffer
import expr.{ScBlockExpr, ScCatchBlock, ScMatchStmt}
import psi.types._
import result.{Failure, TypeResult, TypingContext}
import statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import lang.resolve.ScalaResolveResult
import toplevel.typedef.ScClass

/**
 * @author Alexander Podkhalyuzin
 */

trait ScPattern extends ScalaPsiElement {
  def getType(ctx: TypingContext) : TypeResult[ScType] = Failure("Cannot type pattern", Some(this))

  def bindings : Seq[ScBindingPattern] = {
    val b = new ArrayBuffer[ScBindingPattern]
    _bindings(this, b)
    b
  }

  private def _bindings(p : ScPattern, b : ArrayBuffer[ScBindingPattern]) : Unit = {
    p match {
      case binding: ScBindingPattern => b += binding
      case _ =>
    }

    for (sub <- p.subpatterns) {
      _bindings(sub, b)
    }
  }

  def subpatterns : Seq[ScPattern] = {
    if (this.isInstanceOf[ScReferencePattern]) return Seq.empty
    findChildrenByClassScala[ScPattern](classOf[ScPattern])
  }

  def expectedType: Option[ScType] = getParent match {
    case list : ScPatternList => list.getParent match {
      case _var : ScVariable => Some(_var.getType(TypingContext.empty).getOrElse(return None))
      case _val : ScValue => Some(_val.getType(TypingContext.empty).getOrElse(return None))
    }
    case argList : ScPatternArgumentList => {
      argList.getParent match {
        case constr : ScConstructorPattern => {
          constr.ref.bind match {
            case Some(ScalaResolveResult(clazz: ScClass, substitutor: ScSubstitutor)) if clazz.isCase => {
              val i = argList.patterns.findIndexOf(_ == this)
              clazz.constructor match {
                case Some(constructor: ScPrimaryConstructor) => {
                  val clauses = constructor.parameterList.clauses
                  if (clauses.length == 0) None
                  else {
                    val subst = if (clazz.typeParameters.length == 0) substitutor else {
                      constr.expectedType match {
                        case Some(ScParameterizedType(des, args)) if (ScType.extractClassType(des) match {
                          case Some((`clazz`, _)) => true
                          case _ => false
                        }) => {
                          val s = args.zip(clazz.typeParameters).foldLeft(ScSubstitutor.empty) {
                            (subst, pair) =>
                              val scType = pair._1
                              val typeParameter = pair._2
                              subst.bindT(typeParameter.getName, scType)
                          }
                          substitutor.followed(s)
                        }
                        case _ => substitutor
                      }
                    }
                    val params = clauses(0).parameters
                    if (params.length == 0) return None
                    def isSeqWildcard: Boolean = {
                      this match {
                        case naming: ScNamingPattern => {
                          naming.getLastChild.isInstanceOf[ScSeqWildcard]
                        }
                        case _ => false
                      }
                    }
                    if (i != argList.patterns.length - 1 || !isSeqWildcard) {
                      if (i < params.length) Some(subst.subst(params(i).getType(TypingContext.empty).getOrElse(return None)))
                      else if (params(params.length - 1).isRepeatedParameter) {
                        Some(subst.subst(params(params.length - 1).getType(TypingContext.empty).getOrElse(return None)))
                      } else None
                    } else {
                      if (params.length > i + 1) None
                      else if (!params(params.length - 1).isRepeatedParameter) None
                      else {
                        val seqClass = JavaPsiFacade.getInstance(getProject).findClass("scala.collection.Seq", getResolveScope)
                        if (seqClass == null) return None
                        val tp = subst.subst(params(params.length - 1).getType(TypingContext.empty).getOrElse(return None))
                        Some(ScParameterizedType(ScDesignatorType(seqClass), Seq(tp)))
                      }
                    }
                  }
                }
                case _ => None
              }
            }
            case _ => None //todo: extractor patterns
          }
        }
      }
    }
    case patternList : ScPatterns => patternList.getParent match {
      case tuple : ScTuplePattern => tuple.expectedType match {
        case Some(ScTupleType(comps)) => {
          for((t, p) <- comps.elements.zip(patternList.patterns.elements)) {
            if (p == this) return Some(t)
          }
          None
        }
        case _ => None
      }
    }
    case clause : ScCaseClause => clause.getParent/*clauses*/.getParent match {
      case matchStat : ScMatchStmt => matchStat.expr match {
        case Some(e) => Some(e.getType(TypingContext.empty).getOrElse(Any))
        case _ => None
      }
      case _ : ScCatchBlock => {
        val thr = JavaPsiFacade.getInstance(getProject).findClass("java.lang.Throwable")
        if (thr != null) Some(new ScDesignatorType(thr)) else None
      }
      case b : ScBlockExpr => b.expectedType match { //l1.zip(l2) {case (a,b) =>}
        case Some(ScFunctionType(ret, params)) => {
          if (params.length == 0) Some(Unit)
          else if (params.length == 1) Some(params(0))
          else Some(new ScTupleType(params, getProject))
        }
        case Some(ScParameterizedType(des, args)) if (ScType.extractClassType(des) match {
          case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == "scala.PartialFunction" => true
          case Some((clazz: PsiClass, _)) if clazz.getQualifiedName.startsWith("scala.Function") => true
          case _ => false
        }) => {
          if (args.length == 1) Some(Unit)
          else if (args.length == 2) Some(args(0))
          else Some(new ScTupleType(args.slice(0, args.length - 1), getProject))
        }
        case _ => None
      }
    }
    case named: ScNamingPattern => named.expectedType
    case _ => None //todo
  }
}