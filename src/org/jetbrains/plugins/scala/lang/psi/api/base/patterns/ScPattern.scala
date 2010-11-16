package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import collection.mutable.ArrayBuffer
import psi.types._
import nonvalue.{ScMethodType, ScTypePolymorphicType}
import result.{Failure, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import expr._
import implicits.ScImplicitlyConvertible
import com.intellij.openapi.progress.ProgressManager
import toplevel.imports.usages.ImportUsed
import statements.{ScTypeAliasDefinition, ScFunction, ScValue, ScVariable}
import toplevel.typedef.{ScTypeDefinition, ScClass}
import psi.impl.base.ScStableCodeReferenceElementImpl
import lang.resolve._
import processor.{MethodResolveProcessor, CompletionProcessor, ExpandedExtractorResolveProcessor}
import statements.params.{ScParameter, ScTypeParam}
import psi.impl.base.types.ScSimpleTypeElementImpl

/**
 * @author Alexander Podkhalyuzin
 */

trait ScPattern extends ScalaPsiElement {
  def isIrrefutableFor(t: Option[ScType]): Boolean = false

  def getType(ctx: TypingContext) : TypeResult[ScType] = Failure("Cannot type pattern", Some(this))

  def bindings : Seq[ScBindingPattern] = {
    val b = new ArrayBuffer[ScBindingPattern]
    _bindings(this, b)
    b
  }

  override def accept(visitor: ScalaElementVisitor) = visitor.visitPattern(this)

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

  private def resolveReferenceToExtractor(ref: ScStableCodeReferenceElement, i: Int, expected: Option[ScType],
                                          patternsNumber: Int): Option[ScType] = {
    val bind: Option[ScalaResolveResult] = ref.bind match {
      case Some(ScalaResolveResult(_: ScBindingPattern | _: ScParameter, _)) => {
        val refImpl = ref.asInstanceOf[ScStableCodeReferenceElementImpl]
        val resolve = refImpl.doResolve(refImpl, new ExpandedExtractorResolveProcessor(ref, ref.refName, ref.getKinds(false), ref.getContext match {
          case inf: ScInfixPattern => inf.expectedType
          case constr: ScConstructorPattern => constr.expectedType
          case _ => None
        }))
        if (resolve.length != 1) None
        else {
          resolve(0) match {
            case s: ScalaResolveResult => Some(s)
            case _ => None
          }
        }
      }
      case m => m
    }
    bind match {
      case Some(ScalaResolveResult(fun: ScFunction, substitutor: ScSubstitutor)) if fun.getName == "unapply" &&
              fun.parameters.length == 1 => {
        val subst = if (fun.typeParameters.length == 0) substitutor else {
          val undefSubst = fun.typeParameters.foldLeft(ScSubstitutor.empty)((s, p) =>
            s.bindT((p.name, ScalaPsiUtil.getPsiElementId(p)), ScUndefinedType(new ScTypeParameterType(p,
              substitutor))))
          val funType = undefSubst.subst(fun.parameters(0).getType(TypingContext.empty).getOrElse(return None))
          expected match {
            case Some(tp) => {
              val t = Conformance.conforms(tp, funType)
              if (t) {
                val undefSubst = Conformance.undefinedSubst(tp, funType)
                undefSubst.getSubstitutor match {
                  case Some(newSubst) => newSubst.followed(substitutor)
                  case _ => substitutor
                }
              } else substitutor
            }
            case _ => substitutor
          }
        }
        for (rt <- fun.returnType) {
          if (subst.subst(rt).equiv(lang.psi.types.Boolean)) return None
          rt match {
            case ScParameterizedType(des, args) if (ScType.extractClass(des) match {
              case Some(clazz) if clazz.getQualifiedName == "scala.Option" ||
                      clazz.getQualifiedName == "scala.Some" => true
              case _ => false
            }) => {
              if (args.length != 1) return None
              args(0) match {
                case ScTupleType(args) => {
                  if (i < args.length) return Some(subst.subst(args(i)))
                  else return None
                }
                case p@ScParameterizedType(des, args) if p.getTupleType != None => {
                  if (i < args.length) return Some(subst.subst(args(i)))
                  else return None
                }
                case tp => {
                  if (i == 0) return Some(subst.subst(tp))
                  else return None
                }
              }
            }
            case _ => return None
          }
        }
        None
      }
      case Some(ScalaResolveResult(fun: ScFunction, substitutor: ScSubstitutor)) if fun.getName == "unapplySeq" &&
              fun.parameters.length == 1 => {
         val subst = if (fun.typeParameters.length == 0) substitutor else {
          val undefSubst = fun.typeParameters.foldLeft(ScSubstitutor.empty)((s, p) =>
            s.bindT((p.name, ScalaPsiUtil.getPsiElementId(p)), ScUndefinedType(new ScTypeParameterType(p,
              substitutor))))
          val funType = undefSubst.subst(fun.parameters(0).getType(TypingContext.empty).getOrElse(return None))
          expected match {
            case Some(tp) => {
              val t = Conformance.conforms(tp, funType)
              if (t) {
                val undefSubst = Conformance.undefinedSubst(tp, funType)
                undefSubst.getSubstitutor match {
                  case Some(newSubst) => newSubst.followed(substitutor)
                  case _ => substitutor
                }
              } else substitutor
            }
            case _ => substitutor
          }
        }
        for (rt <- fun.returnType) {
          rt match {
            case ScParameterizedType(des, args) if (ScType.extractClass(des) match {
              case Some(clazz) if clazz.getQualifiedName == "scala.Option" ||
                      clazz.getQualifiedName == "scala.Some" => true
              case _ => false
            }) => {
              if (args.length != 1) return None
              (Seq(args(0)) ++ BaseTypes.get(args(0))).find({
                case ScParameterizedType(des, args) if args.length == 1 && (ScType.extractClass(des) match {
                  case Some(clazz) if clazz.getQualifiedName == "scala.collection.Seq" => true
                  case _ => false
                }) => true
                case _ => false
              }) match {
                case Some(seq@ScParameterizedType(des, args)) => {
                  this match {
                    case n: ScNamingPattern if n.getLastChild.isInstanceOf[ScSeqWildcard] => return Some(subst.subst(seq))
                    case _ => return Some(subst.subst(args(0)))
                  }
                }
                case _ => return None
              }
            }
            case _ => return None
          }
        }
        None
      }
      case _ => None
    }
  }

  @volatile
  private var expectedTypeCache: Option[ScType] = null
  @volatile
  private var expectedTypeModCount: Long = 0L

  def expectedType: Option[ScType] = {
    var tp = expectedTypeCache
    val curModCount = getManager.getModificationTracker.getModificationCount
    if (tp != null && curModCount == expectedTypeModCount) {
      return tp
    }
    tp = innerExpectedType
    expectedTypeModCount = curModCount
    expectedTypeCache = tp
    return tp
  }

  private def innerExpectedType: Option[ScType] = getContext match {
    case list : ScPatternList => list.getContext match {
      case _var : ScVariable => Some(_var.getType(TypingContext.empty).getOrElse(return None))
      case _val : ScValue => Some(_val.getType(TypingContext.empty).getOrElse(return None))
    }
    case argList : ScPatternArgumentList => {
      argList.getContext match {
        case constr : ScConstructorPattern => {
          resolveReferenceToExtractor(constr.ref, constr.args.patterns.findIndexOf(_ == this), constr.expectedType,
            argList.patterns.length)
        }
        case _ => None
      }
    }
    case composite: ScCompositePattern => composite.expectedType
    case infix: ScInfixPattern => {
      val i = if (infix.leftPattern == this) 0 else {
        if (this.isInstanceOf[ScTuplePattern]) return None
        1
      }
      resolveReferenceToExtractor(infix.refernece, i, infix.expectedType, 2)
    }
    case par: ScParenthesisedPattern => par.expectedType
    case patternList : ScPatterns => patternList.getContext match {
      case tuple : ScTuplePattern => {
        tuple.getContext match {
          case infix: ScInfixPattern => {
            if (infix.leftPattern != tuple) {
              //so it's right pattern
              val i = tuple.patternList match {
                case Some(patterns: ScPatterns) => patterns.patterns.findIndexOf(_ == this)
                case _ => return None
              }
              return resolveReferenceToExtractor(infix.refernece, i + 1, infix.expectedType, tuple.patternList.get.patterns.length + 1)
            }
          }
          case _ =>
        }
        tuple.expectedType match {
          case Some(ScTupleType(comps)) => {
            for ((t, p) <- comps.elements.zip(patternList.patterns.elements)) {
              if (p == this) return Some(t)
            }
            None
          }
          case Some(par@ScParameterizedType(des, typeArgs)) if par.getTupleType != None => {
            for ((t, p) <- par.getTupleType.get.components.elements.zip(patternList.patterns.elements)) {
              if (p == this) return Some(t)
            }
            None
          }
          case _ => None
        }
      }
      case _ => None//todo: XmlPattern
    }
    case clause: ScCaseClause => clause.getContext/*clauses*/.getContext match {
      case matchStat : ScMatchStmt => matchStat.expr match {
        case Some(e) => Some(e.getType(TypingContext.empty).getOrElse(Any))
        case _ => None
      }
      case _: ScCatchBlock => {
        val thr = JavaPsiFacade.getInstance(getProject).findClass("java.lang.Throwable", getResolveScope)
        if (thr != null) Some(new ScDesignatorType(thr)) else None
      }
      case b : ScBlockExpr => b.expectedType match { //l1.zip(l2) {case (a,b) =>}
        case Some(ScFunctionType(ret, params)) => {
          if (params.length == 0) Some(Unit)
          else if (params.length == 1) Some(params(0))
          else Some(new ScTupleType(params, getProject, getResolveScope))
        }
        case Some(ScParameterizedType(des, args)) if (ScType.extractClass(des) match {
          case Some(clazz) if clazz.getQualifiedName == "scala.PartialFunction" => true
          case Some(clazz) if clazz.getQualifiedName.startsWith("scala.Function") => true
          case _ => false
        }) => {
          if (args.length == 1) Some(Unit)
          else if (args.length == 2) Some(args(0))
          else Some(new ScTupleType(args.slice(0, args.length - 1), getProject, getResolveScope))
        }
        case _ => None
      }
    }
    case named: ScNamingPattern => named.expectedType
    case gen: ScGenerator => {
      val isYield = gen.getContext.getContext.asInstanceOf[ScForStatement].isYield
      var next = gen.getNextSibling
      if (gen.rvalue == null) return None
      //var tp = gen.rvalue.getType(TypingContext.empty).getOrElse(return None) //todo: now it's not used
      while (next != null && !next.isInstanceOf[ScGenerator]) {
        next match {
          case g: ScGuard => //todo: replace type tp to appropriate after this operation
          case e: ScEnumerator => //todo:
          case _ =>
        }
        next = next.getNextSibling
      }
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
      val processor = new MethodResolveProcessor(gen.rvalue, refName, List(Seq(new Expression(Nothing))) /*todo*/, Seq.empty/*todo*/)
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
      if (processor.candidates.length != 1) return None
      else {
        val res = processor.candidates.apply(0)
        res match {
          case ScalaResolveResult(method: ScFunction, subst) => {
            if (method.paramClauses.clauses.length == 0) return None
            val clause = method.paramClauses.clauses.apply(0)
            if (clause.parameters.length != 1) return None
            val param = clause.parameters.apply(0)
            val tp = subst.subst(param.getType(TypingContext.empty).getOrElse(return None))
            tp match {
              case ScFunctionType(_, params) if params.length == 1 => Some(params(0))
              case ScParameterizedType(des, args) if (ScType.extractClassType(des) match {
                case Some((clazz: PsiClass, _)) => clazz.getQualifiedName == "scala.Function1"
                case _ => false
              }) => Some(args(0))
              case _ => None
            }
          }
          case _ => return None
        }
      }
    }
    case enum: ScEnumerator => {
      if (enum.rvalue == null) return None
      Some(enum.rvalue.getType(TypingContext.empty).getOrElse(return None))
    }
    case _ => None //todo
  }
}