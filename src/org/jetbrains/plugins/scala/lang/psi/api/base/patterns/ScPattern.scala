package org.jetbrains.plugins.scala
package lang
package psi
package api
package base
package patterns

import com.intellij.psi._
import com.intellij.psi.util.PsiModificationTracker
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeVariableTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.expr.xml.ScXmlPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve._
import org.jetbrains.plugins.scala.lang.resolve.processor.{CompletionProcessor, ExpandedExtractorResolveProcessor}
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_11

import scala.annotation.tailrec
import scala.collection.immutable.Set
import scala.collection.mutable.ArrayBuffer

/**
 * @author Alexander Podkhalyuzin
 */

trait ScPattern extends ScalaPsiElement {
  def isIrrefutableFor(t: Option[ScType]): Boolean = false

  def getType(ctx: TypingContext): TypeResult[ScType] = Failure("Cannot type pattern", Some(this))

  def bindings: Seq[ScBindingPattern] = {
    val b = new ArrayBuffer[ScBindingPattern]

    def inner(p: ScPattern) {
      p match {
        case binding: ScBindingPattern => b += binding
        case _ =>
      }

      for (sub <- p.subpatterns) {
        inner(sub)
      }
    }

    inner(this)
    b
  }

  def typeVariables: Seq[ScTypeVariableTypeElement] = {
    val b = new ArrayBuffer[ScTypeVariableTypeElement]

    def inner(p: ScPattern) {
      p match {
        case ScTypedPattern(te) =>
          te.accept(new ScalaRecursiveElementVisitor {
            override def visitTypeVariableTypeElement(tvar: ScTypeVariableTypeElement): Unit = {
              b += tvar
            }
          })
        case _ =>
      }

      for (sub <- p.subpatterns) {
        inner(sub)
      }
    }

    inner(this)
    b
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitPattern(this)
  }

  def subpatterns: Seq[ScPattern] = this match {
    case _: ScReferencePattern => Seq.empty
    case _ => findChildrenByClassScala[ScPattern](classOf[ScPattern])
  }

  private def resolveReferenceToExtractor(ref: ScStableCodeReferenceElement, i: Int, expected: Option[ScType],
                                          patternsNumber: Int): Option[ScType] = {
    val bind: Option[ScalaResolveResult] = ref.bind() match {
      case Some(ScalaResolveResult(_: ScBindingPattern | _: ScParameter, _)) =>
        val resolve = ref match {
          case refImpl: ScStableCodeReferenceElementImpl =>
            refImpl.doResolve(refImpl, new ExpandedExtractorResolveProcessor(ref, ref.refName, ref.getKinds(incomplete = false), ref.getContext match {
              case inf: ScInfixPattern => inf.expectedType
              case constr: ScConstructorPattern => constr.expectedType
              case _ => None
            }))
        }
        if (resolve.length != 1) None
        else {
          resolve(0) match {
            case s: ScalaResolveResult => Some(s)
            case _ => None
          }
        }
      case m => m
    }

    def calculateSubstitutor(_tp: ScType, funType: ScType, substitutor: ScSubstitutor): ScSubstitutor = {
      val tp = _tp match {
        case ex: ScExistentialType => ex.skolem
        case _                     => _tp
      }

      def rightWay: ScSubstitutor = {
        val t = Conformance.conformsInner(tp, substitutor.subst(funType), Set.empty, new ScUndefinedSubstitutor)
        if (t._1) {
          val undefSubst = t._2
          undefSubst.getSubstitutor match {
            case Some(newSubst) => newSubst.followed(substitutor)
            case _              => substitutor
          }
        } else substitutor
      }

      //todo: looks quite hacky to try another direction first, do you know better? see SCL-6543
      val t = Conformance.conformsInner(substitutor.subst(funType), tp, Set.empty, new ScUndefinedSubstitutor)
      if (t._1) {
        val undefSubst = t._2
        undefSubst.getSubstitutor match {
          case Some(newSubst) => newSubst.followed(substitutor)
          case _              => rightWay
        }
      } else rightWay
    }

    bind match {
      case Some(ScalaResolveResult(fun: ScFunction, substitutor: ScSubstitutor)) if fun.name == "unapply" &&
              fun.parameters.length == 1 =>
        val subst = if (fun.typeParameters.length == 0) substitutor else {
          var undefSubst = fun.typeParameters.foldLeft(ScSubstitutor.empty)((s, p) =>
            s.bindT((p.name, ScalaPsiUtil.getPsiElementId(p)), ScUndefinedType(ScTypeParameterType(p,
              substitutor))))
          val clazz = ScalaPsiUtil.getContextOfType(this, true, classOf[ScTemplateDefinition])
          clazz match {
            case clazz: ScTemplateDefinition =>
              undefSubst = undefSubst.followed(new ScSubstitutor(ScThisType(clazz)))
            case _ =>
          }
          val funType = undefSubst.subst(fun.parameters(0).getType(TypingContext.empty) match {
            case Success(tp, _) => tp
            case _ => return None
          })
          expected match {
            case Some(tp) => calculateSubstitutor(tp, funType, substitutor)
            case _ => substitutor
          }
        }
        fun.returnType match {
          case Success(rt, _) =>
            def updateRes(tp: ScType): ScType = {
              val parameters: Seq[ScTypeParam] = fun.typeParameters
              tp.recursiveVarianceUpdate {
                case (tp: ScTypeParameterType, variance) if parameters.contains(tp.param) =>
                  (true, if (variance == -1) substitutor.subst(tp.lower.v)
                  else substitutor.subst(tp.upper.v))
                case (typez, _) => (false, typez)
              }
            }
            if (subst.subst(rt).equiv(lang.psi.types.Boolean)) return None
            val args = ScPattern.extractorParameters(rt, this, ScPattern.isOneArgCaseClassMethod(fun))
            if (i < args.length) return Some(updateRes(subst.subst(args(i)).unpackedType))
            else return None
          case _ =>
        }
        None
      case Some(ScalaResolveResult(fun: ScFunction, substitutor: ScSubstitutor)) if fun.name == "unapplySeq" &&
              fun.parameters.length == 1 =>
        val subst = if (fun.typeParameters.length == 0) substitutor else {
         val undefSubst = substitutor followed fun.typeParameters.foldLeft(ScSubstitutor.empty)((s, p) =>
           s.bindT((p.name, ScalaPsiUtil.getPsiElementId(p)), ScUndefinedType(ScTypeParameterType(p,
             substitutor))))
         val funType = undefSubst.subst(fun.parameters(0).getType(TypingContext.empty) match {
           case Success(tp, _) => tp
           case _ => return None
         })
         expected match {
           case Some(tp) => calculateSubstitutor(tp, funType, substitutor)
           case _ => substitutor
         }
       }
        fun.returnType match {
          case Success(rt, _) =>
            val args = ScPattern.extractorParameters(subst.subst(rt), this, ScPattern.isOneArgCaseClassMethod(fun))
            if (args.length == 0) return None
            if (i < args.length - 1) return Some(subst.subst(args(i)))
            val lastArg = args(args.length - 1)
            (Seq(lastArg) ++ BaseTypes.get(lastArg)).find({
              case ScParameterizedType(des, seqArgs) if seqArgs.length == 1 && (ScType.extractClass(des) match {
                case Some(clazz) if clazz.qualifiedName == "scala.collection.Seq" => true
                case _ => false
              }) => true
              case _ => false
            }) match {
              case Some(seq@ScParameterizedType(des, seqArgs)) =>
                this match {
                  case n: ScNamingPattern if n.getLastChild.isInstanceOf[ScSeqWildcard] => return Some(subst.subst(seq))
                  case _ => return Some(subst.subst(seqArgs(0)))
                }
              case _ => return None
            }
          case _ =>
        }
        None
      case _ => None
    }
  }

  def expectedType: Option[ScType] = {
    CachesUtil.get(this, CachesUtil.PATTERN_EXPECTED_TYPE,
      new CachesUtil.MyProvider(this, (p: ScPattern) => p.innerExpectedType)
      (PsiModificationTracker.MODIFICATION_COUNT))
  }

  private def innerExpectedType: Option[ScType] = {
    getContext match {
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
      case argList : ScPatternArgumentList =>
        argList.getContext match {
          case constr : ScConstructorPattern =>
            resolveReferenceToExtractor(constr.ref, constr.args.patterns.indexWhere(_ == this), constr.expectedType, argList.patterns.length)
          case _ => None
        }
      case composite: ScCompositePattern => composite.expectedType
      case infix: ScInfixPattern =>
        val i = if (infix.leftPattern == this) 0 else {
          if (this.isInstanceOf[ScTuplePattern]) return None
          1
        }
        resolveReferenceToExtractor(infix.refernece, i, infix.expectedType, 2)
      case par: ScParenthesisedPattern => par.expectedType
      case patternList : ScPatterns => patternList.getContext match {
        case tuple : ScTuplePattern =>
          tuple.getContext match {
            case infix: ScInfixPattern =>
              if (infix.leftPattern != tuple) {
                //so it's right pattern
                val i = tuple.patternList match {
                  case Some(patterns: ScPatterns) => patterns.patterns.indexWhere(_ == this)
                  case _ => return None
                }
                return resolveReferenceToExtractor(infix.refernece, i + 1, infix.expectedType, tuple.patternList.get.patterns.length + 1)
              }
            case _ =>
          }

          tuple.expectedType.flatMap {
            case ScTupleType(comps)                       =>
              for ((t, p) <- comps.iterator.zip(patternList.patterns.iterator)) {
                if (p == this) return Some(t)
              }
              None
            case et0 if et0 == types.AnyRef || et0 == types.Any => Some(types.Any)
            case _                                              => None
          }
        case _: ScXmlPattern =>
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
        case _ => None
      }
      case clause: ScCaseClause => clause.getContext/*clauses*/.getContext match {
        case matchStat : ScMatchStmt => matchStat.expr match {
          case Some(e) => Some(e.getType(TypingContext.empty).getOrAny)
          case _ => None
        }
        case b: ScBlockExpr if b.getContext.isInstanceOf[ScCatchBlock] =>
          val thr = ScalaPsiManager.instance(getProject).getCachedClass(getResolveScope, "java.lang.Throwable")
          if (thr != null) Some(ScType.designator(thr)) else None
        case b : ScBlockExpr =>
          b.expectedType(fromUnderscore = false) match {
            case Some(et) =>
              et.removeAbstracts match {
                case ScFunctionType(_, Seq()) => Some(types.Unit)
                case ScFunctionType(_, Seq(p0)) => Some(p0)
                case ScFunctionType(_, params) =>
                  val tt = ScTupleType(params)(getProject, getResolveScope)
                  Some(tt)
                case ScPartialFunctionType(_, param) => Some(param)
                case _ => None
              }
            case None => None
          }
        case _ => None
      }
      case named: ScNamingPattern => named.expectedType
      case gen: ScGenerator =>
        val analog = getAnalog
        if (analog != this) analog.expectedType
        else None
      case enum: ScEnumerator =>
        if (enum.rvalue == null) return None
        Some(enum.rvalue.getType(TypingContext.empty) match {
          case Success(tp, _) => tp
          case _ => return None
        })
      case _ => None
    }
  }

  def getAnalog: ScPattern = {
    getContext match {
      case gen: ScGenerator =>
        val f: ScForStatement = gen.getContext.getContext match {
          case fr: ScForStatement => fr
          case _ => return this
        }
        f.getDesugarizedExpr match {
          case Some(expr) =>
            if (analog != null) return analog
          case _ =>
        }
        this
      case _ => this
    }
  }

  var desugarizedPatternIndex = -1
  var analog: ScPattern = null
}

object ScPattern {
  def isOneArgCaseClassMethod(fun: ScFunction): Boolean = {
    fun.syntheticCaseClass match {
      case Some(c: ScClass) => c.constructor.exists(_.effectiveFirstParameterSection.length == 1)
      case _ => false
    }
  }

  private def findMember(name: String, tp: ScType, place: PsiElement): Option[ScType] = {
    val cp = new CompletionProcessor(StdKinds.methodRef, place, forName = Some(name))
    cp.processType(tp, place)
    cp.candidatesS.flatMap {
      case ScalaResolveResult(fun: ScFunction, subst) if fun.parameters.length == 0 && fun.name == name =>
        Seq(subst.subst(fun.returnType.getOrAny))
      case ScalaResolveResult(b: ScBindingPattern, subst) if b.name == name =>
        Seq(subst.subst(b.getType(TypingContext.empty).getOrAny))
      case ScalaResolveResult(param: ScClassParameter, subst) if param.name == name =>
        Seq(subst.subst(param.getType(TypingContext.empty).getOrAny))
      case _ => Seq.empty
    }.headOption
  }

  private def extractPossibleProductParts(receiverType: ScType, place: PsiElement, isOneArgCaseClass: Boolean): Seq[ScType] = {
    val res: ArrayBuffer[ScType] = new ArrayBuffer[ScType]()
    @tailrec
    def collect(i: Int) {
      findMember(s"_$i", receiverType, place) match {
        case Some(tp) if !isOneArgCaseClass =>
          res += tp
          collect(i + 1)
        case _ =>
          if (i == 1) res += receiverType
      }
    }
    collect(1)
    res.toSeq
  }

  def extractProductParts(tp: ScType, place: PsiElement): Seq[ScType] = {
    extractPossibleProductParts(tp, place, isOneArgCaseClass = false)
  }

  def extractorParameters(returnType: ScType, place: PsiElement, isOneArgCaseClass: Boolean): Seq[ScType] = {
    def collectFor2_11: Seq[ScType] = {
      findMember("isEmpty", returnType, place) match {
        case Some(tp) if types.Boolean.equiv(tp) =>
        case _ => return Seq.empty
      }

      val receiverType = findMember("get", returnType, place).getOrElse(return Seq.empty)
      extractPossibleProductParts(receiverType, place, isOneArgCaseClass)
    }

    val level = place.languageLevel
    if (level >= Scala_2_11) collectFor2_11
    else {
      returnType match {
        case ScParameterizedType(des, args) =>
          ScType.extractClass(des) match {
            case Some(clazz) if clazz.qualifiedName == "scala.Option" ||
                    clazz.qualifiedName == "scala.Some" =>
              if (args.length == 1) {
                def checkProduct(tp: ScType): Seq[ScType] = {
                  val productChance = collectFor2_11
                  if (productChance.length <= 1) Seq(tp)
                  else {
                    val productFqn = "scala.Product" + productChance.length
                    (for {
                      productClass <- Option(ScalaPsiManager.instance(place.getProject).getCachedClass(place.getResolveScope, productFqn))
                      clazz <- ScType.extractClass(tp, Some(place.getProject))
                    } yield clazz == productClass || clazz.isInheritor(productClass, true)).
                      filter(identity).fold(Seq(tp))(_ => productChance)
                  }
                }
                args(0) match {
                  case tp if isOneArgCaseClass => Seq(tp)
                  case ScTupleType(comps) => comps
                  case tp => checkProduct(tp)
                }
              } else Seq.empty
            case _ => Seq.empty
          }
        case _ => Seq.empty
      }
    }
  }
}