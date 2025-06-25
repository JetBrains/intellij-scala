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
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.types._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScSuperReference, ScThisReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticClass
import org.jetbrains.plugins.scala.lang.psi.types.Signature.ExportedSigInfo
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Nothing, TypeParameter, TypeParameterType}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.project.ProjectContext
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
    inferValueType:                 Boolean,
    noConstructor:                  Boolean = false,
    withUnnecessaryImplicitsUpdate: Boolean = false
  ): TypeResult = {
    ProgressManager.checkCanceled()

    def resolveConstructorWithLocalTypeInference(constrRef: ScStableCodeReference): TypeResult = {
      val bindResult = constrRef.bind()

      val result = bindResult match {
        case Some(r @ ScalaResolveResult(method: PsiMethod, _: ScSubstitutor)) if !noConstructor =>
          typeForConstructor(method, r)
        case Some(ScalaResolveResult(ta: ScTypeAlias, _: ScSubstitutor)) if ta.isExistentialTypeAlias =>
          Right(ScExistentialArgument(ta))
        case Some(srr) =>
          getContext match {
            case parameterized: ScParameterizedTypeElement if constrRef.isConstructorReference && !noConstructor =>
              if (!srr.element.isInstanceOf[ScTypeAliasDeclaration]) {
                Right(updateForParameterized(constrRef, srr.substitutor, srr.element, parameterized))
              } else calculateReferenceType(constrRef)
            case _ => calculateReferenceType(constrRef)
          }
        case _ => calculateReferenceType(constrRef)
      }

      result
    }

    def typeForConstructor(
      constr:        PsiMethod,
      srr:           ScalaResolveResult
    ): TypeResult = {
      findConstructorInvocation match {
        case Some(constrInvocation) =>
          val (tpe, _, implicitArgs) =
            Compatibility.checkConstructorApplicability(
              constrInvocation,
              constr,
              srr,
              inferValueType
            )

          constrInvocation.setImplicitArguments(implicitArgs)
          Right(tpe)
        case None => Failure(ScalaBundle.message("no.constructor.invocation.found"))
      }
    }

    reference match {
      case Some(ref) =>
        val isConstructorRef      = ref.isConstructorReference && !noConstructor
        val resolvedNoConstructor = ref.resolveNoConstructor

        if (resolvedNoConstructor.length == 1) {
          val resolvedNoConstructorHead = resolvedNoConstructor.head
          val subst                     = resolvedNoConstructorHead.matchClauseSubstitutor
          val isScala3                  = this.isInScala3File
          val hasNoArgs                 = findConstructorInvocation.forall(_.args.isEmpty)

          val typeResult =
            resolvedNoConstructorHead match {
              case ScalaResolveResult(psiTypeParameter: PsiTypeParameter, _) =>
                Right(TypeParameterType(psiTypeParameter))
              case ScalaResolveResult(tvar: ScTypeVariableTypeElement, _) =>
                Right(tvar.`type`().getOrAny)
              case ScalaResolveResult(synth: ScSyntheticClass, _) =>
                Right(synth.stdType)
              case ScalaResolveResult(to: ScTypeParametersOwner, subst: ScSubstitutor)
                if isConstructorRef &&
                  (to.typeParameters.isEmpty || getContext.isInstanceOf[ScParameterizedTypeElement]) &&
                  (!isScala3 || hasNoArgs) && !withUnnecessaryImplicitsUpdate =>

                val tp = getContext match {
                  case p: ScParameterizedTypeElement =>
                    val parameterized = updateForParameterized(ref, subst, to, p)
                    Right(parameterized)
                  case _ => calculateReferenceType(ref)
                }

                tp
              case ScalaResolveResult(to: PsiTypeParameterListOwner, subst: ScSubstitutor)
                if isConstructorRef &&
                  (to.getTypeParameters.isEmpty || getContext.isInstanceOf[ScParameterizedTypeElement]) &&
                  (!isScala3 || hasNoArgs) && !withUnnecessaryImplicitsUpdate =>

                val tp = getContext match {
                  case p: ScParameterizedTypeElement =>
                    val parameterized = updateForParameterized(ref, subst, to, p)
                    Right(parameterized)
                  case _ => calculateReferenceType(ref)
                }

                tp
              case ScalaResolveResult(fun: ScFunction, _)  => //SCL-19477
                Right(fun.returnType.getOrAny)
              case _ => resolveConstructorWithLocalTypeInference(ref)
            }

          typeResult.map(subst)
        } else resolveConstructorWithLocalTypeInference(ref)

      case None => pathElement match {
        case ref: ScStableCodeReference => calculateReferenceType(ref)
        case thisRef: ScThisReference   => fromThisReference(thisRef, ScThisType)()
        case superRef: ScSuperReference => fromSuperReference(superRef, ScThisType)()
      }
    }
  }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = {
    visitor.visitSimpleTypeElement(this)
  }
}

object ScSimpleTypeElementImpl {
  private def parameterizeClassType(tp: ScType, clazz: PsiTypeParameterListOwner): ScType =
    if (clazz.getTypeParameters.isEmpty) tp
    else
      ScParameterizedType(tp, clazz.getTypeParameters.map(TypeParameterType(_)).toSeq)

  // SCL-21176
  def parameterizeTypeAlias(tp: ScType, ta: ScTypeAliasDefinition): ScType =
    if (ta.typeParameters.isEmpty) tp
    else
      ScParameterizedType(tp, ta.typeParameters.map(TypeParameterType(_)))

  private def updateForParameterized(
    ref:   ScStableCodeReference,
    subst: ScSubstitutor,
    elem:  PsiNamedElement,
    p:     ScParameterizedTypeElement
  )(implicit
    ctx: ProjectContext
  ): ScType = {
    val tp = elem match {
      case ta: ScTypeAliasDefinition =>
        if (ScalaApplicationSettings.PRECISE_TEXT)
          parameterizeTypeAlias(calculateReferenceType(ref).getOrElse(return Nothing), ta)
        else
          ta.aliasedType.getOrElse(return Nothing)
      case clazz: PsiClass =>
        parameterizeClassType(
          calculateReferenceType(ref).getOrElse(return Nothing),
          clazz
        )
    }

    val res = subst(tp)

    val typeParameters = elem match {
      case tp: ScTypeParametersOwner if tp.typeParameters.nonEmpty =>
        tp.typeParameters.map(TypeParameter(_))
      case ptp: PsiTypeParameterListOwner if ptp.getTypeParameters.nonEmpty =>
        ptp.getTypeParameters.toSeq.map(TypeParameter(_))
      case _ => return res
    }

    val appSubst = ScSubstitutor.bind(typeParameters, p.typeArgList.typeArgs)(_.calcType)
    appSubst(res)
  }

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
        srr.exportedInfo match {
          case Some(ExportedSigInfo(_, fromType)) =>
            return Right(
              fromType.fold(ScalaType.designator(srr.element))(tpe =>
                ScProjectionType(tpe, srr.element)
              )
            )
          case None => (named, srr.fromType)
        }
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
