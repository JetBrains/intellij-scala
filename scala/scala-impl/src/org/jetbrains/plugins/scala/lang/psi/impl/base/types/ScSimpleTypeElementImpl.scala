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
import org.jetbrains.plugins.scala.lang.macros.MacroDef
import org.jetbrains.plugins.scala.lang.macros.evaluator.{MacroContext, ScalaMacroEvaluator}
import org.jetbrains.plugins.scala.lang.psi.annotator.ScSimpleTypeElementAnnotator
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil.SafeCheckException
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.base.{Constructor, _}
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScSuperReference, ScThisReference, ScUnderScoreSectionUtil}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{InferUtil, ScalaElementVisitor}
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
import org.jetbrains.plugins.scala.macroAnnotations.{CachedWithRecursionGuard, ModCount}

/**
 * @author Alexander Podkhalyuzin
 * Date: 22.02.2008
 */

class ScSimpleTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node)
  with ScSimpleTypeElement with ScSimpleTypeElementAnnotator {

  protected def innerType: TypeResult = innerNonValueType(inferValueType = true)

  override def getTypeNoConstructor: TypeResult = innerNonValueType(inferValueType = true, noConstructor = true)

  @CachedWithRecursionGuard(this, Failure("Recursive non value type of type element"),
    ModCount.getBlockModificationCount)
  override def getNonValueType(withUnnecessaryImplicitsUpdate: Boolean = false): TypeResult =
    innerNonValueType(inferValueType = false, withUnnecessaryImplicitsUpdate = withUnnecessaryImplicitsUpdate)

  private def innerNonValueType(inferValueType: Boolean, noConstructor: Boolean = false, withUnnecessaryImplicitsUpdate: Boolean = false): TypeResult = {
    ProgressManager.checkCanceled()

    def parametrise(tp: ScType, clazz: PsiClass, subst: ScSubstitutor): ScType = {
      if (clazz.getTypeParameters.isEmpty) {
        tp
      } else {
        ScParameterizedType(tp, clazz.getTypeParameters.map(TypeParameterType(_)))
      }
    }

    def getConstructorParams(constr: PsiMethod, subst: ScSubstitutor): (Seq[Seq[Parameter]], Boolean) = {
      constr match {
        case ScalaConstructor(c) =>
          val clauses = c.effectiveParameterClauses
          (clauses.map(_.effectiveParameters.map { p =>
            val paramType: ScType = subst(p.`type`().getOrAny)
            new Parameter(p.name, p.deprecatedName, paramType, paramType, p.isDefaultParam,p.isRepeatedParameter,
              p.isCallByNameParameter, p.index, Some(p), p.getDefaultExpression.flatMap(_.`type`().toOption))
          }),
            clauses.lastOption.exists(_.isImplicit))
        case JavaConstructor(c) =>
          (Seq(c.parameters.map { p =>
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
        findConstructor.foreach(_.setImplicitArguments(res._2))
        res._1
      } else tp
    }

    def typeForConstructor(ref: ScStableCodeReference, constr: PsiMethod,
                           _subst: ScSubstitutor, parentElement: PsiNamedElement): ScType = {
      val clazz = constr.containingClass
      val (constrTypParameters: Seq[ScTypeParam], constrSubst: ScSubstitutor) = parentElement match {
        case _: ScTypeAliasDefinition => (Seq.empty, ScSubstitutor.empty)
        case owner: ScTypeParametersOwner if owner.typeParameters.nonEmpty =>
          constr match {
            case ScalaConstructor(c) =>
              val params = c.getConstructorTypeParameters.map(_.typeParameters).getOrElse(Seq.empty)
              val subst = ScSubstitutor.bind(owner.typeParameters, params)(TypeParameterType(_))
              (params, subst)
            case _ =>
              // JavaConstructor
              (Seq.empty, ScSubstitutor.empty)
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
      val res = subst(tp)

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
          val appSubst = ScSubstitutor.bind(typeParameters, p.typeArgList.typeArgs)(_.calcType)
          val newRes = appSubst(res)
          updateImplicits(newRes, withExpected = false, params = params, lastImplicit = lastImplicit)
          return newRes
        case _ =>
      }

      findConstructor match {
        case Some(c) =>
          def updateWithClause(nonValueType: ScTypePolymorphicType, clauseIdx: Int, canThrowSCE: Boolean) = {
            InferUtil.localTypeInference(
              nonValueType.internalType,
              params(clauseIdx),
              c.arguments(clauseIdx).exprs.map(new Expression(_)),
              nonValueType.typeParameters,
              canThrowSCE = canThrowSCE,
              filterTypeParams = false)
          }

          def withoutLastClause(): ScTypePolymorphicType = {
            var i = 0
            var result = ScTypePolymorphicType(res, typeParameters)
            while (i < params.length - 1 && i < c.arguments.length - 1) {
              result = updateWithClause(result, i, canThrowSCE = false)
              i += 1
            }
            result
          }

          def lastClauseAndImplicits(previous: ScTypePolymorphicType, withExpected: Boolean): ScTypePolymorphicType = {
            val fromExpected = c.expectedType match {
              case Some(expected) if withExpected =>
                def updateRes(expected: ScType): ScTypePolymorphicType = {
                  InferUtil.localTypeInference(previous.internalType,
                    Seq(Parameter(expected, isRepeated = false, index = 0)),
                    Seq(new Expression(ScSubstitutor.bind(previous.typeParameters)(UndefinedType(_)).apply(res.inferValueType))),
                    previous.typeParameters, shouldUndefineParameters = false, filterTypeParams = false) //here should work in different way:
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
                    case _ => previous //do not update res, we haven't expected type
                  }
                }
              case _ => previous
            }

            //last clause after expected types
            val lastClauseIdx = c.arguments.length - 1
            val withLastClause =
              if (lastClauseIdx >= 0 && lastClauseIdx < params.length) {
                updateWithClause(fromExpected, lastClauseIdx, canThrowSCE = withExpected)
              }
              else fromExpected

            if (lastImplicit && c.arguments.length < params.length) {
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

          if (inferValueType) {
            val pts = nonValueType match {
              case t: ScTypePolymorphicType => t.polymorphicTypeSubstitutor
              case _ => ScSubstitutor.empty
            }
            pts(nonValueType.internalType)
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

        ref.resolveNoConstructor match {
          case Array(ScalaResolveResult(psiTypeParameter: PsiTypeParameter, _)) =>
            Right(TypeParameterType(psiTypeParameter))
          case Array(ScalaResolveResult(tvar: ScTypeVariableTypeElement, _)) =>
            Right(tvar.`type`().getOrAny)
          case Array(ScalaResolveResult(synth: ScSyntheticClass, _)) =>
            Right(synth.stdType)
          case Array(ScalaResolveResult(to: ScTypeParametersOwner, subst: ScSubstitutor))
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
          case Array(ScalaResolveResult(to: PsiTypeParameterListOwner, subst: ScSubstitutor))
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
          case _ => //resolve constructor with local type inference
            ref.bind() match {
              case Some(r@ScalaResolveResult(method: PsiMethod, subst: ScSubstitutor)) if !noConstructor =>
                Right(typeForConstructor(ref, method, subst, r.getActualElement))
              case Some(ScalaResolveResult(ta: ScTypeAlias, _: ScSubstitutor)) if ta.isExistentialTypeAlias =>
                Right(ScExistentialArgument(ta))
              case _ => calculateReferenceType(ref, shapesOnly = false)
            }
        }
      case None => pathElement match {
        case ref: ScStableCodeReference => calculateReferenceType(ref)
        case thisRef: ScThisReference => fromThisReference(thisRef, ScThisType)()
        case superRef: ScSuperReference => fromSuperReference(superRef, ScThisType)()
      }
    }
  }

  override protected def acceptScala(visitor: ScalaElementVisitor) {
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
        case Array(r) => Some(r)
        case _ => None
      }
    }) match {
      case Some(r@ScalaResolveResult(Constructor(constr), _)) =>
        (constr.containingClass, r.fromType)
      case Some(ScalaResolveResult(MacroDef(f), _)) =>
        val macroEvaluator = ScalaMacroEvaluator.getInstance(f.getProject)
        val typeFromMacro = macroEvaluator.checkMacro(f, MacroContext(ref, None))
        return typeFromMacro.map(Right(_)).getOrElse(Failure("Unknown macro in type position"))
      case Some(r@ScalaResolveResult(n: PsiNamedElement, _)) => (n, r.fromType)
      case _ => return Failure("Cannot resolve reference")
    }

    def makeProjection(`type`: ScType, superReference: Boolean = false) =
      ScProjectionType(`type`, resolvedElement)

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
              case Right(tp) => makeProjection(tp)
              case failure@Failure(_) => return failure
            }
        }
        Right(result)
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
            Right(result)
        }
    }
  }

  private def fromTemplate(maybeTemplate: Option[ScTemplateDefinition],
                           message: String,
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

  private def isParameterizedTypeRef(ref: ScStableCodeReference): Boolean = ref.getContext match {
    case _: ScParameterizedTypeElement => true
    case s: ScSimpleTypeElement => s.getContext.isInstanceOf[ScParameterizedTypeElement]
    case _ => false
  }
}
