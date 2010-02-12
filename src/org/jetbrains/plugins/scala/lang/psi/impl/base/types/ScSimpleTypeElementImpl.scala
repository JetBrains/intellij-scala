package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.psi._
import tree.{IElementType, TokenSet}
import api.base.types._
import psi.ScalaPsiElementImpl
import lexer.ScalaTokenTypes
import psi.types._
import psi.impl.toplevel.synthetic.ScSyntheticClass
import collection.Set
import result.{Failure, TypeResult, Success, TypingContext}
import scala.None
import api.base.{ScConstructor, ScReferenceElement}
import collection.mutable.ArrayBuffer
import api.base.patterns.ScReferencePattern
import lang.resolve.{ResolveUtils, ScalaResolveResult}
import util.PsiTreeUtil
import api.statements._
import api.toplevel.{ScTypeParametersOwner, ScTypedDefinition, ScNamedElement, ScPolymorphicElement}
import params.ScTypeParam
import api.expr.{ScNewTemplateDefinition, ScExpression}
import api.toplevel.templates.{ScExtendsBlock, ScClassParents}

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleTypeElement {
  override def toString: String = "SimpleTypeElement"

  def singleton = getNode.findChildByType(ScalaTokenTypes.kTYPE) != null

  protected def innerType(ctx: TypingContext): TypeResult[ScType] = {
    val lift: (ScType) => Success[ScType] = Success(_, Some(this))

    val result: TypeResult[ScType] = reference match {
      case Some(ref) => ref.qualifier match {
        case Some(q) => wrap(ref.bind) flatMap {
          case ScalaResolveResult(aliasDef: ScTypeAliasDefinition, s) => {
            if (aliasDef.typeParameters.length == 0) aliasDef.aliasedType(ctx) map {t => s.subst(t)}
            else {
              //todo work with recursive aliases
              lift(new ScTypeConstructorType(aliasDef, s))
            }
          }
          case ScalaResolveResult(synth: ScSyntheticClass, _) => lift(synth.t)
          case _ => Success(ScProjectionType(new ScSingletonType(q), ref), Some(this))
        }
        case None => wrap(ref.bind) flatMap {
          case ScalaResolveResult(e, s) => e match {
            case aliasDef: ScTypeAliasDefinition =>
              if (aliasDef.typeParameters.length == 0) aliasDef.aliasedType(ctx) map {t => s.subst(t)}
              else {
                //todo work with recursive aliases
                lift(new ScTypeConstructorType(aliasDef, s))
              }
            case alias: ScTypeAliasDeclaration => lift(new ScTypeAliasType(alias, s))
            case tp: PsiTypeParameter => lift(ScalaPsiManager.typeVariable(tp))
            case synth: ScSyntheticClass => lift(synth.t)
            case null => lift(Any)
            case _ => lift(ScDesignatorType(e))
          }
        }
      }
      case None => Failure("Reference is not defined", Some(this))
    }
    if (result.isEmpty && singleton) {
      Success(ScSingletonType(pathElement), Some(this))
    } else {
      //if type parameters ommited, we should to infer them manually, but this is Parameterized or TypeArgs Type.
      getContext match {
        case _: ScParameterizedTypeElement | _: ScTypeArgs => return result
        case _ =>
      }
      result match {
        case Success(p: ScParameterizedType, _) => result
        case Success(tp: ScType, _) => {
          ScType.extractClassType(tp) match {
            case Some((clazz: PsiClass, subst: ScSubstitutor)) => {
              val tps = clazz.getTypeParameters
              //If parent Constructor and tps.length > 0 => we need localTypeInference
              val needLocalTypeInference = getContext match {
                case _: ScConstructor => true
                case _ => false
              }
              //if tps.length == 0 => this is SimpleType without Parameterizations.
              if (tps.length > 0) {
                val typez = ScParameterizedType(tp, tps.map({tp => new ScTypeParameterType(tp, subst)}))
                val undefSubst: ScSubstitutor = tps.foldLeft[ScSubstitutor](ScSubstitutor.empty) {
                  (subst, tp) => subst.bindT(tp.getName, ScUndefinedType(tp match {
                    case tp: ScTypeParam => new ScTypeParameterType(tp: ScTypeParam, subst)
                    case tp: PsiTypeParameter => new ScTypeParameterType(tp, subst)
                  }))
                }
                if (!needLocalTypeInference) lift(typez)
                else {
                  //ok, let's try to infer generic type
                  var result: TypeResult[ScType] = null
                  val argClauses= getContext match {
                    case constr: ScConstructor => constr.arguments.map(args => args.exprs).toList
                  } //todo: add implicit parameters
                  var filtered = clazz.getConstructors.filter(forFilter(_, subst.followed(undefSubst), argClauses, false))
                  val withImplicits = filtered.isEmpty
                  if (withImplicits) filtered = clazz.getConstructors.filter(forFilter(_, subst.followed(undefSubst), argClauses, true))
                  val applicable = filtered.map(forMap(_, subst.followed(undefSubst), argClauses, withImplicits, typez))
                  if (applicable.length == 0) return lift(typez)
                  val buff = new ArrayBuffer[ScSubstitutor]
                  def existsBetter(r: PsiMethod): Boolean = {
                    for ((r1,_) <- applicable if r != r1) {
                      if (isMoreSpecific(r1, r)) return true
                    }
                    false
                  }
                  for ((r, g) <- applicable if !existsBetter(r)) {
                    buff += g
                  }
                  if (buff.size != 1) return lift(typez)
                  else return lift(buff.apply(0).subst(typez))
                }
              }
              else result
            }
            case _ => result
          }
        }
        case _ => result
      }
    }
  }

  /**
   * Filtering if this constructor is ok for applying to this invocation
   */
  private def forFilter(m: PsiMethod, subst: ScSubstitutor, argClauses: List[Seq[ScExpression]],
                        checkWithImplicits: Boolean): Boolean = {
    Compatibility.compatible(m, subst, argClauses, checkWithImplicits)._1
  }

  /**
   * Getting right substitutor after local type inference
   */
  private def forMap(m: PsiMethod, subst: ScSubstitutor, argClauses: List[Seq[ScExpression]],
                        checkWithImplicits: Boolean, typez: ScType): (PsiMethod, ScSubstitutor) = {
    val expected: Option[ScType] = {
      getContext match {
        case constr: ScConstructor => {
          constr.getContext match {
            case par: ScClassParents => {
              par.getContext match {
                case ext: ScExtendsBlock => {
                  ext.getContext match {
                    case templ: ScNewTemplateDefinition => templ.expectedType
                    case _ => None
                  }
                }
                case _ => None
              }
            }
            case _ => None
          }
        }
        case _ => None
      }
    }
    val clazz = m.getContainingClass
    clazz match {
      case owner: ScTypeParametersOwner => {
        var s = Compatibility.compatible(m, subst, argClauses, checkWithImplicits)._2
        for (tParam <- owner.typeParameters) { //todo: think about view type bound
          s = s.addLower(tParam.getName, subst.subst(tParam.lowerBound.getOrElse(Nothing)))
          s = s.addUpper(tParam.getName, subst.subst(tParam.upperBound.getOrElse(Any)))
        }
        expected match {
          case Some(expected) => {
            if (Conformance.conforms(expected, subst.subst(typez))) {
              val uS = Conformance.undefinedSubst(expected, subst.subst(typez))
              s = s.addSubst(uS)
            }
          }
          case _ =>
        }
        s.getSubstitutor match {
          case Some(s) => (m, s)
          case None => (m, ScSubstitutor.empty)
        }
      }
      case owner: PsiTypeParameterListOwner => {
        var s = Compatibility.compatible(owner, subst, argClauses, checkWithImplicits)._2
        for (tParam <- owner.getTypeParameters) {
          s = s.addLower(tParam.getName, Nothing) //todo:
          s = s.addUpper(tParam.getName, Any) //todo:
        }
        expected match {
          case Some(expected) => {
            if (Conformance.conforms(expected, subst.subst(typez))) {
              val uS = Conformance.undefinedSubst(expected, subst.subst(typez))
              s = s.addSubst(uS)
            }
          }
          case _ =>
        }
        s.getSubstitutor match {
          case Some(s) => (m, s)
          case None => (m, ScSubstitutor.empty)
        }
      }
    }
  }

  /**
   * Can differentiate two method, which one of them is more specific to filter another one
   */
  private def isMoreSpecific(e1: PsiMethod, e2: PsiMethod): Boolean = {
    def getType(e: PsiMethod): ScType = e match {
      case f: ScFunction => f.getType(TypingContext.empty).getOrElse(Any)
      case m: PsiMethod => ResolveUtils.methodType(m, ScSubstitutor.empty)
    }
    (e1, e2, getType(e1), getType(e2)) match {
      case (e1, e2, ScFunctionType(ret1, params1), ScFunctionType(ret2, params2))
        if e1.isInstanceOf[PsiMethod] || e1.isInstanceOf[ScFun] => {
          val px = params1.zip(params2).map(p => Compatibility.compatible(p._2, p._1))
          val compt = px.foldLeft(true)((x: Boolean, z: Boolean) => x && z)
          compt && params1.length == params2.length
      }
      case (_, e2: PsiMethod, _, _) => true
      case _ => Compatibility.compatible(getType(e1), getType(e2))
    }
  }
}
