package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import org.jetbrains.plugins.scala.caches.CachesUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.InferUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScMethodLike, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{PsiTypeParameterExt, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.implicits.ScImplicitlyConvertible
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.{ConformanceExtResult, Expression}
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypingContext}
import org.jetbrains.plugins.scala.lang.psi.types.{api, _}
import org.jetbrains.plugins.scala.lang.psi.{ScalaPsiElement, ScalaPsiUtil}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

import scala.collection.Set
import scala.collection.immutable.HashSet
import scala.collection.mutable.ArrayBuffer

//todo: remove all argumentClauses, we need just one of them
class MethodResolveProcessor(override val ref: PsiElement,
                             val refName: String,
                             var argumentClauses: List[Seq[Expression]],
                             val typeArgElements: Seq[ScTypeElement],
                             val prevTypeInfo: Seq[TypeParameter],
                             override val kinds: Set[ResolveTargets.Value] = StdKinds.methodRef,
                             val expectedOption: () => Option[ScType] = () => None,
                             val isUnderscore: Boolean = false,
                             var isShapeResolve: Boolean = false,
                             val constructorResolve: Boolean = false,
                             val enableTupling: Boolean = false,
                             var noImplicitsForArgs: Boolean = false,
                             val selfConstructorResolve: Boolean = false,
                             val isDynamic: Boolean = false)
                            (implicit override val typeSystem: TypeSystem)
  extends ResolveProcessor(kinds, ref, refName) {

  private def isUpdate: Boolean = {
    if (ref == null) return false
    ref.getContext match {
      case call: ScMethodCall => call.isUpdateCall
      case _ => false
    }
  }

  override def execute(element : PsiElement, state: ResolveState): Boolean = {
    val named = element match {
      case named: PsiNamedElement => named
      case _ => return true //do not process
    }
    def implicitConversionClass: Option[PsiClass] = state.get(ScImplicitlyConvertible.IMPLICIT_RESOLUTION_KEY).toOption
    def implFunction: Option[PsiNamedElement] = state.get(CachesUtil.IMPLICIT_FUNCTION).toOption
    def implType: Option[ScType] = state.get(CachesUtil.IMPLICIT_TYPE).toOption
    def isNamedParameter: Boolean = state.get(CachesUtil.NAMED_PARAM_KEY).toOption.exists(_.booleanValue)
    def fromType: Option[ScType] = state.get(BaseProcessor.FROM_TYPE_KEY).toOption
    def unresolvedTypeParameters: Option[Seq[TypeParameter]] = state.get(BaseProcessor.UNRESOLVED_TYPE_PARAMETERS_KEY).toOption
    def nameShadow: Option[String] = Option(state.get(ResolverEnv.nameKey))
    def forwardReference: Boolean = isForwardReference(state)
    if (nameAndKindMatch(named, state) || constructorResolve) {
      val accessible = isNamedParameter || isAccessible(named, ref)
      if (accessibility && !accessible) return true

      val s = fromType match {
        case Some(tp) => getSubst(state).followUpdateThisType(tp)
        case _ => getSubst(state)
      }
      element match {
        case m: PsiMethod =>
          addResult(new ScalaResolveResult(m, s, getImports(state), nameShadow, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType, fromType = fromType, isAccessible = accessible,
            isForwardReference = forwardReference, unresolvedTypeParameters = unresolvedTypeParameters))
        case _: ScClass =>
        case o: ScObject if o.isPackageObject =>  // do not resolve to package object
        case obj: ScObject if ref.getParent.isInstanceOf[ScMethodCall] || ref.getParent.isInstanceOf[ScGenericCall] =>
          val functionName = if (isUpdate) "update" else "apply"
          val typeResult = getFromType(state) match {
            case Some(tp) => Success(ScProjectionType(tp, obj, superReference = false), Some(obj))
            case _ => obj.getType(TypingContext.empty)
          }
          val processor = new CollectMethodsProcessor(ref, functionName)

          import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.ImportUsed
          // keep information about imports for proper precedence
          val newState = ResolveState.initial.put(ImportUsed.key,  state.get(ImportUsed.key))
          typeResult.foreach(t => processor.processType(t, ref, newState))

          val sigs = processor.candidatesS.flatMap {
            case ScalaResolveResult(meth: PsiMethod, subst) => Some((meth, subst))
            case _ => None
          }.toSeq
          val seq = sigs.map {
            case (m, subst) =>
              new ScalaResolveResult(m, subst, getImports(state), nameShadow, implicitConversionClass,
                implicitFunction = implFunction, implicitType = implType, fromType = fromType, parentElement = Some(obj),
                isAccessible = accessible && isAccessible(m, ref), isForwardReference = forwardReference,
                unresolvedTypeParameters = unresolvedTypeParameters)
          }.filter {
            case r => !accessibility || r.isAccessible
          }
          if (seq.nonEmpty) addResults(seq)
          else addResult(new ScalaResolveResult(named, s, getImports(state), nameShadow, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType, isNamedParameter = isNamedParameter,
            fromType = fromType, isAccessible = accessible, isForwardReference = forwardReference,
            unresolvedTypeParameters = unresolvedTypeParameters))
        case synthetic: ScSyntheticFunction =>
          addResult(new ScalaResolveResult(synthetic, s, getImports(state), nameShadow, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType, fromType = fromType, isAccessible = accessible,
            isForwardReference = forwardReference, unresolvedTypeParameters = unresolvedTypeParameters))
        case pack: PsiPackage =>
          addResult(new ScalaResolveResult(ScPackageImpl(pack), s, getImports(state), nameShadow, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType, fromType = fromType, isAccessible = accessible,
            isForwardReference = forwardReference, unresolvedTypeParameters = unresolvedTypeParameters))
        case _ =>
          addResult(new ScalaResolveResult(named, s, getImports(state), nameShadow, implicitConversionClass,
            implicitFunction = implFunction, implicitType = implType, isNamedParameter = isNamedParameter,
            fromType = fromType, isAccessible = accessible, isForwardReference = forwardReference,
            unresolvedTypeParameters = unresolvedTypeParameters))
      }
    }
    true
  }



  override def candidatesS: Set[ScalaResolveResult] = {
    if (isDynamic) {
      collectCandidates(super.candidatesS.map(_.copy(isDynamic = true))).filter(_.isApplicable())
    } else {
      collectCandidates(super.candidatesS)
    }
  }

  private def collectCandidates(input: Set[ScalaResolveResult]): Set[ScalaResolveResult] = {
    if (input.isEmpty) return input
    if (!isShapeResolve && enableTupling && argumentClauses.nonEmpty) {
      isShapeResolve = true
      val cand1 = MethodResolveProcessor.candidates(this, input)
      if (!isDynamic && (cand1.isEmpty || cand1.forall(_.tuplingUsed))) {
        //tupling ok
        isShapeResolve = false
        val oldArg = argumentClauses
        val tpl = ScalaPsiUtil.tuplizy(argumentClauses.head, ref.getResolveScope, ref.getManager, ScalaPsiUtil.firstLeaf(ref))
        if (tpl.isEmpty) {
          return MethodResolveProcessor.candidates(this, input)
        }
        argumentClauses = tpl.toList
        val res = MethodResolveProcessor.candidates(this, input)
        argumentClauses = oldArg
        if (res.forall(!_.isApplicable())) {
          return MethodResolveProcessor.candidates(this, input)
        }
        res.map(r => r.copy(tuplingUsed = true))
      } else {
        isShapeResolve = false
        MethodResolveProcessor.candidates(this, input)
      }
    } else
      MethodResolveProcessor.candidates(this, input)
    }
  }

object MethodResolveProcessor {
  private def problemsFor(c: ScalaResolveResult,
                          checkWithImplicits: Boolean,
                          ref: PsiElement,
                          argumentClauses: List[Seq[Expression]],
                          typeArgElements: Seq[ScTypeElement],
                          selfConstructorResolve: Boolean,
                          prevTypeInfo: Seq[TypeParameter],
                          expectedOption: () => Option[ScType],
                          isUnderscore: Boolean,
                          isShapeResolve: Boolean)(implicit typeSystem: TypeSystem): ConformanceExtResult = {
    val problems = new ArrayBuffer[ApplicabilityProblem]()

    val realResolveResult = c.innerResolveResult match {
      case Some(rr) => rr
      case _ => c
    }
    val element = realResolveResult.element
    val s = realResolveResult.substitutor

    val elementForUndefining = element match {
      case m: PsiMethod if m.isConstructor && !selfConstructorResolve => realResolveResult.getActualElement
      case _ => element //do not
    }

    val substitutor: ScSubstitutor =
      undefinedSubstitutor(elementForUndefining, s, selfConstructorResolve, typeArgElements).followed(InferUtil.undefineSubstitutor(prevTypeInfo))

    val typeParameters: Seq[TypeParameter] = prevTypeInfo ++ (element match {
      case fun: ScFunction => fun.typeParameters.map(TypeParameter(_))
      case fun: PsiMethod => fun.getTypeParameters.map(TypeParameter(_)).toSeq
      case _ => Seq.empty
    })

    def addExpectedTypeProblems(eOption: Option[ScType] = expectedOption()): Unit = {
      for (expected <- eOption) {
        val retType: ScType = element match {
          case f: ScFunction if f.paramClauses.clauses.length > 1 &&
            !f.paramClauses.clauses.apply(1).isImplicit =>
            problems += ExpectedTypeMismatch //do not check expected types for more than one param clauses
            Nothing
          case f: ScFunction => substitutor.subst(f.returnType.getOrNothing)
          case f: ScFun => substitutor.subst(f.retType)
          case m: PsiMethod =>
            Option(m.getReturnType).map { rt =>
              substitutor.subst(rt.toScType())
            }.getOrElse(Nothing)
          case _ => Nothing
        }
        if (!retType.conforms(expected) && !expected.equiv(api.Unit)) {
          problems += ExpectedTypeMismatch
        }
      }
    }

    def checkFunction(fun: PsiNamedElement): ConformanceExtResult = {
      def default(): ConformanceExtResult = {
        fun match {
          case fun: ScFunction if fun.paramClauses.clauses.isEmpty ||
            fun.paramClauses.clauses.head.parameters.isEmpty ||
            isUnderscore => ConformanceExtResult(problems)
          case fun: ScFun if fun.paramClauses == Seq() || fun.paramClauses == Seq(Seq()) || isUnderscore =>
            addExpectedTypeProblems()
            ConformanceExtResult(problems)
          case method: PsiMethod if method.getParameterList.getParameters.isEmpty ||
            isUnderscore =>
            addExpectedTypeProblems()
            ConformanceExtResult(problems)
          case _ =>
            addExpectedTypeProblems()
            problems += MissedParametersClause(null)
            ConformanceExtResult(problems)
        }
      }

      def processFunctionType(retType: ScType, params: Seq[ScType]): ConformanceExtResult = {
        val args = params.map(new Expression(_))
        val result = Compatibility.compatible(fun, substitutor, List(args), checkWithImplicits = false,
        scope = ref.getResolveScope, isShapesResolve = isShapeResolve)
        problems ++= result.problems
        addExpectedTypeProblems(Some(retType))
        result.copy(problems)
      }

      fun match {
        case fun: ScFunction if fun.paramClauses.clauses.isEmpty =>
          addExpectedTypeProblems()
          return ConformanceExtResult(problems)
        case fun: ScFun if fun.paramClauses.isEmpty =>
          addExpectedTypeProblems()
          return ConformanceExtResult(problems)
        case _ =>
      }

      expectedOption().map(_.removeAbstracts) match {
        case Some(FunctionType(retType, params)) => processFunctionType(retType, params)
        case Some(tp: ScType) if ScalaPsiUtil.isSAMEnabled(fun) =>
          ScalaPsiUtil.toSAMType(tp, fun.getResolveScope, fun.scalaLanguageLevelOrDefault) match {
            case Some(FunctionType(retType, params)) => processFunctionType(retType, params)
            case _ => default()
          }
        case _ => default()
      }
    }

    def constructorCompatibility(constr: ScMethodLike with PsiNamedElement): ConformanceExtResult = {
      val classTypeParameters: Seq[ScTypeParam] = constr.getClassTypeParameters.map(_.typeParameters).getOrElse(Seq())
      if (typeArgElements.isEmpty || typeArgElements.length == classTypeParameters.length) {
        val result =
          Compatibility.compatible(constr, substitutor, argumentClauses, checkWithImplicits,
            ref.getResolveScope, isShapeResolve)
        problems ++= result.problems
        result.copy(problems)
      } else {
        problems += new ApplicabilityProblem("2")
        ConformanceExtResult(problems)
      }
    }

    def javaConstructorCompatibility(constr: PsiMethod): ConformanceExtResult = {
      val classTypeParmeters = constr.containingClass.getTypeParameters
      if (typeArgElements.isEmpty || typeArgElements.length == classTypeParmeters.length) {
        val result =
          Compatibility.compatible(constr, substitutor, argumentClauses, checkWithImplicits,
            ref.getResolveScope, isShapeResolve)
        problems ++= result.problems
        result.copy(problems)
      } else {
        problems += new ApplicabilityProblem("2")
        ConformanceExtResult(problems)
      }
    }

    val result = element match {
      //objects
      case _: ScObject if argumentClauses.nonEmpty =>
        problems += new DoesNotTakeParameters
        ConformanceExtResult(problems)
      case _: PsiClass =>
        ConformanceExtResult(problems)
      case _: ScTypeAlias =>
        ConformanceExtResult(problems)
      //Implicit Application
      case f: ScFunction if f.hasMalformedSignature =>
        problems += new MalformedDefinition
        ConformanceExtResult(problems)
      case c: ScPrimaryConstructor if c.hasMalformedSignature =>
        problems += new MalformedDefinition
        ConformanceExtResult(problems)
      case f: ScFunction if f.isConstructor => constructorCompatibility(f)
      case c: ScPrimaryConstructor with PsiNamedElement => constructorCompatibility(c)
      case method: PsiMethod if method.isConstructor => javaConstructorCompatibility(method)
      case fun: ScFunction if (typeArgElements.isEmpty ||
              typeArgElements.length == fun.typeParameters.length) && fun.paramClauses.clauses.length == 1 &&
              fun.paramClauses.clauses.head.isImplicit &&
              argumentClauses.isEmpty =>
        addExpectedTypeProblems()
        ConformanceExtResult(problems) //special case for cases like Seq.toArray
      //eta expansion
      case fun: ScTypeParametersOwner if (typeArgElements.isEmpty ||
              typeArgElements.length == fun.typeParameters.length) && argumentClauses.isEmpty &&
              fun.isInstanceOf[PsiNamedElement] =>
        fun match {
          case function: ScFunction if function.isConstructor =>
            problems += new ApplicabilityProblem("1")
            return ConformanceExtResult(problems)
          case _ =>
        }
        checkFunction(fun.asInstanceOf[PsiNamedElement])
      case fun: PsiTypeParameterListOwner if (typeArgElements.isEmpty ||
              typeArgElements.length == fun.getTypeParameters.length) && argumentClauses.isEmpty &&
              fun.isInstanceOf[PsiNamedElement] =>
        checkFunction(fun.asInstanceOf[PsiNamedElement])
      //simple application including empty application
      case tp: ScTypeParametersOwner with PsiNamedElement =>
        val args = argumentClauses.headOption.toList

        val typeArgCount = typeArgElements.length
        val typeParamCount = tp.typeParameters.length
        if (typeArgCount > 0 && typeArgCount != typeParamCount) {
          if (typeParamCount == 0) {
            problems += DoesNotTakeTypeParameters
          } else if (typeParamCount < typeArgCount) {
            problems ++= typeArgElements.drop(typeParamCount).map(ExcessTypeArgument)
          } else {
            problems ++= tp.typeParameters.drop(typeArgCount).map(ptp => MissedTypeParameter(TypeParameter(ptp)))
          }
          addExpectedTypeProblems()
          ConformanceExtResult(problems)
        } else {
          val result =
            Compatibility.compatible(tp.asInstanceOf[PsiNamedElement], substitutor, args, checkWithImplicits,
              ref.getResolveScope, isShapeResolve)
          problems ++= result.problems
          addExpectedTypeProblems()
          result.copy(problems)
        }
      case tp: PsiTypeParameterListOwner with PsiNamedElement =>
        val typeArgCount = typeArgElements.length
        val typeParamCount = tp.getTypeParameters.length
        if (typeArgCount > 0 && typeArgCount != typeParamCount) {
          if (typeParamCount == 0) {
            problems += DoesNotTakeTypeParameters
          } else if (typeParamCount < typeArgCount) {
            problems ++= typeArgElements.drop(typeParamCount).map(ExcessTypeArgument)
          } else {
            problems ++= tp.getTypeParameters.drop(typeArgCount).map(ptp => MissedTypeParameter(TypeParameter(ptp)))
          }
          addExpectedTypeProblems()
          new ConformanceExtResult(problems)
        } else {
          val args = argumentClauses.headOption.toList
          val result =
            Compatibility.compatible(tp, substitutor, args, checkWithImplicits,
              ref.getResolveScope, isShapeResolve)
          problems ++= result.problems
          addExpectedTypeProblems()
          result.copy(problems)
        }
      case _ =>
        if (typeArgElements.nonEmpty) problems += DoesNotTakeTypeParameters
        if (argumentClauses.nonEmpty) problems += new DoesNotTakeParameters
        addExpectedTypeProblems()
        ConformanceExtResult(problems)
    }

    if (result.problems.forall(_ == ExpectedTypeMismatch)) {
      var uSubst = result.undefSubst
      uSubst.getSubstitutor(notNonable = false) match {
        case None => result.copy(problems = Seq(WrongTypeParameterInferred))
        case Some(unSubst) =>
          def hasRecursiveTypeParameters(typez: ScType): Boolean = {

            var hasRecursiveTypeParameters = false
            typez.recursiveUpdate {
              case tpt: TypeParameterType =>
                typeParameters.find(_.nameAndId == tpt.nameAndId) match {
                  case None => (true, tpt)
                  case _ =>
                    hasRecursiveTypeParameters = true
                    (true, tpt)
                }
              case tp: ScType => (hasRecursiveTypeParameters, tp)
            }
            hasRecursiveTypeParameters
          }
          for (TypeParameter(typeParams, lowerType, upperType, tParam) <- typeParameters) {
            if (lowerType.v != Nothing) {
              val substedLower = s.subst(unSubst.subst(lowerType.v))
              if (!hasRecursiveTypeParameters(substedLower)) {
                uSubst = uSubst.addLower(tParam.nameAndId, substedLower, additional = true)
              }
            }
            if (upperType.v != Any) {
              val substedUpper = s.subst(unSubst.subst(upperType.v))
              if (!hasRecursiveTypeParameters(substedUpper)) {
                uSubst = uSubst.addUpper(tParam.nameAndId, substedUpper, additional = true)
              }
            }
          }
          uSubst.getSubstitutor(notNonable = false) match {
            case Some(_) => result
            case _ => result.copy(problems = Seq(WrongTypeParameterInferred))
          }
      }
    } else result
  }

  // TODO clean this up
  def undefinedSubstitutor(element: PsiNamedElement, s: ScSubstitutor, selfConstructorResolve: Boolean,
                           typeArgElements: Seq[ScTypeElement])
                          (implicit typeSystem: TypeSystem = element.typeSystem): ScSubstitutor = {
    if (selfConstructorResolve) return ScSubstitutor.empty

    //todo: it's always None, if you have constructor => actual element is class of type alias
    val constructorTypeParameters = element match {
      case ml: ScMethodLike => ml.getClassTypeParameters
      case _ => None
    }

    val maybeTypeParameters: Option[Seq[PsiTypeParameter]] = (constructorTypeParameters, element) match {
      case (Some(typeParameterClause), _) =>
        Some(typeParameterClause.typeParameters)
      //todo: this case is impossible case for reasons mentioned above
      case (_, method: PsiMethod) if method.isConstructor => // Java constructors
        Some(method.containingClass.getTypeParameters)
      case (None, t: ScTypeParametersOwner) =>
        Some(t.typeParameters)
      case (None, p: PsiTypeParameterListOwner) =>
        Some(p.getTypeParameters)
      case _ => None
    }
    maybeTypeParameters match {
      case Some(typeParameters: Seq[PsiTypeParameter]) =>
        s.followed(
          if (typeArgElements.nonEmpty && typeParameters.length == typeArgElements.length) {
            ScalaPsiUtil.genericCallSubstitutor(typeParameters.map(_.nameAndId), typeArgElements)
          } else {
            typeParameters.foldLeft(ScSubstitutor.empty) {
              case (subst, typeParameter) =>
                subst.bindT(typeParameter.nameAndId, UndefinedType(TypeParameterType(typeParameter)))
            }
          })
      case _ => s
    }
  }

  def candidates(proc: MethodResolveProcessor, _input: Set[ScalaResolveResult]): Set[ScalaResolveResult] = {
    import proc._

    //We want to leave only fields and properties from inherited classes, this is important, because
    //field in base class is shadowed by private field from inherited class
    val input: Set[ScalaResolveResult] = _input.filter {
      case r: ScalaResolveResult =>
        r.element match {
          case f: ScFunction if f.hasParameterClause => true
          case b: ScTypedDefinition =>
            b.nameContext match {
              case m: ScMember =>
                val clazz1: ScTemplateDefinition = m.containingClass
                if (clazz1 == null) true
                else {
                  _input.forall {
                    case r2: ScalaResolveResult =>
                      r2.element match {
                        case f: ScFunction if f.hasParameterClause => true
                        case b2: ScTypedDefinition =>
                          b2.nameContext match {
                            case m2: ScMember =>
                              val clazz2: ScTemplateDefinition = m2.containingClass
                              if (clazz2 == null) true
                              else {
                                if (clazz1 == clazz2) true
                                else {
                                  ScalaPsiUtil.cachedDeepIsInheritor(clazz1, clazz2)
                                }
                              }
                            case _ => true
                          }
                        case _ => true
                      }
                  }
                }
              case _ => true
            }
          case _ => true
        }
    }

    def expand(r: ScalaResolveResult): Set[(ScalaResolveResult, Boolean)] = {
      def applyMethodsFor(tp: ScType): Set[(ScalaResolveResult, Boolean)] = {
        val (substitutor: ScSubstitutor, cleanTypeArguments) = {
          if (typeArgElements.nonEmpty) {
            r.element match {
              case owner: ScTypeParametersOwner if owner.typeParameters.nonEmpty =>
                (ScalaPsiUtil.genericCallSubstitutor(owner.typeParameters.map(_.nameAndId), typeArgElements).followed(r.substitutor), true)
              case owner: PsiTypeParameterListOwner if owner.getTypeParameters.length > 0 =>
                (ScalaPsiUtil.genericCallSubstitutor(owner.getTypeParameters.map(_.nameAndId), typeArgElements).followed(r.substitutor), true)
              case _ => (r.substitutor, false)
            }
          } else {
            (r.substitutor, false)
          }
        }

        val processor = new CollectMethodsProcessor(ref, "apply")
        processor.processType(substitutor.subst(tp), ref.asInstanceOf[ScalaPsiElement])
        val cands = processor.candidatesS.map(rr => (r.copy(innerResolveResult = Some(rr)), cleanTypeArguments))
        if (cands.isEmpty) HashSet((r, false)) else cands
      }

      r.element match {
        case f: ScFunction if f.hasParameterClause => HashSet((r, false))
        case b: ScTypedDefinition if argumentClauses.nonEmpty =>
          applyMethodsFor(b.getType(TypingContext.empty).getOrElse(return HashSet.empty))
        case b: PsiField => // See SCL-3055
          applyMethodsFor(b.getType.toScType())
        case _ => HashSet((r, false))
      }
    }

    def mapper(applicationImplicits: Boolean): Set[ScalaResolveResult] = {
      if (argumentClauses.nonEmpty) {
        input.flatMap(expand).map {
          case (r, cleanTypeArguments) =>
            val pr = problemsFor(r, applicationImplicits, ref, argumentClauses,
              if (cleanTypeArguments) Seq.empty else typeArgElements,
              selfConstructorResolve, prevTypeInfo, expectedOption, isUnderscore, isShapeResolve)
            r.innerResolveResult match {
              case Some(rr) =>
                r.copy(innerResolveResult = Some(rr.copy(problems = pr.problems,
                  defaultParameterUsed = pr.defaultParameterUsed)))
              case _ => r.copy(problems = pr.problems, defaultParameterUsed = pr.defaultParameterUsed, resultUndef = Some(pr.undefSubst))
            }
        }
      } else input.map(r => {
        val pr = problemsFor(r, applicationImplicits, ref, argumentClauses, typeArgElements, selfConstructorResolve,
          prevTypeInfo, expectedOption, isUnderscore, isShapeResolve)
        r.copy(problems = pr.problems, defaultParameterUsed = pr.defaultParameterUsed, resultUndef = Some(pr.undefSubst))
      })
    }
    var mapped = mapper(applicationImplicits = false)
    var filtered = mapped.filter(_.isApplicableInternal(withExpectedType = true))
    if (filtered.isEmpty) filtered = mapped.filter(_.isApplicableInternal(withExpectedType = false))

    if (filtered.isEmpty && !noImplicitsForArgs) {
      //check with implicits
      mapped = mapper(applicationImplicits = true)
      filtered = mapped.filter(_.isApplicableInternal(withExpectedType = true))
      if (filtered.isEmpty) filtered = mapped.filter(_.isApplicableInternal(withExpectedType = false))
    }

    val onlyValues = mapped.forall { r =>
      r.element match {
        case f: ScFunction => false
        case t: ScTypedDefinition => r.innerResolveResult.isEmpty && r.problems.size == 1
        case _ => false
      }
    }
    if (filtered.isEmpty && onlyValues) {
      //possible implicit conversions in ScMethodCall
      return input.map(_.copy(notCheckedResolveResult = true))
    } else if (!onlyValues) {
      //in this case all values are not applicable
      mapped = mapped.map(r => {
        if (r.isApplicable()) {
          r.innerResolveResult match {
            case Some(rr) => r.copy(problems = rr.problems)
            case _ => r
          }
        }
        else r
      })
    }

    //choose alternative with by name params
    if (argumentClauses.nonEmpty && filtered.size > 1 && !isShapeResolve) {
      argumentClauses.head.map(assignmemt => assignmemt.expr).
        collect { case assignment: ScAssignStmt => assignment.assignName }.foreach { listOfNames =>

        filtered = filtered.filter(r =>
          r.element match {
            case func: ScFunction if func.hasParameterClause =>
              val paramsNames = func.parameterList.params.map(_.name)
              listOfNames.find(str => !paramsNames.contains(str)).forall(_.isEmpty)
            case _ => false
          }
        )
      }
    }

    //remove default parameters alternatives
    if (filtered.size > 1 && !isShapeResolve) filtered = filtered.filter(r => r.innerResolveResult match {
      case Some(rr) => !rr.defaultParameterUsed
      case None => !r.defaultParameterUsed
    })

    if (isShapeResolve) {
      if (filtered.isEmpty) {
        if (enableTupling) {
          val filtered2 = input.filter(r => {
            r.element match {
              case fun: ScFun if fun.paramClauses.nonEmpty =>
                fun.paramClauses.head.length == 1
              case fun: ScFunction if fun.paramClauses.clauses.nonEmpty =>
                fun.paramClauses.clauses.head.parameters.length == 1
              case p: ScPrimaryConstructor if p.parameterList.clauses.nonEmpty =>
                p.parameterList.clauses.head.parameters.length == 1
              case m: PsiMethod => m.getParameterList.getParameters.length == 1
              case _ => false
            }
          }).map(r => r.copy(tuplingUsed = true))
          if (filtered2.isEmpty) return input.map(r => r.copy(notCheckedResolveResult = true))
          return filtered2
        }
        return input.map(r => r.copy(notCheckedResolveResult = true))
      }
      else return filtered
    }

    if (filtered.isEmpty && mapped.isEmpty) input.map(r => r.copy(notCheckedResolveResult = true))
    else if (filtered.isEmpty) mapped
    else {
      val len = if (argumentClauses.isEmpty) 0 else argumentClauses.head.length
      if (filtered.size == 1) return filtered
      MostSpecificUtil(ref, len).mostSpecificForResolveResult(filtered, hasTypeParametersCall = typeArgElements.nonEmpty) match {
        case Some(r) => HashSet(r)
        case None => filtered
      }
    }
  }
}
