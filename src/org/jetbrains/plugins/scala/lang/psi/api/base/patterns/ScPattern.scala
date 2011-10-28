package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import collection.mutable.ArrayBuffer
import psi.types._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import com.intellij.psi._
import expr._
import result.{Success, Failure, TypeResult, TypingContext}
import statements.{ScFunction, ScValue, ScVariable}
import psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlPattern
import lang.resolve._
import processor.ExpandedExtractorResolveProcessor
import statements.params.ScParameter
import caches.CachesUtil
import util.PsiModificationTracker
import psi.impl.ScalaPsiManager

/**
 * @author Alexander Podkhalyuzin
 */

trait ScPattern extends ScalaPsiElement {
  def isIrrefutableFor(t: Option[ScType]): Boolean = false

  def getType(ctx: TypingContext): TypeResult[ScType] = Failure("Cannot type pattern", Some(this))

  def bindings: Seq[ScBindingPattern] = {
    val b = new ArrayBuffer[ScBindingPattern]
    _bindings(this, b)
    b
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitPattern(this)
  }

  private def _bindings(p: ScPattern, b: ArrayBuffer[ScBindingPattern]) {
    p match {
      case binding: ScBindingPattern => b += binding
      case _ =>
    }

    for (sub <- p.subpatterns) {
      _bindings(sub, b)
    }
  }

  def subpatterns: Seq[ScPattern] = {
    if (this.isInstanceOf[ScReferencePattern]) return Seq.empty
    findChildrenByClassScala[ScPattern](classOf[ScPattern])
  }

  private def resolveReferenceToExtractor(ref: ScStableCodeReferenceElement, i: Int, expected: Option[ScType],
                                          patternsNumber: Int): Option[ScType] = {
    val bind: Option[ScalaResolveResult] = ref.bind() match {
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
          val funType = undefSubst.subst(fun.parameters(0).getType(TypingContext.empty) match {
            case Success(tp, _) => tp
            case _ => return None
          })
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
        fun.returnType match {
          case Success(rt, _) =>
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
          case _ =>
        }
        None
      }
      case Some(ScalaResolveResult(fun: ScFunction, substitutor: ScSubstitutor)) if fun.getName == "unapplySeq" &&
              fun.parameters.length == 1 => {
         val subst = if (fun.typeParameters.length == 0) substitutor else {
          val undefSubst = substitutor followed fun.typeParameters.foldLeft(ScSubstitutor.empty)((s, p) =>
            s.bindT((p.name, ScalaPsiUtil.getPsiElementId(p)), ScUndefinedType(new ScTypeParameterType(p,
              substitutor))))
          val funType = undefSubst.subst(fun.parameters(0).getType(TypingContext.empty) match {
            case Success(tp, _) => tp
            case _ => return None
          })
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
        fun.returnType match {
          case Success(rt, _) =>
            subst.subst(rt) match {
              case ScParameterizedType(des, args) if (ScType.extractClass(des) match {
                case Some(clazz) if clazz.getQualifiedName == "scala.Option" ||
                        clazz.getQualifiedName == "scala.Some" => true
                case _ => false
              }) => {
                if (args.length != 1) return None
                val tupleArgs = args(0) match {
                  case ScTupleType(comps) => comps
                  case p: ScParameterizedType =>
                    p.getTupleType match {
                      case Some(ScTupleType(comps)) => comps
                      case _ => Seq(p)
                    }
                  case tp => Seq(tp)
                }
                if (tupleArgs.length == 0) return None
                val lastArg = tupleArgs(tupleArgs.length - 1)
                (Seq(lastArg) ++ BaseTypes.get(lastArg)).find({
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
          case _ =>
        }
        None
      }
      case _ => None
    }
  }

  def expectedType: Option[ScType] = {
    CachesUtil.get(this, CachesUtil.PATTERN_EXPECTED_TYPE,
      new CachesUtil.MyProvider(this, (p: ScPattern) => innerExpectedType)
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  private def innerExpectedType: Option[ScType] = getContext match {
    case list : ScPatternList => list.getContext match {
      case _var : ScVariable => Some(_var.getType(TypingContext.empty) match {
        case Success(tp, _) => tp
        case _ => return None
      })
      case _val : ScValue => Some(_val.getType(TypingContext.empty) match {
        case Success(tp, _) => tp
        case _ => return None
      })
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

        tuple.expectedType.flatMap {et0 =>
          ScType.extractTupleType(et0) match {
            case Some(ScTupleType(comps)) => {
              for ((t, p) <- comps.iterator.zip(patternList.patterns.iterator)) {
                if (p == this) return Some(t)
              }
              None
            }
            case None => None
          }
        }

      }
      case _: ScXmlPattern => {
        val nodeClass: PsiClass = ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.xml.Node")
        if (nodeClass == null) return None
        this match {
          case n: ScNamingPattern if n.getLastChild.isInstanceOf[ScSeqWildcard] =>
            val seqClass: PsiClass =
              ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "scala.collection.Seq")
            if (seqClass == null) return None
            Some(ScParameterizedType(ScDesignatorType(seqClass), Seq(ScDesignatorType(nodeClass))))
          case _ => Some(ScDesignatorType(nodeClass))
        }
      }
      case _ => None
    }
    case clause: ScCaseClause => clause.getContext/*clauses*/.getContext match {
      case matchStat : ScMatchStmt => matchStat.expr match {
        case Some(e) => Some(e.getType(TypingContext.empty).getOrAny)
        case _ => None
      }
      case _: ScCatchBlock => {
        val thr = JavaPsiFacade.getInstance(getProject).findClass("java.lang.Throwable", getResolveScope)
        if (thr != null) Some(ScType.designator(thr)) else None
      }
      case b : ScBlockExpr => {
        b.expectedType(false) match {
          case Some(et) =>
            ScType.extractFunctionType(et) match {
              case Some(ScFunctionType(_, Seq())) => Some(Unit)
              case Some(ScFunctionType(_, Seq(p0))) => Some(p0.removeAbstracts)
              case Some(ScFunctionType(_, params)) =>
                val tt = new ScTupleType(params.map(_.removeAbstracts))(getProject, getResolveScope)
                Some(tt)
              case None =>
                ScType.extractPartialFunctionType(et) match {
                  case Some((des, param, _)) => Some(param.removeAbstracts)
                  case None => None
                }
            }
          case None => None
        }
      }
    }
    case named: ScNamingPattern => named.expectedType
    case gen: ScGenerator => {
      val analog = getAnalog
      if (analog != this) analog.expectedType
      else None
    }
    case enum: ScEnumerator => {
      if (enum.rvalue == null) return None
      Some(enum.rvalue.getType(TypingContext.empty) match {
        case Success(tp, _) => tp
        case _ => return None
      })
    }
    case _ => None
  }

  def getAnalog: ScPattern = {
    getContext match {
      case gen: ScGenerator => {
        val f: ScForStatement = gen.getContext.getContext.asInstanceOf[ScForStatement]
        f.getDesugarisedExpr match {
          case Some(expr) =>
            if (analog != null) return analog
          case _ =>
        }
        this
      }
      case _ => this
    }
  }

  var desugarizedPatternIndex = -1
  var analog: ScPattern = null
}