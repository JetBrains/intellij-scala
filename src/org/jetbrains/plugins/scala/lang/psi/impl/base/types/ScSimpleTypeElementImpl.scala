package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import org.jetbrains.plugins.scala.lang.psi.types
import com.intellij.lang.ASTNode
import com.intellij.psi._
import api.base.types._
import psi.ScalaPsiElementImpl
import lexer.ScalaTokenTypes
import psi.types._
import nonvalue.{ScMethodType, ScTypePolymorphicType, TypeParameter, Parameter}
import psi.impl.toplevel.synthetic.ScSyntheticClass
import result.{Failure, TypeResult, Success, TypingContext}
import scala.None
import api.statements._
import api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import psi.types.Compatibility.Expression
import collection.immutable.HashMap
import api.expr.{ScSuperReference, ScThisReference, ScUnderScoreSectionUtil}
import lang.resolve.ScalaResolveResult
import api.base._
import caches.CachesUtil
import util.{PsiTreeUtil, PsiModificationTracker}
import psi.ScalaPsiUtil.SafeCheckException
import api.{InferUtil, ScalaElementVisitor}
import org.jetbrains.plugins.scala.extensions.{PsiParameterExt, toPsiMemberExt, toSeqExt, toPsiNamedElementExt}
import com.intellij.openapi.progress.ProgressManager
import api.toplevel.typedef.{ScObject, ScTemplateDefinition}

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleTypeElement {
  override def toString: String = "SimpleTypeElement: " + getText

  def singleton = getNode.findChildByType(ScalaTokenTypes.kTYPE) != null

  def findConstructor: Option[ScConstructor] = {
    getContext match {
      case constr: ScConstructor => Some(constr)
      case param: ScParameterizedTypeElement =>
        param.getContext match {
          case constr: ScConstructor => Some(constr)
          case _ => None
        }
      case _ => None
    }
  }

  protected def innerType(ctx: TypingContext): TypeResult[ScType] = innerNonValueType(ctx, inferValueType = true)

  override def getTypeNoConstructor(ctx: TypingContext): TypeResult[ScType] = {
    CachesUtil.getWithRecursionPreventingWithRollback(this, CachesUtil.SIMPLE_TYPE_ELEMENT_TYPE_NO_CONSTRUCTOR_KEY,
      new CachesUtil.MyProvider[ScSimpleTypeElement, TypeResult[ScType]](
        this, elem => innerNonValueType(ctx, inferValueType = true, noConstructor = true)
      )(PsiModificationTracker.MODIFICATION_COUNT), Failure("Recursive type of type element", Some(this)))
  }

  override def getNonValueType(ctx: TypingContext): TypeResult[ScType] = {
    CachesUtil.getWithRecursionPreventingWithRollback(this, CachesUtil.NON_VALUE_TYPE_ELEMENT_TYPE_KEY,
        new CachesUtil.MyProvider[ScSimpleTypeElementImpl, TypeResult[ScType]](
        this, elem => elem.innerNonValueType(ctx, inferValueType = false)
      )(PsiModificationTracker.MODIFICATION_COUNT), Failure("Recursive non value type of type element", Some(this)))
  }

  private def innerNonValueType(ctx: TypingContext, inferValueType: Boolean, noConstructor: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()
    val lift: (ScType) => Success[ScType] = Success(_, Some(this))

    def parametrise(tp: ScType, clazz: PsiClass, subst: ScSubstitutor): ScType = {
      if (clazz.getTypeParameters.length == 0) {
        tp
      } else {
        ScParameterizedType(tp, clazz.getTypeParameters.map {
          case tp: ScTypeParam => new ScTypeParameterType(tp, subst)
          case ptp             => new ScTypeParameterType(ptp, subst)
        })
      }
    }

    def getConstructorParams(constr: PsiMethod, subst: ScSubstitutor): (Seq[Seq[Parameter]], Boolean) = {
      constr match {
        case fun: ScFunction =>
          (fun.effectiveParameterClauses.map(_.parameters.mapWithIndex {
            case (p, index) =>
              val paramType: ScType = subst.subst(p.getType(TypingContext.empty).getOrAny)
              new Parameter(p.name, p.deprecatedName,
                paramType, paramType, p.isDefaultParam,
                p.isRepeatedParameter, p.isCallByNameParameter, index, Some(p))
          }),
            fun.parameterList.clauses.lastOption.exists(_.isImplicit))
        case f: ScPrimaryConstructor =>
          (f.effectiveParameterClauses.map(_.parameters.mapWithIndex {
            case (p, index) =>
              val paramType: ScType = subst.subst(p.getType(TypingContext.empty).getOrAny)
              new Parameter(p.name, p.deprecatedName,
                paramType, paramType, p.isDefaultParam,
                p.isRepeatedParameter, p.isCallByNameParameter, index, Some(p))
          }),
            f.parameterList.clauses.lastOption.exists(_.isImplicit))
        case m: PsiMethod =>
          (Seq(m.getParameterList.getParameters.toSeq.mapWithIndex {
            case (p, index) => new Parameter("", None, p.paramType, false, p.isVarArgs, false, index)
          }), false)
      }
    }

    def updateImplicits(tp: ScType, withExpected: Boolean, params: Seq[Seq[Parameter]], lastImplicit: Boolean): ScType = {
      if (lastImplicit) {
        //Let's add implicit parameters
        val newTp = tp match {
          case ScTypePolymorphicType(i, p) =>
            ScTypePolymorphicType(ScMethodType(i, params.last, isImplicit = true)(getProject, getResolveScope), p)
          case _ => ScMethodType(tp, params.last, isImplicit = true)(getProject, getResolveScope)
        }
        val res = InferUtil.updateTypeWithImplicitParameters(newTp, this, None, withExpected)
        implicitParameters = res._2
        res._1
      } else tp
    }

    def typeForConstructor(ref: ScStableCodeReferenceElement, constr: PsiMethod,
                           _subst: ScSubstitutor, parentElement: PsiNamedElement): ScType = {
      val clazz = constr.containingClass
      val (constrTypParameters: Seq[ScTypeParam], constrSubst: ScSubstitutor) = parentElement match {
        case ta: ScTypeAliasDefinition => (Seq.empty, ScSubstitutor.empty)
        case s: ScTypeParametersOwner if s.typeParameters.length > 0 =>
          constr match {
            case method: ScMethodLike =>
              val params = method.getConstructorTypeParameters.map(_.typeParameters).getOrElse(Seq.empty)
              val subst = new ScSubstitutor(s.typeParameters.zip(params).map {
                case (tpClass: ScTypeParam, tpConstr: ScTypeParam) =>
                  ((tpClass.name, ScalaPsiUtil.getPsiElementId(tpClass)),
                    new ScTypeParameterType(tpConstr, ScSubstitutor.empty))
              }.toMap, Map.empty, None)
              (params, subst)
            case _ => (Seq.empty, ScSubstitutor.empty)
          }
        case _ => (Seq.empty, ScSubstitutor.empty)
      }
      val subst = _subst followed constrSubst
      val tp = parentElement match {
        case ta: ScTypeAliasDefinition =>
          ta.aliasedType.getOrElse(return types.Nothing)
        case _ =>
          parametrise(ScSimpleTypeElementImpl.calculateReferenceType(ref, shapesOnly = false).
            getOrElse(return types.Nothing), clazz, subst)
      }
      val res = subst.subst(tp)

      val (params: Seq[Seq[Parameter]], lastImplicit: Boolean) = getConstructorParams(constr, subst)

      val typeParameters: Seq[TypeParameter] = parentElement match {
        case tp: ScTypeParametersOwner if constrTypParameters.length > 0 =>
          constrTypParameters.map(tp => new TypeParameter(tp.name,
                tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp))
        case tp: ScTypeParametersOwner if tp.typeParameters.length > 0 =>
          tp.typeParameters.map(tp => new TypeParameter(tp.name,
                tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp))
        case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.length > 0 =>
          ptp.getTypeParameters.toSeq.map(ptp => new TypeParameter(ptp.name,
            types.Nothing, types.Any, ptp)) //todo: add lower and upper bound
        case _ =>
          updateImplicits(tp, withExpected = false, params = params, lastImplicit = lastImplicit)
          return res
      }

      getContext match {
        case p: ScParameterizedTypeElement =>
          val zipped = p.typeArgList.typeArgs.zip(typeParameters)
          val appSubst = new ScSubstitutor(new HashMap[(String, String), ScType] ++ zipped.map{case (arg, typeParam) =>
            ((typeParam.name, ScalaPsiUtil.getPsiElementId(typeParam.ptp)),
              arg.getType(TypingContext.empty).getOrAny
              )},
            Map.empty, None)
          val newRes = appSubst.subst(res)
          updateImplicits(newRes, withExpected = false, params = params, lastImplicit = lastImplicit)
          return newRes
        case _ =>
      }

      findConstructor match {
        case Some(c) =>
          var nonValueType = ScTypePolymorphicType(res, typeParameters)
          var i = 0
          //We need to update type info for generics in the following order:
          //1. All clauses without last params clause or last arguments clause
          //2. According to expected type
          //3. Last argument clause
          //4. Implicit clauses if applicable
          //5. In case of SafeCheckException return to 3 to complete update without expected type
          while (i < params.length - 1 && i < c.arguments.length - 1) {
            nonValueType = ScalaPsiUtil.localTypeInference(nonValueType.internalType, params(i),
              c.arguments(i).exprs.map(new Expression(_)), nonValueType.typeParameters)
            i += 1
          }

          def lastClause(withExpected: Boolean) {
            c.expectedType match {
              case Some(expected) if withExpected =>
                def updateRes(expected: ScType) {
                  nonValueType = ScalaPsiUtil.localTypeInference(nonValueType.internalType,
                    Seq(new Parameter("", None, expected, false, false, false, 0)),
                      Seq(new Expression(ScalaPsiUtil.undefineSubstitutor(nonValueType.typeParameters).subst(res.inferValueType))),
                    nonValueType.typeParameters, shouldUndefineParameters = false, filterTypeParams = false) //here should work in different way:
                }
                val fromUnderscore = c.newTemplate match {
                  case Some(n) => ScUnderScoreSectionUtil.underscores(n).length != 0
                  case None => false
                }
                if (!fromUnderscore) {
                  updateRes(expected)
                } else {
                  expected match {
                    case ScFunctionType(retType, _) => updateRes(retType)
                    case _ => //do not update res, we haven't expected type
                  }
                }
              case _ =>
            }

            //last clause after expected types
            if (i < params.length && i < c.arguments.length) {
              nonValueType = ScalaPsiUtil.localTypeInference(nonValueType.internalType, params(i),
                c.arguments(i).exprs.map(new Expression(_)), nonValueType.typeParameters, safeCheck = withExpected)
              i += 1
            }

            if (lastImplicit && i < params.length) {
              //Let's add implicit parameters
              updateImplicits(nonValueType, withExpected, params, lastImplicit) match {
                case t: ScTypePolymorphicType => nonValueType = t
                case _ =>
              }
            }
          }

          val oldNonValueType = nonValueType
          try {
            lastClause(withExpected = true)
          } catch {
            case e: SafeCheckException =>
              nonValueType = oldNonValueType
              lastClause(withExpected = false)
          }

          if (inferValueType) {
            val pts = nonValueType match {
              case t: ScTypePolymorphicType => t.polymorphicTypeSubstitutor
              case _ => ScSubstitutor.empty
            }
            pts.subst(nonValueType.internalType)
          } else nonValueType
        case None => res
      }
    }

    reference match {
      case Some(ref) =>
        def updateForParameterized(subst: ScSubstitutor, elem: PsiNamedElement,
                                    p: ScParameterizedTypeElement): (ScType, ScSubstitutor) = {
          val tp = elem match {
            case ta: ScTypeAliasDefinition =>
              ta.aliasedType.getOrElse(return (types.Nothing, ScSubstitutor.empty))
            case clazz: PsiClass =>
              parametrise(ScSimpleTypeElementImpl.calculateReferenceType(ref, shapesOnly = false).
                getOrElse(return (types.Nothing, ScSubstitutor.empty)), clazz, subst)
          }
          val res = subst.subst(tp)
          val typeParameters: Seq[TypeParameter] = elem match {
            case tp: ScTypeParametersOwner if tp.typeParameters.length > 0 =>
              tp.typeParameters.map(tp => new TypeParameter(tp.name,
                    tp.lowerBound.getOrNothing, tp.upperBound.getOrAny, tp))
            case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.length > 0 =>
              ptp.getTypeParameters.toSeq.map(ptp => new TypeParameter(ptp.name,
                types.Nothing, types.Any, ptp)) //todo: add lower and upper bound
            case _ => return (res, ScSubstitutor.empty)
          }

          val zipped = p.typeArgList.typeArgs.zip(typeParameters)
          val appSubst = new ScSubstitutor(new HashMap[(String, String), ScType] ++ zipped.map { case (arg, typeParam) =>
            ((typeParam.name, ScalaPsiUtil.getPsiElementId(typeParam.ptp)),
                    arg.getType(TypingContext.empty).getOrAny)
          },
            Map.empty, None)
          (appSubst.subst(res), appSubst)
        }
        val constrRef = ref.isConstructorReference && !noConstructor

        def updateImplicitsWithoutLocalTypeInference(r: TypeResult[ScType], ss: ScSubstitutor): TypeResult[ScType] = {
          r.map {
            tp =>
              ref.bind() match {
                case Some(r@ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor)) =>
                  val (params, lastImplicit) = getConstructorParams(method, subst.followed(ss))
                  updateImplicits(tp, withExpected = false, params = params, lastImplicit = lastImplicit)
                  tp
                case _ => tp
              }
          }
        }

        ref.resolveNoConstructor match {
          case Array(ScalaResolveResult(tp: PsiTypeParameter, _)) =>
            lift(ScalaPsiManager.typeVariable(tp))
          case Array(ScalaResolveResult(synth: ScSyntheticClass, _)) =>
            lift(synth.t)
          case Array(ScalaResolveResult(to: ScTypeParametersOwner, subst: ScSubstitutor))
            if constrRef && to.isInstanceOf[PsiNamedElement] &&
              (to.typeParameters.length == 0 || getContext.isInstanceOf[ScParameterizedTypeElement]) =>
            val (tp, ss) = getContext match {
              case p: ScParameterizedTypeElement if !to.isInstanceOf[ScTypeAliasDeclaration] =>
                val (parameterized, ss) = updateForParameterized(subst, to.asInstanceOf[PsiNamedElement], p)
                (Success(parameterized, Some(this)), ss)
              case _ =>
                (ScSimpleTypeElementImpl.calculateReferenceType(ref, shapesOnly = false), ScSubstitutor.empty)
            }
            updateImplicitsWithoutLocalTypeInference(tp, ss)
          case Array(ScalaResolveResult(to: PsiTypeParameterListOwner, subst: ScSubstitutor))
            if constrRef && to.isInstanceOf[PsiNamedElement] &&
              (to.getTypeParameters.length == 0 || getContext.isInstanceOf[ScParameterizedTypeElement]) =>
            val (result, ss) = getContext match {
              case p: ScParameterizedTypeElement if !to.isInstanceOf[ScTypeAliasDeclaration] =>
                val (parameterized, ss) = updateForParameterized(subst, to.asInstanceOf[PsiNamedElement], p)
                (Success(parameterized, Some(this)), ss)
              case _ =>
                (ScSimpleTypeElementImpl.calculateReferenceType(ref, shapesOnly = false), ScSubstitutor.empty)
            }
            updateImplicitsWithoutLocalTypeInference(result, ss)
          case _ => //resolve constructor with local type inference
            ref.bind() match {
              case Some(r@ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor)) if !noConstructor =>
                Success(typeForConstructor(ref, method, subst, r.getActualElement), Some(this))
              case _ => ScSimpleTypeElementImpl.calculateReferenceType(ref, shapesOnly = false)
            }
        }
      case None => ScSimpleTypeElementImpl.calculateReferenceType(pathElement, shapesOnly = false)
    }
  }

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitSimpleTypeElement(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case s: ScalaElementVisitor => s.visitSimpleTypeElement(this)
      case _ => super.accept(visitor)
    }
  }
}

object ScSimpleTypeElementImpl {
  def calculateReferenceType(path: ScPathElement, shapesOnly: Boolean): TypeResult[ScType] = {
    path match {
      case ref: ScStableCodeReferenceElement => calculateReferenceType(ref, shapesOnly)
      case thisRef: ScThisReference =>
        thisRef.refTemplate match {
          case Some(template) =>
            Success(ScThisType(template), Some(path))
          case _ => Failure("Cannot find template for this reference", Some(thisRef))
        }
      case superRef: ScSuperReference =>
        val template = superRef.drvTemplate.getOrElse(
            return Failure("Cannot find enclosing container", Some(superRef))
          )
        Success(ScThisType(template), Some(path))
    }
  }

  def calculateReferenceType(ref: ScStableCodeReferenceElement, shapesOnly: Boolean): TypeResult[ScType] = {
    val (resolvedElement, fromType) = (if (!shapesOnly) {
      if (ref.isConstructorReference) {
        ref.resolveNoConstructor match {
          case Array(r@ScalaResolveResult(to: ScTypeParametersOwner, subst: ScSubstitutor))
            if to.isInstanceOf[PsiNamedElement] &&
              (to.typeParameters.length == 0 || ref.getContext.isInstanceOf[ScParameterizedTypeElement]) => Some(r)
          case Array(r@ScalaResolveResult(to: PsiTypeParameterListOwner, subst: ScSubstitutor))
            if to.isInstanceOf[PsiNamedElement] &&
              (to.getTypeParameters.length == 0 || ref.getContext.isInstanceOf[ScParameterizedTypeElement]) => Some(r)
          case _ => ref.bind()
        }
      } else ref.bind()
    } else {
      ref.shapeResolve match {
        case Array(r: ScalaResolveResult) => Some(r)
        case _ => None
      }
    }) match {
      case Some(r@ScalaResolveResult(n: PsiMethod, _)) if n.isConstructor =>
        (n.containingClass, r.fromType)
      case Some(r@ScalaResolveResult(n: PsiNamedElement, _)) => (n, r.fromType)
      case _ => return Failure("Cannot resolve reference", Some(ref))
    }
    ref.qualifier match {
      case Some(qual) =>
        qual.resolve() match {
          case pack: PsiPackage =>
            val obj = PsiTreeUtil.getContextOfType(resolvedElement, classOf[ScObject])
            if (obj != null && obj.isPackageObject) {
              Success(ScProjectionType(ScDesignatorType(obj), resolvedElement,
                superReference = false), Some(ref))
            } else {
              Success(ScType.designator(resolvedElement), Some(ref))
            }
          case _ =>
            calculateReferenceType(qual, shapesOnly) match {
              case failure: Failure => failure
              case Success(tp, _) =>
                Success(ScProjectionType(tp, resolvedElement, superReference = false), Some(ref))
            }
        }
      case None =>
        ref.pathQualifier match {
          case Some(thisRef: ScThisReference) =>
            thisRef.refTemplate match {
              case Some(template) =>
                Success(ScProjectionType(ScThisType(template), resolvedElement, superReference = false), Some(ref))
              case _ => Failure("Cannot find template for this reference", Some(thisRef))
            }
          case Some(superRef: ScSuperReference) =>
            val template = superRef.drvTemplate match {
              case Some(x) => x
              case None => return Failure("Cannot find enclosing container", Some(superRef))
            }
            Success(new ScProjectionType(ScThisType(template), resolvedElement, resolvedElement.isInstanceOf[PsiClass]), Some(ref))
          case _ =>
            resolvedElement match {
              case param: ScParameter if !param.isVal =>
                Success(ScDesignatorType(param), Some(ref))
              case self: ScSelfTypeElement =>
                val td = PsiTreeUtil.getContextOfType(self, true, classOf[ScTemplateDefinition])
                Success(ScThisType(td), Some(ref))
              case _ =>
                if (fromType == None) return Success(ScType.designator(resolvedElement), Some(ref))
                Success(ScProjectionType(fromType.get, resolvedElement, superReference = false), Some(ref))
            }
        }
    }
  }
}