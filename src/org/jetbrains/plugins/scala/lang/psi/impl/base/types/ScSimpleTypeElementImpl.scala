package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScSuperReference, ScThisReference, ScUnderScoreSectionUtil}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

import scala.collection.immutable.HashMap

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleTypeElement {
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

  override def getTypeNoConstructor(ctx: TypingContext): TypeResult[ScType] = innerNonValueType(ctx, inferValueType = true, noConstructor = true)

  @CachedWithRecursionGuard[ScSimpleTypeElement](this, Failure("Recursive non value type of type element", Some(this)),
    ModCount.getBlockModificationCount)
  override def getNonValueType(ctx: TypingContext, withUnnecessaryImplicitsUpdate: Boolean = false): TypeResult[ScType] =
    innerNonValueType(ctx, inferValueType = false, withUnnecessaryImplicitsUpdate = withUnnecessaryImplicitsUpdate)


  private def innerNonValueType(ctx: TypingContext, inferValueType: Boolean, noConstructor: Boolean = false, withUnnecessaryImplicitsUpdate: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()
    val lift: (ScType) => Success[ScType] = Success(_, Some(this))

    def parametrise(tp: ScType, clazz: PsiClass, subst: ScSubstitutor): ScType = {
      if (clazz.getTypeParameters.isEmpty) {
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
          (fun.effectiveParameterClauses.map(_.effectiveParameters.map { p =>
            val paramType: ScType = subst.subst(p.getType(TypingContext.empty).getOrAny)
            new Parameter(p.name, p.deprecatedName, paramType, paramType, p.isDefaultParam,p.isRepeatedParameter,
              p.isCallByNameParameter, p.index, Some(p), p.getDefaultExpression.flatMap(_.getType().toOption))
          }),
            fun.parameterList.clauses.lastOption.exists(_.isImplicit))
        case f: ScPrimaryConstructor =>
          (f.effectiveParameterClauses.map(_.effectiveParameters.map { p =>
            val paramType: ScType = subst.subst(p.getType(TypingContext.empty).getOrAny)
            new Parameter(p.name, p.deprecatedName, paramType, paramType, p.isDefaultParam, p.isRepeatedParameter,
              p.isCallByNameParameter, p.index, Some(p), p.getDefaultExpression.flatMap(_.getType().toOption))
          }),
            f.parameterList.clauses.lastOption.exists(_.isImplicit))
        case m: PsiMethod =>
          (Seq(m.getParameterList.getParameters.map { p =>
            new Parameter("", None, p.exactParamType(), false, p.isVarArgs, false, p.index)
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
        val res = InferUtil.updateTypeWithImplicitParameters(newTp, this, None, withExpected, fullInfo = false)
        implicitParameters = res._2
        res._1
      } else tp
    }

    def typeForConstructor(ref: ScStableCodeReferenceElement, constr: PsiMethod,
                           _subst: ScSubstitutor, parentElement: PsiNamedElement): ScType = {
      val clazz = constr.containingClass
      val (constrTypParameters: Seq[ScTypeParam], constrSubst: ScSubstitutor) = parentElement match {
        case ta: ScTypeAliasDefinition => (Seq.empty, ScSubstitutor.empty)
        case s: ScTypeParametersOwner if s.typeParameters.nonEmpty =>
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
        case tp: ScTypeParametersOwner if constrTypParameters.nonEmpty =>
          constrTypParameters.map(new TypeParameter(_))
        case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
          tp.typeParameters.map(new TypeParameter(_))
        case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.nonEmpty =>
          ptp.getTypeParameters.toSeq.map(new TypeParameter(_))
        case _ =>
          updateImplicits(tp, withExpected = false, params = params, lastImplicit = lastImplicit)
          return res
      }

      getContext match {
        case p: ScParameterizedTypeElement =>
          val zipped = p.typeArgList.typeArgs.zip(typeParameters)
          val appSubst = new ScSubstitutor(new HashMap[(String, PsiElement), ScType] ++ zipped.map{
            case (arg, typeParam) =>
              ((typeParam.name, ScalaPsiUtil.getPsiElementId(typeParam.ptp)), arg.getType(TypingContext.empty).getOrAny)
          }, Map.empty, None)
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
            nonValueType = InferUtil.localTypeInference(nonValueType.internalType, params(i),
              c.arguments(i).exprs.map(new Expression(_)), nonValueType.typeParameters)
            i += 1
          }

          def lastClause(withExpected: Boolean) {
            c.expectedType match {
              case Some(expected) if withExpected =>
                def updateRes(expected: ScType) {
                  nonValueType = InferUtil.localTypeInference(nonValueType.internalType,
                    Seq(new Parameter("", None, expected, false, false, false, 0)),
                      Seq(new Expression(InferUtil.undefineSubstitutor(nonValueType.typeParameters).subst(res.inferValueType))),
                    nonValueType.typeParameters, shouldUndefineParameters = false, filterTypeParams = false) //here should work in different way:
                }
                val fromUnderscore = c.newTemplate match {
                  case Some(n) => ScUnderScoreSectionUtil.underscores(n).nonEmpty
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
              nonValueType = InferUtil.localTypeInference(nonValueType.internalType, params(i),
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
            case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
              tp.typeParameters.map(new TypeParameter(_))
            case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.nonEmpty =>
              ptp.getTypeParameters.toSeq.map(new TypeParameter(_))
            case _ => return (res, ScSubstitutor.empty)
          }

          val zipped = p.typeArgList.typeArgs.zip(typeParameters)
          val appSubst = new ScSubstitutor(new HashMap[(String, PsiElement), ScType] ++ zipped.map {
            case (arg, typeParam) =>
              ((typeParam.name, ScalaPsiUtil.getPsiElementId(typeParam.ptp)), arg.getType(TypingContext.empty).getOrAny)
          }, Map.empty, None)
          (appSubst.subst(res), appSubst)
        }
        val constrRef = ref.isConstructorReference && !noConstructor

        def updateImplicitsWithoutLocalTypeInference(r: TypeResult[ScType], ss: ScSubstitutor): TypeResult[ScType] = {
          if (withUnnecessaryImplicitsUpdate) {
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
          } else r
        }

        ref.resolveNoConstructor match {
          case Array(ScalaResolveResult(tp: PsiTypeParameter, _)) =>
            lift(ScalaPsiManager.typeVariable(tp))
          case Array(ScalaResolveResult(tvar: ScTypeVariableTypeElement, _)) =>
            lift(tvar.getType(TypingContext.empty).getOrAny)
          case Array(ScalaResolveResult(synth: ScSyntheticClass, _)) =>
            lift(synth.t)
          case Array(ScalaResolveResult(to: ScTypeParametersOwner, subst: ScSubstitutor))
            if constrRef && to.isInstanceOf[PsiNamedElement] &&
              (to.typeParameters.isEmpty || getContext.isInstanceOf[ScParameterizedTypeElement]) =>
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
              (to.getTypeParameters.isEmpty || getContext.isInstanceOf[ScParameterizedTypeElement]) =>
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
              (to.typeParameters.isEmpty || ref.getContext.isInstanceOf[ScParameterizedTypeElement]) => Some(r)
          case Array(r@ScalaResolveResult(to: PsiTypeParameterListOwner, subst: ScSubstitutor))
            if to.isInstanceOf[PsiNamedElement] &&
              (to.getTypeParameters.isEmpty || ref.getContext.isInstanceOf[ScParameterizedTypeElement]) => Some(r)
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
              fromType match {
                case Some(ScDesignatorType(obj: ScObject)) if obj.isPackageObject =>
                  Success(ScProjectionType(ScDesignatorType(obj), resolvedElement,
                    superReference = false), Some(ref))
                case _ =>
                  Success(ScType.designator(resolvedElement), Some(ref))
              }
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
            Success(ScProjectionType(ScThisType(template), resolvedElement, resolvedElement.isInstanceOf[PsiClass]), Some(ref))
          case _ =>
            resolvedElement match {
              case self: ScSelfTypeElement =>
                val td = PsiTreeUtil.getContextOfType(self, true, classOf[ScTemplateDefinition])
                Success(ScThisType(td), Some(ref))
              case _ =>
                if (fromType.isEmpty) return Success(ScType.designator(resolvedElement), Some(ref))
                Success(ScProjectionType(fromType.get, resolvedElement, superReference = false), Some(ref))
            }
        }
    }
  }
}
