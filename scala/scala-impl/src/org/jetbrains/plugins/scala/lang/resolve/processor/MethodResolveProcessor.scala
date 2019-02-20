package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.isContextAncestor
import org.jetbrains.plugins.scala.caches.CachesUtil._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base._
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.TypeParamIdOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic.ScSyntheticFunction
import org.jetbrains.plugins.scala.lang.psi.types.Compatibility.{ConformanceExtResult, Expression}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScProjectionType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt}

import scala.collection.Set
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
                             val nameArgForDynamic: Option[String] = None)
  extends ResolveProcessor(kinds, ref, refName) {

  private def isUpdate: Boolean = {
    if (ref == null) return false
    ref.getContext match {
      case call: ScMethodCall => call.isUpdateCall
      case _ => false
    }
  }

  def isDynamic: Boolean = nameArgForDynamic.nonEmpty

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {
    def implicitConversionClass: Option[PsiClass] = state.get(IMPLICIT_RESOLUTION).toOption

    def implFunction: Option[ScalaResolveResult] = state.get(IMPLICIT_FUNCTION).toOption

    def implType: Option[ScType] = state.get(IMPLICIT_TYPE).toOption

    def isNamedParameter: Boolean = state.get(NAMED_PARAM_KEY).toOption.exists(_.booleanValue)
    def fromType: Option[ScType] = state.get(BaseProcessor.FROM_TYPE_KEY).toOption
    def unresolvedTypeParameters: Option[Seq[TypeParameter]] = state.get(BaseProcessor.UNRESOLVED_TYPE_PARAMETERS_KEY).toOption
    def nameShadow: Option[String] = Option(state.get(ResolverEnv.nameKey))
    def forwardReference: Boolean = isForwardReference(state)

    if (nameMatches(namedElement) || constructorResolve) {
      val accessible = isNamedParameter || isAccessible(namedElement, ref)
      if (accessibility && !accessible) return true

      val s = fromType match {
        case Some(tp) => getSubst(state).followUpdateThisType(tp)
        case _ => getSubst(state)
      }
      namedElement match {
        case m: PsiMethod =>
          addResult(new ScalaResolveResult(m, s, getImports(state), nameShadow, implicitConversionClass,
            implicitConversion = implFunction, implicitType = implType, fromType = fromType, isAccessible = accessible,
            isForwardReference = forwardReference, unresolvedTypeParameters = unresolvedTypeParameters))
        case _: ScClass =>
        case o: ScObject if o.isPackageObject =>  // do not resolve to package object
        case obj: ScObject if ref.getParent.isInstanceOf[ScMethodCall] || ref.getParent.isInstanceOf[ScGenericCall] =>
          val functionName = if (isUpdate) "update" else "apply"
          val typeResult = getFromType(state) match {
            case Some(tp) => Right(ScProjectionType(tp, obj))
            case _ => obj.`type`()
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
                implicitConversion = implFunction, implicitType = implType, fromType = fromType, parentElement = Some(obj),
                isAccessible = accessible && isAccessible(m, ref), isForwardReference = forwardReference,
                unresolvedTypeParameters = unresolvedTypeParameters)
          }.filter { r => !accessibility || r.isAccessible }
          if (seq.nonEmpty) addResults(seq)
          else addResult(new ScalaResolveResult(namedElement, s, getImports(state), nameShadow, implicitConversionClass,
            implicitConversion = implFunction, implicitType = implType, isNamedParameter = isNamedParameter,
            fromType = fromType, isAccessible = accessible, isForwardReference = forwardReference,
            unresolvedTypeParameters = unresolvedTypeParameters))
        case synthetic: ScSyntheticFunction =>
          addResult(new ScalaResolveResult(synthetic, s, getImports(state), nameShadow, implicitConversionClass,
            implicitConversion = implFunction, implicitType = implType, fromType = fromType, isAccessible = accessible,
            isForwardReference = forwardReference, unresolvedTypeParameters = unresolvedTypeParameters))
        case pack: PsiPackage =>
          addResult(new ScalaResolveResult(ScPackageImpl(pack), s, getImports(state), nameShadow, implicitConversionClass,
            implicitConversion = implFunction, implicitType = implType, fromType = fromType, isAccessible = accessible,
            isForwardReference = forwardReference, unresolvedTypeParameters = unresolvedTypeParameters))
        case _ =>
          addResult(new ScalaResolveResult(namedElement, s, getImports(state), nameShadow, implicitConversionClass,
            implicitConversion = implFunction, implicitType = implType, isNamedParameter = isNamedParameter,
            fromType = fromType, isAccessible = accessible, isForwardReference = forwardReference,
            unresolvedTypeParameters = unresolvedTypeParameters))
      }
    }
    true
  }



  override def candidatesS: Set[ScalaResolveResult] = {
    if (isDynamic) {
      collectCandidates(super.candidatesS.map(_.copy(nameArgForDynamic = nameArgForDynamic))).filter(_.isApplicable())
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
        val tpl = ScalaPsiUtil.tupled(argumentClauses.head, ref)
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
                          isShapeResolve: Boolean): ConformanceExtResult = {

    implicit val ctx: ProjectContext = c.element

    val problems = new ArrayBuffer[ApplicabilityProblem]()

    val realResolveResult = c.innerResolveResult match {
      case Some(rr) => rr
      case _ => c
    }
    val element = realResolveResult.element
    val s = realResolveResult.substitutor

    val elementsForUndefining = element match {
      case ScalaConstructor(_) if !selfConstructorResolve => Seq(realResolveResult.getActualElement)
      case Constructor(_) =>
        Seq(realResolveResult.getActualElement, element).distinct
      case _ => Seq(element) //do not
    }
    val iterator = elementsForUndefining.iterator
    var tempSubstitutor: ScSubstitutor = ScSubstitutor.empty
    while (iterator.hasNext) {
      val element = iterator.next()
      tempSubstitutor = tempSubstitutor.followed(
        undefinedSubstitutor(element, s, selfConstructorResolve, typeArgElements)
      )
    }

    val substitutor = tempSubstitutor.followed(ScSubstitutor.bind(prevTypeInfo)(UndefinedType(_)))

    val typeParameters: Seq[TypeParameter] = prevTypeInfo ++ (element match {
      case fun: ScFunction => fun.typeParameters.map(TypeParameter(_))
      case fun: PsiMethod => fun.getTypeParameters.map(TypeParameter(_)).toSeq
      case _ => Seq.empty
    })

    def addExpectedTypeProblems(eOption: Option[ScType] = expectedOption(), result: Option[ConformanceExtResult] = None): ConformanceExtResult = {
      if (eOption.isEmpty) return result.map(_.copy(problems)).getOrElse(ConformanceExtResult(problems))

      val expected = eOption.get
      val retType: ScType = element match {
        case f: ScFunction if f.paramClauses.clauses.length > 1 &&
          !f.paramClauses.clauses(1).isImplicit =>
          problems += ExpectedTypeMismatch //do not check expected types for more than one param clauses
          Nothing
        case f: ScFunction => substitutor(f.returnType.getOrNothing)
        case f: ScFun => substitutor(f.retType)
        case m: PsiMethod =>
          Option(m.getReturnType).map { rt =>
            substitutor(rt.toScType())
          }.getOrElse(Nothing)
        case _ => Nothing
      }
      val conformance = retType.typeSystem.conformsInner(expected, retType)
      if (conformance.isLeft && !expected.equiv(api.Unit)) {
        problems += ExpectedTypeMismatch
      }
      result match {
        case Some(aResult) =>
          val substitutor =
            if (expected.equiv(api.Unit)) aResult.constraints
            else aResult.constraints + conformance.constraints
          aResult.copy(problems, substitutor)
        case None => ConformanceExtResult(problems, conformance.constraints)
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
          case method: PsiMethod if method.parameters.isEmpty ||
            isUnderscore =>
            addExpectedTypeProblems()
          case _ =>
            problems += MissedParametersClause(null)
            addExpectedTypeProblems()
        }
      }

      def processFunctionType(retType: ScType, params: Seq[ScType]): ConformanceExtResult = {
        val args = params.map(new Expression(_))
        val result = Compatibility.compatible(fun, substitutor, List(args), checkWithImplicits = false,
        scope = ref.resolveScope, isShapesResolve = isShapeResolve)
        problems ++= result.problems
        addExpectedTypeProblems(Some(retType), result = Some(result))
      }

      fun match {
        case fun: ScFunction if fun.paramClauses.clauses.isEmpty =>
          return addExpectedTypeProblems()
        case fun: ScFun if fun.paramClauses.isEmpty =>
          return addExpectedTypeProblems()
        case _ =>
      }

      expectedOption().map {
        case a: ScAbstractType => a.simplifyType
        case f => f
      } match {
        case Some(FunctionType(retType, params)) => processFunctionType(retType, params)
        case Some(tp: ScType) if fun.isSAMEnabled =>
          ScalaPsiUtil.toSAMType(tp, fun) match {
            case Some(FunctionType(retType, params)) => processFunctionType(retType, params)
            case _ => default()
          }
        case _ => default()
      }
    }

    def constructorCompatibility(constr: PsiMethod, classTypeParameters: Seq[PsiTypeParameter]): ConformanceExtResult = {
      def isSubstituted(p: PsiTypeParameter): Boolean = {
        val tpt = TypeParameterType(p)
        substitutor(tpt) != tpt
      }

      val allTypeParametersDefined =
        classTypeParameters.forall { p =>
          isContextAncestor(p.getOwner, ref, true) || isSubstituted(p)
        }

      if (typeArgElements.isEmpty || allTypeParametersDefined) {
        val result =
          Compatibility.compatible(constr, substitutor, argumentClauses, checkWithImplicits,
            ref.resolveScope, isShapeResolve)
        problems ++= result.problems
        result.copy(problems)
      } else {
        problems += new ApplicabilityProblem("2")
        ConformanceExtResult(problems)
      }
    }

    def scalaConstructorCompatibility(constr: ScMethodLike): ConformanceExtResult = {
      val classTypeParameters = constr.getClassTypeParameters.map(_.typeParameters).getOrElse(Seq())
      constructorCompatibility(constr, classTypeParameters)
    }

    def javaConstructorCompatibility(constr: PsiMethod): ConformanceExtResult = {
      val classTypeParmeters = constr.containingClass.getTypeParameters
      constructorCompatibility(constr, classTypeParmeters)
    }

    val result = element match {
      //objects
      case obj: ScObject =>
        if (argumentClauses.isEmpty) {
          expectedOption().map(_.removeAbstracts) match {
            case Some(FunctionType(_, params)) =>
              problems += ExpectedTypeMismatch
            case Some(tp: ScType) if obj.isSAMEnabled =>
              ScalaPsiUtil.toSAMType(tp, obj) match {
                case Some(FunctionType(_, params)) =>
                  problems += ExpectedTypeMismatch
                case _ =>
              }
            case _ =>
          }
        } else {
          problems += new DoesNotTakeParameters
        }
        ConformanceExtResult(problems)
      case _: PsiClass =>
        ConformanceExtResult(problems)
      case _: ScTypeAlias =>
        ConformanceExtResult(problems)
      //Implicit Application
      case f: ScMethodLike if f.hasMalformedSignature =>
        problems += new MalformedDefinition
        ConformanceExtResult(problems)
      case ScalaConstructor(constructor) => scalaConstructorCompatibility(constructor)
      case JavaConstructor(constructor) => javaConstructorCompatibility(constructor)
      case fun: ScFunction if (typeArgElements.isEmpty ||
              typeArgElements.length == fun.typeParameters.length) && fun.paramClauses.clauses.length == 1 &&
              fun.paramClauses.clauses.head.isImplicit &&
              argumentClauses.isEmpty =>
        addExpectedTypeProblems()
      //eta expansion
      case fun: ScTypeParametersOwner if (typeArgElements.isEmpty ||
              typeArgElements.length == fun.typeParameters.length) && argumentClauses.isEmpty &&
              fun.isInstanceOf[PsiNamedElement] =>
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
        } else {
          val result =
            Compatibility.compatible(tp.asInstanceOf[PsiNamedElement], substitutor, args, checkWithImplicits,
              ref.resolveScope, isShapeResolve, ref)
          problems ++= result.problems
          addExpectedTypeProblems(result = Some(result))
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
        } else {
          val args = argumentClauses.headOption.toList
          val result =
            Compatibility.compatible(tp, substitutor, args, checkWithImplicits,
              ref.resolveScope, isShapeResolve)
          problems ++= result.problems
          addExpectedTypeProblems(result = Some(result))
        }
      case _ =>
        if (typeArgElements.nonEmpty) problems += DoesNotTakeTypeParameters
        if (argumentClauses.nonEmpty) problems += new DoesNotTakeParameters
        addExpectedTypeProblems()
    }

    if (result.problems.forall(_ == ExpectedTypeMismatch)) {
      val maybeResult = result.constraints match {
        case undefined@ConstraintSystem(newSubstitutor) =>
          val typeParamIds = typeParameters.map {
            _.typeParamId
          }.toSet

          var uSubst = undefined
          for (TypeParameter(tParam, _, lowerType, upperType) <- typeParameters) {
            val typeParamId = tParam.typeParamId

            if (!lowerType.isNothing) {
              s(newSubstitutor(lowerType)) match {
                case lower if !lower.hasRecursiveTypeParameters(typeParamIds) =>
                  uSubst = uSubst.withLower(typeParamId, lower)
                    .withTypeParamId(typeParamId)
                case _ =>
              }
            }

            if (!upperType.isAny) {
              s(newSubstitutor(upperType)) match {
                case upper if !upper.hasRecursiveTypeParameters(typeParamIds) =>
                  uSubst = uSubst.withUpper(typeParamId, upper)
                    .withTypeParamId(typeParamId)
                case _ =>
              }
            }
          }

          uSubst match {
            case ConstraintSystem(_) => Some(result)
            case _ => None
          }
        case _ => None
      }

      maybeResult.getOrElse {
        result.copy(problems = Seq(WrongTypeParameterInferred))
      }
    } else result
  }

  // TODO clean this up
  def undefinedSubstitutor(element: PsiNamedElement, s: ScSubstitutor, selfConstructorResolve: Boolean,
                           typeArgElements: Seq[ScTypeElement]): ScSubstitutor = {
    if (selfConstructorResolve) return ScSubstitutor.empty

    implicit val ctx: ProjectContext = element

    val maybeTypeParameters: Option[Seq[PsiTypeParameter]] = element match {
      case t: ScTypeParametersOwner => Some(t.typeParameters)
      case p: PsiTypeParameterListOwner => Some(p.getTypeParameters)
      case _ => None
    }
    maybeTypeParameters match {
      case Some(typeParameters: Seq[PsiTypeParameter]) =>
        val follower =
          if (typeArgElements.nonEmpty && typeParameters.length == typeArgElements.length)
            ScSubstitutor.bind(typeParameters, typeArgElements)(_.calcType)
          else
            ScSubstitutor.bind(typeParameters)(UndefinedType(_))

        s.followed(follower)
      case _ => s
    }
  }

  def candidates(proc: MethodResolveProcessor, _input: Set[ScalaResolveResult]): Set[ScalaResolveResult] = {
    import proc._

    //We want to leave only fields and properties from inherited classes, this is important, because
    //field in base class is shadowed by private field from inherited class
    val input: Set[ScalaResolveResult] = _input.filter { r =>
        r.element match {
          case f: ScFunction if f.hasParameterClause => true
          case b: ScTypedDefinition =>
            b.nameContext match {
              case m: ScMember =>
                val clazz1: ScTemplateDefinition = m.containingClass
                if (clazz1 == null) true
                else {
                  _input.forall { r2 =>
                      r2.element match {
                        case f: ScFunction if f.hasParameterClause => true
                        case b2: ScTypedDefinition =>
                          b2.nameContext match {
                            case m2: ScMember =>
                              val clazz2: ScTemplateDefinition = m2.containingClass
                              if (clazz2 == null) true
                              else clazz1.sameOrInheritor(clazz2)
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
            r.element match {
              case owner: ScTypeParametersOwner if owner.typeParameters.nonEmpty =>
                (undefinedSubstitutor(owner, r.substitutor, false, typeArgElements), true)
              case owner: PsiTypeParameterListOwner if owner.getTypeParameters.length > 0 =>
                (undefinedSubstitutor(owner, r.substitutor, false, typeArgElements), true)
              case _ => (r.substitutor, false)
            }
        }

        val processor = new CollectMethodsProcessor(ref, "apply")
        processor.processType(substitutor(tp), ref.asInstanceOf[ScalaPsiElement])
        val cands = processor.candidatesS.map(rr => (r.copy(innerResolveResult = Some(rr)), cleanTypeArguments))
        if (cands.isEmpty) Set((r, false)) else cands
      }

      if (argumentClauses.isEmpty) Set((r, false))
      else {
        r.element match {
          case f: ScFunction if f.hasParameterClause => Set((r, false))
          case b: ScTypedDefinition if argumentClauses.nonEmpty =>
            val tpe = b.`type`().toOption
            tpe.map(applyMethodsFor).getOrElse(Set.empty)
          case b: PsiField => // See SCL-3055
            applyMethodsFor(b.getType.toScType())
          case _ => Set((r, false))
        }
      }
    }

    def mapper(applicationImplicits: Boolean): Set[ScalaResolveResult] = {
      val expanded = input.flatMap(expand).iterator
      var results = ArrayBuffer.empty[ScalaResolveResult]

      //problemsFor make all the work, wrapping it in scala collection API adds 9 unnecessary methods to the stacktrace
      while (expanded.hasNext) {
        val (r, cleanTypeArguments) = expanded.next()
        val typeArgElems = if (cleanTypeArguments) Seq.empty else typeArgElements
        val pr = problemsFor(r, applicationImplicits, ref, argumentClauses, typeArgElems, selfConstructorResolve,
          prevTypeInfo, expectedOption, isUnderscore, isShapeResolve)

        val result = r.innerResolveResult match {
          case Some(rr) if argumentClauses.nonEmpty =>
            val innerCopy = rr.copy(problems = pr.problems, defaultParameterUsed = pr.defaultParameterUsed)
            r.copy(innerResolveResult = Some(innerCopy))
          case _ => r.copy(problems = pr.problems, defaultParameterUsed = pr.defaultParameterUsed, resultUndef = Some(pr.constraints))
        }
        results += result
      }

      results.toSet
    }

    var mapped = mapper(applicationImplicits = false)
    var filtered = mapped.filter(_.isApplicableInternal(withExpectedType = true))

    // filter to check for wrong parameter names
    if (argumentClauses.nonEmpty && filtered.nonEmpty) {
      argumentClauses.head.map(assignmemt => assignmemt.expr).
        collect { case assignment: ScAssignment => assignment.referenceName }.foreach { listOfNames =>
        filtered = filtered.filter { r =>
          r.element match {
            case func: ScFunction if func.hasParameterClause =>
              val paramsNames = func.parameterList.params.map(_.name)
              listOfNames.find(str => !paramsNames.contains(str)).forall(_.isEmpty)
            case _ => false
          }
        }
      }
    }

    if (filtered.isEmpty) filtered = mapped.filter(_.isApplicableInternal(withExpectedType = false))

    if (filtered.isEmpty && !noImplicitsForArgs) {
      //check with implicits
      mapped = mapper(applicationImplicits = true)
      filtered = mapped.filter(_.isApplicableInternal(withExpectedType = true))
      if (filtered.isEmpty) filtered = mapped.filter(_.isApplicableInternal(withExpectedType = false))
    }

    val onlyValues = mapped.forall { r =>
      r.element match {
        case _: ScFunction => false
        case _: ScReferencePattern if r.innerResolveResult.exists(_.element.getName == "apply") => true
        case _: ScTypedDefinition => r.innerResolveResult.isEmpty && r.problems.size == 1
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
              case m: PsiMethod => m.parameters.length == 1
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
        case Some(r) => Set(r)
        case None => filtered
      }
    }
  }
}
