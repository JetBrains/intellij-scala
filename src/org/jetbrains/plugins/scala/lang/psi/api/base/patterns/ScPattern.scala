package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import collection.mutable.ArrayBuffer
import psi.types._
import result.{Failure, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import expr._
import implicits.ScImplicitlyConvertible
import com.intellij.openapi.progress.ProgressManager
import toplevel.imports.usages.ImportUsed
import statements.params.ScTypeParam
import statements.{ScTypeAliasDefinition, ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.psi.api.ScalaElementVisitor
import toplevel.typedef.{ScTypeDefinition, ScClass}
import psi.impl.base.ScStableCodeReferenceElementImpl
import lang.resolve._

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
    var bind: Option[ScalaResolveResult] = ref.bind match {
      case Some(ScalaResolveResult(ta: ScTypeAliasDefinition, substitutor)) => {
        val alType = ta.aliasedType(TypingContext.empty)
        var res: Option[ScalaResolveResult] = null
        for (tp <- alType) {
          ScType.extractClassType(tp) match {
            case Some((clazz: ScClass, subst: ScSubstitutor)) if clazz.isCase => {
              res = Some(new ScalaResolveResult(clazz, substitutor.followed(subst)))
            }
            case _ => res = None
          }
        }
        res
      }
      case Some(ScalaResolveResult(_: ScBindingPattern, _)) => {
        val refImpl = ref.asInstanceOf[ScStableCodeReferenceElementImpl]
        val resolve = refImpl._resolve(refImpl, new ExpandedExtractorResolveProcessor(ref, ref.refName, ref.getKinds(false), ref.getContext match {
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
      case Some(ScalaResolveResult(clazz: ScClass, substitutor: ScSubstitutor)) if clazz.isCase => {
        clazz.constructor match {
          case Some(constructor: ScPrimaryConstructor) => {
            val clauses = constructor.parameterList.clauses
            if (clauses.length == 0) None
            else {
              val subst = if (clazz.typeParameters.length == 0) substitutor else {
                val clazzType = ScParameterizedType(ScDesignatorType(clazz), clazz.getTypeParameters.map(tp =>
                  ScUndefinedType(tp match {case tp: ScTypeParam => new ScTypeParameterType(tp, substitutor)
                  case _ => new ScTypeParameterType(tp, substitutor)})))
                expected match {
                  case Some(tp) => {
                    val t = Conformance.conforms(tp, clazzType)
                    if (t) {
                      val undefSubst = Conformance.undefinedSubst(tp, clazzType)
                      undefSubst.getSubstitutor match {
                        case Some(newSubst) => substitutor.followed(newSubst)
                        case _ => substitutor
                      }
                    } else substitutor
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
              if (i != patternsNumber - 1 || !isSeqWildcard) {
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
      case Some(ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor)) if fun.getName == "unapply" => {
        for (rt <- fun.returnType) {
          if (subst.subst(rt).equiv(lang.psi.types.Boolean)) return None
          subst.subst(rt) match {
            case ScParameterizedType(des, args) if (ScType.extractClassType(des) match {
              case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == "scala.Option" ||
                      clazz.getQualifiedName == "scala.Some" => true
              case _ => false
            }) => {
              if (args.length != 1) return None
              args(0) match {
                case ScTupleType(args) => {
                  if (i < args.length) return Some(args(i))
                  else return None
                }
                case ScParameterizedType(des, args) if (ScType.extractClassType(des) match {
                  case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == "scala.Tuple" => true
                  case _ => false
                }) => {
                  if (i < args.length) return Some(args(i))
                  else return None
                }
                case tp => {
                  if (i == 0) return Some(tp)
                  else return None
                }
              }
            }
            case _ => return None
          }
        }
        None
      }
      case Some(ScalaResolveResult(fun: ScFunction, subst: ScSubstitutor)) if fun.getName == "unapplySeq" => {
        for (rt <- fun.returnType) {
          subst.subst(rt) match {
            case ScParameterizedType(des, args) if (ScType.extractClassType(des) match {
              case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == "scala.Option" ||
                      clazz.getQualifiedName == "scala.Some" => true
              case _ => false
            }) => {
              if (args.length != 1) return None
              (Seq(args(0)) ++ BaseTypes.get(args(0))).find({
                case ScParameterizedType(des, args) if args.length == 1 && (ScType.extractClassType(des) match {
                  case Some((clazz: PsiClass, _)) if clazz.getQualifiedName == "scala.collection.Seq" => true
                  case _ => false
                }) => true
                case _ => false
              }) match {
                case Some(seq@ScParameterizedType(des, args)) => {
                  this match {
                    case n: ScNamingPattern if n.getLastChild.isInstanceOf[ScSeqWildcard] => return Some(seq)
                    case _ => return Some(args(0))
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

  def expectedType: Option[ScType] = getParent match {
    case list : ScPatternList => list.getParent match {
      case _var : ScVariable => Some(_var.getType(TypingContext.empty).getOrElse(return None))
      case _val : ScValue => Some(_val.getType(TypingContext.empty).getOrElse(return None))
    }
    case argList : ScPatternArgumentList => {
      argList.getParent match {
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
    case patternList : ScPatterns => patternList.getParent match {
      case tuple : ScTuplePattern => {
        tuple.getParent match {
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
          case _ => None
        }
      }
      case _ => //todo: XmlPattern
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
    case gen: ScGenerator => {
      val isYield = gen.getParent.getParent.asInstanceOf[ScForStatement].isYield
      var next = gen.getNextSibling
      if (gen.rvalue == null) return null
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
      val processor = new MethodResolveProcessor(gen.rvalue, refName, List(Seq(new Expression(Nothing))) /*todo*/, Seq.empty/*todo*/, None)
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