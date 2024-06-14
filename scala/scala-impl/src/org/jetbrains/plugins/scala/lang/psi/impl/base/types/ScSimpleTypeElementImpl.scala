package org.jetbrains.plugins.scala.lang.psi.impl.base
package types

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.getContextOfType
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedWithRecursionGuard}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.macros.MacroDef
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScSuperReference, ScThisReference, ScUnderScoreSectionUtil}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, ScalaElementVisitor}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.Expression
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, Nothing, TypeParameter, TypeParameterType, UndefinedType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{Parameter, ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings.{getInstance => ScalaApplicationSettings}

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScSimpleTypeElement {

  override protected def innerType: TypeResult = innerNonValueType(inferValueType = true)

  override def getTypeNoConstructor: TypeResult = innerNonValueType(inferValueType = true, noConstructor = true)

  override def getNonValueType(withUnnecessaryImplicitsUpdate: Boolean = false): TypeResult =
    cachedWithRecursionGuard(
      "ScSimpleTypeElementImpl.getNonValueType",
      this,
      Failure(ScalaBundle.message("recursive.non.value.type.of.type.element")),
      BlockModificationTracker(this),
      Tuple1(withUnnecessaryImplicitsUpdate)
    ) {
      innerNonValueType(inferValueType = false, withUnnecessaryImplicitsUpdate = withUnnecessaryImplicitsUpdate)
    }

  private def innerNonValueType(
    inferValueType: Boolean,
    noConstructor: Boolean = false,
    withUnnecessaryImplicitsUpdate: Boolean = false
  ): TypeResult = {
    ProgressManager.checkCanceled()

    def parametrise(tp: ScType, clazz: PsiTypeParameterListOwner): ScType =
      if (clazz.getTypeParameters.isEmpty) tp
      else
        ScParameterizedType(tp, clazz.getTypeParameters.map(TypeParameterType(_)).toSeq)

    // SCL-21176
    def parametrise1(tp: ScType, ta: ScTypeAliasDefinition): ScType =
      if (ta.typeParameters.isEmpty) tp
      else
        ScParameterizedType(tp, ta.typeParameters.map(TypeParameterType(_)))

    def getConstructorParams(constr: PsiMethod, subst: ScSubstitutor): (Seq[Seq[Parameter]], Boolean) =
      constr match {
        case ScalaConstructor(c) =>
          val clauses = c.effectiveParameterClauses
          (clauses.map(_.effectiveParameters.map { p =>
            val paramType   = subst(p.`type`().getOrAny)
            val defaultType = p.getDefaultExpression.flatMap(_.`type`().toOption).map(subst)

            new Parameter(
              p.name,
              p.deprecatedName,
              paramType,
              paramType,
              p.isDefaultParam,
              p.isRepeatedParameter,
              p.isCallByNameParameter,
              p.index,
              Option(p),
              defaultType
            )
          }), clauses.lastOption.exists(_.isImplicitOrUsing))
        case JavaConstructor(c) =>
          (Seq(c.parameters.map { p =>
            Parameter(p.paramType(), isRepeated = p.isVarArgs, index = p.index)
          }), false)
      }

    def updateImplicits(tp: ScType, withExpected: Boolean, params: Seq[Seq[Parameter]], lastImplicit: Boolean): ScType = {
      val (innerRes, implicitParams) =
        if (lastImplicit) {
          //Let's add implicit parameters
          val newTp = tp match {
            case ScTypePolymorphicType(i, p) =>
              ScTypePolymorphicType(ScMethodType(i, params.last, isImplicit = true), p)
            case _ => ScMethodType(tp, params.last, isImplicit = true)
          }
          val (updatedTp, implicits, _) =
            InferUtil.updateTypeWithImplicitParameters(newTp, this, None, withExpected, fullInfo = false)

          (updatedTp, implicits)
        } else {
          (tp, None)
        }
      findConstructorInvocation foreach {
        invoc =>
          invoc.setImplicitArguments(if (invoc.arguments.length < params.length) implicitParams else None)
      }
      innerRes
    }

    def typeForConstructor(
      ref:           ScStableCodeReference,
      constr:        PsiMethod,
      _subst:         ScSubstitutor,
      parentElement: PsiNamedElement
    ): ScType = {
      val clazz = constr.containingClass

      val (constrTypeParameters, constrSubst) = parentElement match {
        case _: ScTypeAliasDefinition if !ScalaApplicationSettings.PRECISE_TEXT => (Seq.empty, ScSubstitutor.empty)
        case owner: ScTypeParametersOwner if owner.typeParameters.nonEmpty =>
          constr match {
            case ScalaConstructor(c) =>
              val params = c.getConstructorTypeParameters
              val subst  = ScSubstitutor.bind(owner.typeParameters, params)(TypeParameterType(_))
              (params, subst)
            case JavaConstructor(cons) => (cons.getTypeParameters.toSeq, ScSubstitutor.empty)
            case _                     => (Seq.empty, ScSubstitutor.empty)
          }
        case _ => (Seq.empty, ScSubstitutor.empty)
      }

      val subst = _subst followed constrSubst

      val tp = parentElement match {
        case ta: ScTypeAliasDefinition =>
          if (ScalaApplicationSettings.PRECISE_TEXT) parametrise1(calculateReferenceType(ref).getOrElse(return Nothing), ta)
          else ta.aliasedType.getOrElse(return Nothing)
        case _ => parametrise(calculateReferenceType(ref).getOrElse(return Nothing), clazz)
      }

      val res                    = subst(tp)
      val (params, lastImplicit) = getConstructorParams(constr, subst)

      val typeParameters: Seq[TypeParameter] = parentElement match {
        case _: ScTypeParametersOwner if constrTypeParameters.nonEmpty =>
          constrTypeParameters.map(TypeParameter(_))
        case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
          tp.typeParameters.map(TypeParameter(_))
        case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.nonEmpty =>
          (ptp.getTypeParameters.toSeq ++ constrTypeParameters).map(TypeParameter(_))
        case _ =>
          updateImplicits(tp, withExpected = false, params = params, lastImplicit = lastImplicit)
          return res
      }

      getContext match {
        case p: ScParameterizedTypeElement =>
          val appSubst = ScSubstitutor.bind(typeParameters, p.typeArgList.typeArgs)(_.calcType)
          val newRes = appSubst(res)
          updateImplicits(newRes, withExpected = false, params = params, lastImplicit = lastImplicit)
          return newRes
        case _ =>
      }

      findConstructorInvocation match {
        case Some(constrInvocation) =>
          def updateWithClause(nonValueType: ScTypePolymorphicType, clauseIdx: Int, canThrowSCE: Boolean) = {
            InferUtil.localTypeInference(
              nonValueType.internalType,
              params(clauseIdx),
              constrInvocation.arguments(clauseIdx).exprs,
              nonValueType.typeParameters,
              canThrowSCE = canThrowSCE,
              filterTypeParams = false)
          }

          def withoutLastClause(): ScTypePolymorphicType = {
            var i = 0
            var result = ScTypePolymorphicType(res, typeParameters)
            while (i < params.length - 1 && i < constrInvocation.arguments.length - 1) {
              result = updateWithClause(result, i, canThrowSCE = false)
              i += 1
            }
            result
          }

          def lastClauseAndImplicits(previous: ScTypePolymorphicType, withExpected: Boolean): ScTypePolymorphicType = {
            val fromExpected = constrInvocation.expectedType match {
              case Some(expected) if withExpected =>
                def updateRes(expected: ScType): ScTypePolymorphicType = {
                  InferUtil.localTypeInference(previous.internalType,
                    Seq(Parameter(expected, isRepeated = false, index = 0)),
                    Seq(Expression(ScSubstitutor.bind(previous.typeParameters)(UndefinedType(_)).apply(res.inferValueType))),
                    previous.typeParameters, shouldUndefineParameters = false, filterTypeParams = false) //here should work in different way:
                }
                val fromUnderscore = constrInvocation.newTemplate match {
                  case Some(n) => ScUnderScoreSectionUtil.underscores(n).nonEmpty
                  case None => false
                }
                if (!fromUnderscore) {
                  updateRes(expected)
                } else {
                  expected match {
                    case FunctionType(retType, _) => updateRes(retType)
                    case _ => previous //do not update res, we haven't expected type
                  }
                }
              case _ => previous
            }

            //last clause after expected types
            val lastClauseIdx = constrInvocation.arguments.length - 1
            val withLastClause =
              if (lastClauseIdx >= 0 && lastClauseIdx < params.length) {
                updateWithClause(fromExpected, lastClauseIdx, canThrowSCE = withExpected)
              }
              else fromExpected

            if (lastImplicit && constrInvocation.arguments.length < params.length) {
              //Let's add implicit parameters
              updateImplicits(withLastClause, withExpected, params, lastImplicit) match {
                case t: ScTypePolymorphicType => t
                case _ => withLastClause
              }
            }
            else withLastClause
          }


          //We need to update type info for generics in the following order:
          //1. All clauses without last params clause or last arguments clause
          //2. According to expected type
          //3. Last argument clause
          //4. Implicit clauses if applicable
          //5. In case of SafeCheckException return to 3 to complete update without expected type

          val withoutLast = withoutLastClause()

          val nonValueType =
            try lastClauseAndImplicits(withoutLast, withExpected = true)
            catch {
              case _: SafeCheckException =>
                lastClauseAndImplicits(withoutLast, withExpected = false)
            }

          if (inferValueType) nonValueType.inferValueType
          else                nonValueType
        case None => res
      }
    }

    reference match {
      case Some(ref) =>
        def updateForParameterized(subst: ScSubstitutor, elem: PsiNamedElement,
                                   p: ScParameterizedTypeElement): (ScType, ScSubstitutor) = {
          val tp = elem match {
            case ta: ScTypeAliasDefinition =>
              if (ScalaApplicationSettings.PRECISE_TEXT) parametrise1(calculateReferenceType(ref).getOrElse(return (Nothing, ScSubstitutor.empty)), ta)
              else ta.aliasedType.getOrElse(return (Nothing, ScSubstitutor.empty))
            case clazz: PsiClass =>
              parametrise(calculateReferenceType(ref).
                getOrElse(return (Nothing, ScSubstitutor.empty)), clazz)
          }
          val res = subst(tp)
          val typeParameters: Seq[TypeParameter] = elem match {
            case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
              tp.typeParameters.map(TypeParameter(_))
            case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.nonEmpty =>
              ptp.getTypeParameters.toSeq.map(TypeParameter(_))
            case _ => return (res, ScSubstitutor.empty)
          }

          val appSubst = ScSubstitutor.bind(typeParameters, p.typeArgList.typeArgs)(_.calcType)
          (appSubst(res), appSubst)
        }
        val constrRef = ref.isConstructorReference && !noConstructor

        def updateImplicitsWithoutLocalTypeInference(r: TypeResult, ss: ScSubstitutor): TypeResult = {
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

        def resolveConstructorWithLocalTypeInference(): TypeResult = {
          val bindResult = ref.bind()
          val result = bindResult match {
            case Some(r @ ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor)) if !noConstructor =>
              Right(typeForConstructor(ref, method, subst, r.getActualElement))
            case Some(ScalaResolveResult(ta: ScTypeAlias, _: ScSubstitutor)) if ta.isExistentialTypeAlias =>
              Right(ScExistentialArgument(ta))
            case _ =>
              calculateReferenceType(ref, shapesOnly = false)
          }
          result
        }

        val resolvedNoConstructor = ref.resolveNoConstructor
        if (resolvedNoConstructor.length == 1) {
          val resolvedNoConstructorHead = resolvedNoConstructor.head
          val subst                     = resolvedNoConstructorHead.matchClauseSubstitutor

          val typeResult =
            resolvedNoConstructorHead match {
              case ScalaResolveResult(psiTypeParameter: PsiTypeParameter, _) =>
                Right(TypeParameterType(psiTypeParameter))
              case ScalaResolveResult(tvar: ScTypeVariableTypeElement, _) =>
                Right(tvar.`type`().getOrAny)
              case ScalaResolveResult(synth: ScSyntheticClass, _) =>
                Right(synth.stdType)
              case ScalaResolveResult(to: ScTypeParametersOwner, subst: ScSubstitutor)
                if constrRef && to.isInstanceOf[PsiNamedElement] &&
                  (to.typeParameters.isEmpty || getContext.isInstanceOf[ScParameterizedTypeElement]) =>
                val (tp, ss) = getContext match {
                  case p: ScParameterizedTypeElement if !to.isInstanceOf[ScTypeAliasDeclaration] =>
                    val (parameterized, ss) = updateForParameterized(subst, to.asInstanceOf[PsiNamedElement], p)
                    (Right(parameterized), ss)
                  case _ =>
                    (calculateReferenceType(ref), ScSubstitutor.empty)
                }
                updateImplicitsWithoutLocalTypeInference(tp, ss)
              case ScalaResolveResult(to: PsiTypeParameterListOwner, subst: ScSubstitutor)
                if constrRef && to.isInstanceOf[PsiNamedElement] &&
                  (to.getTypeParameters.isEmpty || getContext.isInstanceOf[ScParameterizedTypeElement]) =>
                val (result, ss) = getContext match {
                  case p: ScParameterizedTypeElement if !to.isInstanceOf[ScTypeAliasDeclaration] =>
                    val (parameterized, ss) = updateForParameterized(subst, to.asInstanceOf[PsiNamedElement], p)
                    (Right(parameterized), ss)
                  case _ =>
                    (calculateReferenceType(ref), ScSubstitutor.empty)
                }
                updateImplicitsWithoutLocalTypeInference(result, ss)
              case ScalaResolveResult(fun: ScFunction, _)  => //SCL-19477
                Right(fun.returnType.getOrAny)
              case _ =>
                resolveConstructorWithLocalTypeInference()
            }

          typeResult.map(subst)
        } else resolveConstructorWithLocalTypeInference()

      case None => pathElement match {
        case ref: ScStableCodeReference => calculateReferenceType(ref)
        case thisRef: ScThisReference => fromThisReference(thisRef, ScThisType)()
        case superRef: ScSuperReference => fromSuperReference(superRef, ScThisType)()
      }
    }
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitSimpleTypeElement(this)
  }
}

object ScSimpleTypeElementImpl {

  def calculateReferenceType(ref: ScStableCodeReference, shapesOnly: Boolean = false): TypeResult = {
    import ref.projectContext

    val (resolvedElement, fromType) = (if (!shapesOnly) {
      if (ref.isConstructorReference) {
        ref.resolveNoConstructor match {
          case Array(r@ScalaResolveResult(to: ScTypeParametersOwner, _: ScSubstitutor))
            if to.isInstanceOf[PsiNamedElement] &&
              (to.typeParameters.isEmpty || isParameterizedTypeRef(ref)) => Some(r)
          case Array(r@ScalaResolveResult(to: PsiTypeParameterListOwner, _: ScSubstitutor))
            if to.isInstanceOf[PsiNamedElement] &&
              (to.getTypeParameters.isEmpty || isParameterizedTypeRef(ref)) => Some(r)
          case _ => ref.bind()
        }
      } else ref.bind()
    } else {
      ref.shapeResolve match {
        case Array(r) => Option(r)
        case _        => None
      }
    }) match {
      case Some(r@ScalaResolveResult(Constructor(constr), _)) =>
        (constr.containingClass, r.fromType)
      case Some(ScalaResolveResult(MacroDef(f), _)) =>
        val macroEvaluator = ScalaMacroEvaluator.getInstance(f.getProject)
        val typeFromMacro = macroEvaluator.checkMacro(f, MacroContext(ref, None))
        return typeFromMacro.map(Right(_)).getOrElse(Failure(ScalaBundle.message("unknown.macro.in.type.position")))
      case Some(srr @ ScalaResolveResult(named: PsiNamedElement, _)) =>
        if (srr.exportedIn.nonEmpty) (named, None)
        else                         (named, srr.fromType)
      case _ => return Failure(ScalaBundle.message("cannot.resolve.ref", ref.refName))
    }

    def makeProjection(`type`: ScType) = ScProjectionType(`type`, resolvedElement)

    ref.qualifier match {
      case Some(qualifier) =>
        val result = qualifier.resolve() match {
          case _: PsiPackage =>
            Option(getContextOfType(resolvedElement, classOf[ScObject])) match {
              case Some(obj) if obj.isPackageObject => makeProjection(ScDesignatorType(obj))
              case _ => fromType match {
                case Some(designator@ScDesignatorType(obj: ScObject)) if obj.isPackageObject =>
                  makeProjection(designator)
                case _ => ScalaType.designator(resolvedElement)
              }
            }
          case _ =>
            calculateReferenceType(qualifier, shapesOnly) match {
              case Right(tp)            => makeProjection(tp)
              case failure @ Failure(_) => return failure
            }
        }
        Right(result)
      case _ =>
        ref.pathQualifier match {
          case Some(thisRef: ScThisReference) =>
            fromThisReference(thisRef, template => makeProjection(ScThisType(template)))(ref)
          case Some(superRef: ScSuperReference) =>
            fromSuperReference(superRef, template => makeProjection(ScThisType(template)))(ref)
          case _ =>
            val result = resolvedElement match {
              case self: ScSelfTypeElement => ScThisType(getContextOfType(self, classOf[ScTemplateDefinition]))
              case _ => fromType match {
                case Some(tp) => makeProjection(tp)
                case _        => ScalaType.designator(resolvedElement)
              }
            }
            Right(result)
        }
    }
  }

  private def fromTemplate(maybeTemplate: Option[ScTemplateDefinition],
                           @Nls message: String,
                           path: ScPathElement,
                           function: ScTemplateDefinition => ScType) = {
    import path.projectContext

    maybeTemplate match {
      case Some(template) => Right(function(template))
      case _ => Failure(message)
    }
  }

  private def fromThisReference(thisReference: ScThisReference,
                                function: ScTemplateDefinition => ScType)
                               (path: ScPathElement = thisReference) =
    fromTemplate(thisReference.refTemplate,
      ScalaBundle.message("cannot.find.template.for.this.reference"),
      path,
      function)

  private def fromSuperReference(superReference: ScSuperReference,
                                 function: ScTemplateDefinition => ScType)
                                (path: ScPathElement = superReference) =
    fromTemplate(superReference.drvTemplate,
      ScalaBundle.message("cannot.find.enclosing.container"),
      path,
      function)

  private def isParameterizedTypeRef(ref: ScStableCodeReference): Boolean = ref.getContext match {
    case _: ScParameterizedTypeElement => true
    case s: ScSimpleTypeElement        => s.getContext.isInstanceOf[ScParameterizedTypeElement]
    case _                             => false
  }
}
