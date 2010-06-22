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
import nonvalue.{ScTypePolymorphicType, ScMethodType}
import psi.impl.toplevel.synthetic.ScSyntheticClass
import collection.Set
import result.{Failure, TypeResult, Success, TypingContext}
import scala.None
import collection.mutable.ArrayBuffer
import api.base.patterns.ScReferencePattern
import lang.resolve.{ResolveUtils, ScalaResolveResult}
import util.PsiTreeUtil
import api.statements._
import api.toplevel.{ScTypeParametersOwner, ScTypedDefinition, ScNamedElement, ScPolymorphicElement}
import params.ScTypeParam
import api.expr.{ScNewTemplateDefinition, ScExpression}
import api.toplevel.templates.{ScExtendsBlock, ScClassParents}
import psi.types.Compatibility.Expression
import lang.resolve.processor.MostSpecificUtil
import api.base.{ScPrimaryConstructor, ScConstructor, ScReferenceElement}
import api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportWildcardSelectorUsed}
import api.toplevel.imports.ScImportExpr
import api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}

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
          //todo: rework alias typees
          case ScalaResolveResult(aliasDef: ScTypeAliasDefinition, s) => {
            if (aliasDef.typeParameters.length == 0) aliasDef.aliasedType(ctx) map {t => s.subst(t)}
            else lift(new ScTypeConstructorType(aliasDef, s))
          }
          case ScalaResolveResult(synth: ScSyntheticClass, _) => lift(synth.t)
          case r: ScalaResolveResult => {
            //todo: scalap hack
            if (r.isHacked) {
              q.bind match {
                case Some(ScalaResolveResult(obj: ScObject, subst)) => {
                  ScalaPsiUtil.getCompanionModule(obj) match {
                    case Some(clazz) => Success(ScProjectionType(ScDesignatorType(clazz), ref), Some(this))
                    case _ => Success(ScProjectionType(new ScSingletonType(q), ref), Some(this))
                  }
                }
                case _ => Success(ScProjectionType(new ScSingletonType(q), ref), Some(this))
              }
            } else
              Success(ScProjectionType(new ScSingletonType(q), ref), Some(this))
          }
        }
        case None => wrap(ref.bind) flatMap {
          case r@ScalaResolveResult(e, s) => e match {
            //todo: rework alias types
            case aliasDef: ScTypeAliasDefinition =>
              if (aliasDef.typeParameters.length == 0) aliasDef.aliasedType(ctx) map {t => s.subst(t)}
              else lift(new ScTypeConstructorType(aliasDef, s))
            case alias: ScTypeAliasDeclaration => lift(new ScTypeAliasType(alias, s))

            case tp: PsiTypeParameter => lift(ScalaPsiManager.typeVariable(tp))
            case synth: ScSyntheticClass => lift(synth.t)

            case null => lift(Any)
            case _ => {
              val clazz = r.boundClass
              if (clazz != null) {
                lift(ScProjectionType(ScDesignatorType(clazz), ref))
              } else lift(ScDesignatorType(e))
            }
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
        case Success(j: JavaArrayType, _) => result
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
                  (subst, tp) => subst.bindT((tp.getName, ScalaPsiUtil.getPsiElementId(tp)), ScUndefinedType(tp match {
                    case tp: ScTypeParam => new ScTypeParameterType(tp: ScTypeParam, subst)
                    case tp: PsiTypeParameter => new ScTypeParameterType(tp, subst)
                  }))
                }
                if (!needLocalTypeInference) lift(typez)
                else {
                  //ok, let's try to infer generic type
                  var result: TypeResult[ScType] = null
                  val argClauses = getContext match {
                    case constr: ScConstructor => constr.arguments.map(args => args.exprs).toList
                  }
                  def forFilter(m: PsiMethod, checkWithImplicits: Boolean): Boolean = {
                    Compatibility.compatible(m, subst.followed(undefSubst),
                      argClauses.map(_.map(Compatibility.Expression(_))),
                      checkWithImplicits, getResolveScope).problems.isEmpty
                  }
                  var filtered = clazz.getConstructors.filter(forFilter(_, false))
                  val withImplicits = filtered.isEmpty
                  if (withImplicits) filtered = clazz.getConstructors.filter(forFilter(_,  true))
                  val applicable = filtered
                  val method = if (applicable.isEmpty) return lift(typez)
                  else {
                    MostSpecificUtil(this, if (argClauses.isEmpty) 0 else argClauses(0).length).
                            mostSpecificForPsiMethod(applicable.toSet) match {
                      case Some(r) => r
                      case None => return lift(typez)
                    }
                  }
                  var resType = method match {
                    case fun: ScFunction => subst.subst(fun.polymorphicType(Some(typez)))
                    case pr: ScPrimaryConstructor => subst.subst(pr.polymorphicType)
                    case method: PsiMethod =>
                      ResolveUtils.javaPolymorphicType(method, subst, getResolveScope, Some(typez))
                  }
                  val iterator = argClauses.iterator
                  while (iterator.hasNext) {
                    val argumentExpressions = iterator.next
                    resType match {
                      case ScFunctionType(retType: ScType, params: Seq[ScType]) => resType = retType
                      case ScMethodType(retType, _, _) => resType = retType
                      case ScTypePolymorphicType(ScMethodType(retType, params, _), typeParams) => {
                        val exprs: Seq[Expression] = argumentExpressions.map(expr => new Expression(expr))
                        resType = ScalaPsiUtil.localTypeInference(retType, params, exprs, typeParams)
                      }
                    }
                  }

                  //todo: add implicit parameters
                  return lift(resType)
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
}
