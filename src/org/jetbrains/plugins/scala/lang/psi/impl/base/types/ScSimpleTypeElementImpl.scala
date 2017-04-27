package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package types

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScSuperReference, ScThisReference, ScUnderScoreSectionUtil}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{PsiTypeParameterExt, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, Nothing, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.result.{Failure, Success, TypeResult, TypingContext}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleTypeElement {
  protected def innerType(ctx: TypingContext): TypeResult[ScType] = innerNonValueType(ctx, inferValueType = true)

  override def getTypeNoConstructor(ctx: TypingContext): TypeResult[ScType] = innerNonValueType(ctx, inferValueType = true, noConstructor = true)

  @CachedWithRecursionGuard(this, Failure("Recursive non value type of type element", Some(this)),
    ModCount.getBlockModificationCount)
  override def getNonValueType(ctx: TypingContext, withUnnecessaryImplicitsUpdate: Boolean = false): TypeResult[ScType] =
    innerNonValueType(ctx, inferValueType = false, withUnnecessaryImplicitsUpdate = withUnnecessaryImplicitsUpdate)


  private def innerNonValueType(ctx: TypingContext, inferValueType: Boolean, noConstructor: Boolean = false, withUnnecessaryImplicitsUpdate: Boolean = false): TypeResult[ScType] = {
    ProgressManager.checkCanceled()

    def parametrise(tp: ScType, clazz: PsiClass, subst: ScSubstitutor): ScType = {
      if (clazz.getTypeParameters.isEmpty) {
        tp
      } else {
        ScParameterizedType(tp, clazz.getTypeParameters.map(TypeParameterType(_, Some(subst))))
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
          (Seq(m.parameters.map { p =>
            Parameter(p.paramType(), isRepeated = p.isVarArgs, index = p.index)
          }), false)
      }
    }

    def updateImplicits(tp: ScType, withExpected: Boolean, params: Seq[Seq[Parameter]], lastImplicit: Boolean): ScType = {
      if (lastImplicit) {
        //Let's add implicit parameters
        val newTp = tp match {
          case ScTypePolymorphicType(i, p) =>
            ScTypePolymorphicType(ScMethodType(i, params.last, isImplicit = true), p)
          case _ => ScMethodType(tp, params.last, isImplicit = true)
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
        case _: ScTypeAliasDefinition => (Seq.empty, ScSubstitutor.empty)
        case s: ScTypeParametersOwner if s.typeParameters.nonEmpty =>
          constr match {
            case method: ScMethodLike =>
              val params = method.getConstructorTypeParameters.map(_.typeParameters).getOrElse(Seq.empty)
              val subst = ScSubstitutor(s.typeParameters.zip(params).map {
                case (tpClass: ScTypeParam, tpConstr: ScTypeParam) =>
                  (tpClass.nameAndId, TypeParameterType(tpConstr))
              }.toMap)
              (params, subst)
            case _ => (Seq.empty, ScSubstitutor.empty)
          }
        case _ => (Seq.empty, ScSubstitutor.empty)
      }
      val subst = _subst followed constrSubst
      val tp = parentElement match {
        case ta: ScTypeAliasDefinition =>
          ta.aliasedType.getOrElse(return Nothing)
        case _ =>
          parametrise(calculateReferenceType(ref).
            getOrElse(return Nothing), clazz, subst)
      }
      val res = subst.subst(tp)

      val (params: Seq[Seq[Parameter]], lastImplicit: Boolean) = getConstructorParams(constr, subst)

      val typeParameters: Seq[TypeParameter] = parentElement match {
        case _: ScTypeParametersOwner if constrTypParameters.nonEmpty =>
          constrTypParameters.map(TypeParameter(_))
        case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
          tp.typeParameters.map(TypeParameter(_))
        case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.nonEmpty =>
          ptp.getTypeParameters.toSeq.map(TypeParameter(_))
        case _ =>
          updateImplicits(tp, withExpected = false, params = params, lastImplicit = lastImplicit)
          return res
      }

      getContext match {
        case p: ScParameterizedTypeElement =>
          val zipped = p.typeArgList.typeArgs.zip(typeParameters)
          val appSubst = ScSubstitutor(zipped.map {
            case (arg, typeParam) =>
              (typeParam.nameAndId, arg.getType(TypingContext.empty).getOrAny)
          }.toMap)
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
                    Seq(Parameter(expected, isRepeated = false, index = 0)),
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
                    case FunctionType(retType, _) => updateRes(retType)
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
            case _: SafeCheckException =>
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
              ta.aliasedType.getOrElse(return (Nothing, ScSubstitutor.empty))
            case clazz: PsiClass =>
              parametrise(calculateReferenceType(ref).
                getOrElse(return (Nothing, ScSubstitutor.empty)), clazz, subst)
          }
          val res = subst.subst(tp)
          val typeParameters: Seq[TypeParameter] = elem match {
            case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
              tp.typeParameters.map(TypeParameter(_))
            case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.nonEmpty =>
              ptp.getTypeParameters.toSeq.map(TypeParameter(_))
            case _ => return (res, ScSubstitutor.empty)
          }

          val zipped = p.typeArgList.typeArgs.zip(typeParameters)
          val appSubst = ScSubstitutor(zipped.map {
            case (arg, typeParam) =>
              (typeParam.nameAndId, arg.getType(TypingContext.empty).getOrAny)
          }.toMap)
          (appSubst.subst(res), appSubst)
        }
        val constrRef = ref.isConstructorReference && !noConstructor

        def updateImplicitsWithoutLocalTypeInference(r: TypeResult[ScType], ss: ScSubstitutor): TypeResult[ScType] = {
          if (withUnnecessaryImplicitsUpdate) {
            r.map {
              tp =>
                ref.bind() match {
                  case Some(ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor)) =>
                    val (params, lastImplicit) = getConstructorParams(method, subst.followed(ss))
                    updateImplicits(tp, withExpected = false, params = params, lastImplicit = lastImplicit)
                    tp
                  case _ => tp
                }
            }
          } else r
        }

        ref.resolveNoConstructor match {
          case Array(ScalaResolveResult(psiTypeParameter: PsiTypeParameter, _)) =>
            this.success(TypeParameterType(psiTypeParameter, None))
          case Array(ScalaResolveResult(tvar: ScTypeVariableTypeElement, _)) =>
            this.success(tvar.getType().getOrAny)
          case Array(ScalaResolveResult(synth: ScSyntheticClass, _)) =>
            this.success(synth.stdType)
          case Array(ScalaResolveResult(to: ScTypeParametersOwner, subst: ScSubstitutor))
            if constrRef && to.isInstanceOf[PsiNamedElement] &&
              (to.typeParameters.isEmpty || getContext.isInstanceOf[ScParameterizedTypeElement]) =>
            val (tp, ss) = getContext match {
              case p: ScParameterizedTypeElement if !to.isInstanceOf[ScTypeAliasDeclaration] =>
                val (parameterized, ss) = updateForParameterized(subst, to.asInstanceOf[PsiNamedElement], p)
                (this.success(parameterized), ss)
              case _ =>
                (calculateReferenceType(ref), ScSubstitutor.empty)
            }
            updateImplicitsWithoutLocalTypeInference(tp, ss)
          case Array(ScalaResolveResult(to: PsiTypeParameterListOwner, subst: ScSubstitutor))
            if constrRef && to.isInstanceOf[PsiNamedElement] &&
              (to.getTypeParameters.isEmpty || getContext.isInstanceOf[ScParameterizedTypeElement]) =>
            val (result, ss) = getContext match {
              case p: ScParameterizedTypeElement if !to.isInstanceOf[ScTypeAliasDeclaration] =>
                val (parameterized, ss) = updateForParameterized(subst, to.asInstanceOf[PsiNamedElement], p)
                (this.success(parameterized), ss)
              case _ =>
                (calculateReferenceType(ref), ScSubstitutor.empty)
            }
            updateImplicitsWithoutLocalTypeInference(result, ss)
          case _ => //resolve constructor with local type inference
            ref.bind() match {
              case Some(r@ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor)) if !noConstructor =>
                this.success(typeForConstructor(ref, method, subst, r.getActualElement))
              case Some(ScalaResolveResult(ta: ScTypeAlias, _: ScSubstitutor)) if ta.isExistentialTypeAlias =>
                this.success(ScExistentialArgument(ta.name, ta.typeParameters.map(TypeParameterType(_, None)).toList,
                  ta.lowerBound.getOrNothing, ta.upperBound.getOrAny))
              case _ => calculateReferenceType(ref, shapesOnly = false)
            }
        }
      case None => pathElement match {
        case ref: ScStableCodeReferenceElement => calculateReferenceType(ref)
        case thisRef: ScThisReference => fromThisReference(thisRef, ScThisType)()
        case superRef: ScSuperReference => fromSuperReference(superRef, ScThisType)()
      }
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

  def calculateReferenceType(ref: ScStableCodeReferenceElement, shapesOnly: Boolean = false): TypeResult[ScType] = {
    import ref.projectContext

    val (resolvedElement, fromType) = (if (!shapesOnly) {
      if (ref.isConstructorReference) {
        ref.resolveNoConstructor match {
          case Array(r@ScalaResolveResult(to: ScTypeParametersOwner, _: ScSubstitutor))
            if to.isInstanceOf[PsiNamedElement] &&
              (to.typeParameters.isEmpty || ref.getContext.isInstanceOf[ScParameterizedTypeElement]) => Some(r)
          case Array(r@ScalaResolveResult(to: PsiTypeParameterListOwner, _: ScSubstitutor))
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

    def makeProjection(`type`: ScType, superReference: Boolean = false) =
      ScProjectionType(`type`, resolvedElement, superReference = superReference)

    ref.qualifier match {
      case Some(qualifier) =>
        val result = qualifier.resolve() match {
          case _: PsiPackage =>
            Option(getContextOfType(resolvedElement, classOf[ScObject])) match {
              case Some(obj) if obj.isPackageObject =>
                makeProjection(ScDesignatorType(obj))
              case _ => fromType match {
                case Some(designator@ScDesignatorType(obj: ScObject)) if obj.isPackageObject =>
                  makeProjection(designator)
                case _ => ScalaType.designator(resolvedElement)
              }
            }
          case _ =>
            calculateReferenceType(qualifier, shapesOnly) match {
              case Success(tp, _) => makeProjection(tp)
              case failure: Failure => return failure
            }
        }
        Success(result, Some(ref))
      case _ =>
        ref.pathQualifier match {
          case Some(thisRef: ScThisReference) =>
            fromThisReference(thisRef, template => makeProjection(ScThisType(template)))(ref)
          case Some(superRef: ScSuperReference) =>
            fromSuperReference(superRef, template => makeProjection(ScThisType(template), resolvedElement.isInstanceOf[PsiClass]))(ref)
          case _ =>
            val result = resolvedElement match {
              case self: ScSelfTypeElement =>
                ScThisType(getContextOfType(self, classOf[ScTemplateDefinition]))
              case _ => fromType match {
                case Some(tp) => makeProjection(tp)
                case _ => ScalaType.designator(resolvedElement)
              }
            }
            Success(result, Some(ref))
        }
    }
  }

  private def fromTemplate(maybeTemplate: Option[ScTemplateDefinition],
                           message: String,
                           path: ScPathElement,
                           function: ScTemplateDefinition => ScType) = {
    import path.projectContext

    val element = Some(path)
    maybeTemplate match {
      case Some(template) => Success(function(template), element)
      case _ => Failure(message, element)
    }
  }

  private def fromThisReference(thisReference: ScThisReference,
                                function: ScTemplateDefinition => ScType)
                               (path: ScPathElement = thisReference) =
    fromTemplate(thisReference.refTemplate,
      "Cannot find template for this reference",
      path,
      function)

  private def fromSuperReference(superReference: ScSuperReference,
                                 function: ScTemplateDefinition => ScType)
                                (path: ScPathElement = superReference) =
    fromTemplate(superReference.drvTemplate,
      "Cannot find enclosing container",
      path,
      function)
}
