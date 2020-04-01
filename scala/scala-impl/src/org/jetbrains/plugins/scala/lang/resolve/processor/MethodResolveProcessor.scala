package org.jetbrains.plugins.scala
package lang
package resolve
package processor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil.isContextAncestor
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
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
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.{ScMethodType, ScTypePolymorphicType}
import org.jetbrains.plugins.scala.lang.resolve.MethodTypeProvider._
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectPsiElementExt}
import org.jetbrains.plugins.scala.util.SAMUtil

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

  private def isUpdate: Boolean =
    ref != null && (ref.getContext match {
      case call: ScMethodCall => call.isUpdateCall
      case _                  => false
    })

  def isDynamic: Boolean = nameArgForDynamic.nonEmpty

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {

    def implFunction: Option[ScalaResolveResult] = state.implicitConversion

    def implType: Option[ScType] = state.implicitType

    def isNamedParameter: Boolean = state.isNamedParameter
    def fromType: Option[ScType] = state.fromType
    def unresolvedTypeParameters: Option[Seq[TypeParameter]] = state.unresolvedTypeParams
    def renamed: Option[String] = state.renamed
    def forwardReference: Boolean = state.isForwardRef
    def importsUsed = state.importsUsed

    if (nameMatches(namedElement) || constructorResolve) {
      val accessible = isNamedParameter || isAccessible(namedElement, ref)
      if (accessibility && !accessible) return true

      val s = state.substitutorWithThisType

      namedElement match {
        case m: PsiMethod =>
          addResult(new ScalaResolveResult(m, s, importsUsed, renamed,
            implicitConversion = implFunction, implicitType = implType, fromType = fromType, isAccessible = accessible,
            isForwardReference = forwardReference, unresolvedTypeParameters = unresolvedTypeParameters))
        case _: ScClass =>
        case o: ScObject if o.isPackageObject =>  // do not resolve to package object
        case obj: ScObject if ref.getParent.isInstanceOf[ScMethodCall] || ref.getParent.isInstanceOf[ScGenericCall] =>
          val functionName = if (isUpdate) "update" else "apply"
          val typeResult = fromType match {
            case Some(tp) => Right(ScProjectionType(tp, obj))
            case _ => obj.`type`()
          }
          val processor = new CollectMethodsProcessor(ref, functionName)

          // keep information about imports for proper precedence
          val newState = ScalaResolveState.withImportsUsed(state.importsUsed)
          typeResult.foreach(t => processor.processType(t, ref, newState))

          val sigs = processor.candidatesS.flatMap {
            case ScalaResolveResult(meth: PsiMethod, subst) => Some((meth, subst))
            case _ => None
          }.toSeq
          val seq = sigs.map {
            case (m, subst) =>
              new ScalaResolveResult(m, subst, importsUsed, renamed,
                implicitConversion = implFunction, implicitType = implType, fromType = fromType, parentElement = Some(obj),
                isAccessible = accessible && isAccessible(m, ref), isForwardReference = forwardReference,
                unresolvedTypeParameters = unresolvedTypeParameters)
          }.filter { r => !accessibility || r.isAccessible }
          if (seq.nonEmpty) addResults(seq)
          else addResult(new ScalaResolveResult(namedElement, s, importsUsed, renamed,
            implicitConversion = implFunction, implicitType = implType, isNamedParameter = isNamedParameter,
            fromType = fromType, isAccessible = accessible, isForwardReference = forwardReference,
            unresolvedTypeParameters = unresolvedTypeParameters))
        case synthetic: ScSyntheticFunction =>
          addResult(new ScalaResolveResult(synthetic, s, importsUsed, renamed,
            implicitConversion = implFunction, implicitType = implType, fromType = fromType, isAccessible = accessible,
            isForwardReference = forwardReference, unresolvedTypeParameters = unresolvedTypeParameters))
        case pack: PsiPackage =>
          addResult(new ScalaResolveResult(ScPackageImpl(pack), s, importsUsed, renamed,
            implicitConversion = implFunction, implicitType = implType, fromType = fromType, isAccessible = accessible,
            isForwardReference = forwardReference, unresolvedTypeParameters = unresolvedTypeParameters))
        case _ =>
          addResult(new ScalaResolveResult(namedElement, s, importsUsed, renamed,
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
    } else {
      MethodResolveProcessor.candidates(this, input)
    }
  }
}

object MethodResolveProcessor {
  private lazy val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.lang.resolve.processor.MethodResolveProcessor")

  private def problemsFor(
    c:                      ScalaResolveResult,
    checkWithImplicits:     Boolean,
    ref:                    PsiElement,
    argumentClauses:        List[Seq[Expression]],
    typeArgElements:        Seq[ScTypeElement],
    selfConstructorResolve: Boolean,
    prevTypeInfo:           Seq[TypeParameter],
    expectedOption:         () => Option[ScType],
    isUnderscore:           Boolean,
    isShapeResolve:         Boolean
  ): ConformanceExtResult = {

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

    val unresovledTps = c.unresolvedTypeParameters.getOrElse(Seq.empty)

    val substitutor =
      tempSubstitutor.followed(ScSubstitutor.bind(prevTypeInfo ++ unresovledTps)(UndefinedType(_)))

    val typeParameters: Seq[TypeParameter] = prevTypeInfo ++ (element match {
      case fun: ScFunction => fun.typeParameters.map(TypeParameter(_))
      case fun: PsiMethod  => fun.getTypeParameters.map(TypeParameter(_)).toSeq
      case _               => Seq.empty
    })

    def addExpectedTypeProblems(
      eOption: Option[ScType]               = expectedOption(),
      result:  Option[ConformanceExtResult] = None
    ): ConformanceExtResult = {
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
          val constraints =
            if (expected.equiv(api.Unit)) aResult.constraints
            else                          aResult.constraints + conformance.constraints
          aResult.copy(problems, constraints)
        case None => ConformanceExtResult(problems, conformance.constraints)
      }
    }

    def checkFunction(fun: PsiNamedElement, isPolymorphic: Boolean): ConformanceExtResult = {
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

      def methodTypeWithoutImplicits(tpe: ScType): ScType = tpe match {
        case ScMethodType(inner, _, true) => inner
        case t @ ScMethodType(inner, ps, false) =>
          ScMethodType(methodTypeWithoutImplicits(inner), ps, isImplicit = false)(t.elementScope)
        case ScTypePolymorphicType(internalType, tparams) =>
          ScTypePolymorphicType(methodTypeWithoutImplicits(internalType), tparams)
        case t => t
      }

      def checkEtaExpandedReference(fun: PsiNamedElement, expectedType: ScType): ConformanceExtResult = {
        val maybeMethodType = fun match {
          case m: PsiMethod => m.methodTypeProvider(ref.elementScope).polymorphicType().toOption
          case fun: ScFun   => fun.polymorphicType().toOption
          case _            => None
        }

        val typeAfterConversions =
          maybeMethodType.map(methodTypeWithoutImplicits).flatMap { tpe =>
            val withUndefParams = tpe match {
              case ptpe: ScTypePolymorphicType =>
                val subst = ScSubstitutor.bind(ptpe.typeParameters)(UndefinedType(_))
                subst(ptpe.internalType)
              case tpe => tpe
            }

            val expr = Expression(withUndefParams.inferValueType, ref)

            expr.getTypeAfterImplicitConversion(
              checkImplicits = true,
              isShape        = false,
              Option(expectedType)
            ).tr.toOption
          }

        val constraints =
          typeAfterConversions.map(tpe =>
            substitutor(tpe).isConservativelyCompatible(expectedType)
          ).getOrElse(ConstraintsResult.Left)

        constraints match {
          case ConstraintsResult.Left => ConformanceExtResult(Seq(ExpectedTypeMismatch))
          case cs: ConstraintSystem   => ConformanceExtResult(problems, cs)
        }
      }

      fun match {
        case fun: ScFunction if fun.paramClauses.clauses.isEmpty =>
          return addExpectedTypeProblems()
        case fun: ScFun if fun.paramClauses.isEmpty =>
          return addExpectedTypeProblems()
        case _ =>
      }

      val functionLikeType = FunctionLikeType(fun)

      expectedOption().map {
        case abs: ScAbstractType => abs.simplifyType
        case t                   => t
      } match {
        case Some(etpe @ functionLikeType(_, _, paramTpes)) =>
          val doNotEtaExpand = isPolymorphic && paramTpes.exists {
            case FullyAbstractType() => true
            case _                   => false
          }

          if (doNotEtaExpand) default()
          else                checkEtaExpandedReference(fun, etpe)
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
          Compatibility.compatible(constr, substitutor, argumentClauses, checkWithImplicits, isShapeResolve)
        problems ++= result.problems
        result.copy(problems)
      } else {
        val problem = InternalApplicabilityProblem(
          ScalaBundle.message("not.all.type.parameters.are.defined", typeArgElements.mkString(", "), classTypeParameters.mkString(", "))
        )
        LOG.warn(problem.toString)
        problems += problem
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

    def checkSimpleApplication(
      e:          PsiNamedElement,
      typeParams: Seq[PsiTypeParameter]
    ): ConformanceExtResult = {
      val typeArgCount   = typeArgElements.length
      val typeParamCount = typeParams.length

      if (typeArgCount > 0 && typeArgCount != typeParamCount) {
        if (typeParamCount == 0) problems += DoesNotTakeTypeParameters
        else if (typeParamCount < typeArgCount)
          problems ++= typeArgElements.drop(typeParamCount).map(ExcessTypeArgument)
        else
          problems ++= typeParams
            .drop(typeArgCount)
            .map(ptp => MissedTypeParameter(TypeParameter(ptp)))

        addExpectedTypeProblems()
      } else {
        val args                 = argumentClauses.headOption.toList
        val expectedTypeProblems = addExpectedTypeProblems()

        val expectedTypeSubst =
          expectedTypeProblems.constraints.substitutionBounds(canThrowSCE = false)

        val substitutorWithExpected =
          expectedTypeSubst.fold(substitutor)(bounds => substitutor.followed(bounds.substitutor))

        val argsConformance =
          Compatibility.compatible(
            e,
            substitutorWithExpected,
            args,
            checkWithImplicits,
            isShapeResolve,
            ref
          )

        problems ++= argsConformance.problems
        argsConformance.copy(problems = problems)
      }
    }

    val result = element match {
      //objects
      case obj: ScObject =>
        if (argumentClauses.isEmpty) {
          expectedOption().map(_.removeAbstracts) match {
            case Some(FunctionType(_, _)) => problems += ExpectedTypeMismatch
            case Some(tp: ScType) if obj.isSAMEnabled =>
              SAMUtil.toSAMType(tp, obj) match {
                case Some(FunctionType(_, _)) => problems += ExpectedTypeMismatch
                case _                        => ()
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
      case f: ScMethodLike if hasMalformedSignature(f) =>
        problems += MalformedDefinition(f.name)
        ConformanceExtResult(problems)
      case ScalaConstructor(constructor) => scalaConstructorCompatibility(constructor)
      case JavaConstructor(constructor) => javaConstructorCompatibility(constructor)
      case fun: ScFunction if (typeArgElements.isEmpty ||
              typeArgElements.length == fun.typeParameters.length) && fun.paramClauses.clauses.length == 1 &&
              fun.paramClauses.clauses.head.isImplicit &&
              argumentClauses.isEmpty =>
        addExpectedTypeProblems()
      //eta expansion
      case (fun: ScTypeParametersOwner) && (_: PsiNamedElement)
          if (typeArgElements.isEmpty ||
            typeArgElements.length == fun.typeParameters.length) && argumentClauses.isEmpty =>
        checkFunction(fun, fun.typeParameters.nonEmpty)
      case (fun: PsiTypeParameterListOwner) && (_: PsiNamedElement)
          if (typeArgElements.isEmpty ||
            typeArgElements.length == fun.getTypeParameters.length) && argumentClauses.isEmpty =>
        checkFunction(fun, fun.getTypeParameters.nonEmpty)
      //simple application including empty application
      case tpOwner: ScTypeParametersOwner with PsiNamedElement     => checkSimpleApplication(tpOwner, tpOwner.typeParameters)
      case tpOwner: PsiTypeParameterListOwner with PsiNamedElement => checkSimpleApplication(tpOwner, tpOwner.getTypeParameters)
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
            case _                   => None
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

  @scala.annotation.tailrec
  def candidates(
    proc:            MethodResolveProcessor,
    _input:          Set[ScalaResolveResult],
    useExpectedType: Boolean = true
  ): Set[ScalaResolveResult] = {
    import proc.{candidates => _, _}

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

    var mapped   = checkResultsApplicability(proc, input, checkWithImplicits = false, useExpectedType)
    var filtered = mapped.filter(_.isApplicableInternal(withExpectedType = true))

    // filter to check for wrong parameter names
    if (argumentClauses.nonEmpty && filtered.nonEmpty) {
      argumentClauses.head.
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
      mapped = checkResultsApplicability(proc, input, checkWithImplicits = true, useExpectedType)
      filtered = mapped.filter(_.isApplicableInternal(withExpectedType = true))
      if (filtered.isEmpty) filtered = mapped.filter(_.isApplicableInternal(withExpectedType = false))
    }

    val onlyValues = mapped.forall { r =>
      r.element match {
        case _: ScFunction => false
        case _: ScReferencePattern if r.innerResolveResult.exists(_.element.getName == "apply") =>
          true
        case _: ScTypedDefinition => r.innerResolveResult.isEmpty && r.problems.size == 1
        case _                    => false
      }
    }

    if (filtered.isEmpty && onlyValues) {
      //possible implicit conversions in ScMethodCall
      return input.map(_.copy(notCheckedResolveResult = true))
    } else if (!onlyValues) {
      //in this case all values are not applicable
      mapped = mapped.map(
        r =>
          if (r.isApplicable())
            r.innerResolveResult match {
              case Some(rr) => r.copy(problems = rr.problems)
              case _        => r
            }
          else r
      )
    }

    //remove default parameters alternatives
    if (filtered.size > 1 && !isShapeResolve)
      filtered = filtered.filter(
        r =>
          r.innerResolveResult match {
            case Some(rr) => !rr.defaultParameterUsed
            case None     => !r.defaultParameterUsed
          }
      )

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
    else if (filtered.isEmpty)
      if (useExpectedType) candidates(proc, _input, useExpectedType = false)
      else                 mapped
    else {
      val len =
        if (argumentClauses.isEmpty) 0
        else                         argumentClauses.head.length

      if (filtered.size == 1) filtered
      else
        MostSpecificUtil(ref, len).mostSpecificForResolveResult(filtered) match {
          case Some(r) => Set(r)
          case None    => filtered
        }
    }
  }

  private def expandApplyOrUpdateMethod(
    r:    ScalaResolveResult,
    proc: MethodResolveProcessor
  ): Set[(ScalaResolveResult, Boolean)] = {
    import proc._

    def applyMethodsFor(tp: ScType): Set[(ScalaResolveResult, Boolean)] = {
      val (substitutor, cleanTypeArguments) =
        r.element match {
          case owner: ScTypeParametersOwner if owner.typeParameters.nonEmpty =>
            (undefinedSubstitutor(owner, r.substitutor, selfConstructorResolve = false, typeArgElements), true)
          case owner: PsiTypeParameterListOwner if owner.getTypeParameters.length > 0 =>
            (undefinedSubstitutor(owner, r.substitutor, selfConstructorResolve = false, typeArgElements), true)
          case _ => (r.substitutor, false)
        }

      val methodName = if (isUpdate) "update" else "apply"
      val processor  = new CollectMethodsProcessor(ref, methodName)
      processor.processType(substitutor(tp), ref)

      val cands =
        processor.candidatesS.map(rr => (r.copy(innerResolveResult = Some(rr)), cleanTypeArguments))

      if (cands.isEmpty) Set((r, false))
      else               cands
    }

    if (argumentClauses.isEmpty) Set((r, false))
    else
      r.element match {
        case f: ScFunction if f.hasParameterClause => Set((r, false))
        case b: ScTypedDefinition =>
          val tpe = b.`type`().toOption
          tpe.map(applyMethodsFor).getOrElse(Set.empty)
        case b: PsiField => // See SCL-3055
          applyMethodsFor(b.getType.toScType())
        case _ => Set((r, false))
      }
  }

  private def checkResultsApplicability(
    proc:               MethodResolveProcessor,
    input:              Set[ScalaResolveResult],
    checkWithImplicits: Boolean,
    useExpectedType:    Boolean
  ): Set[ScalaResolveResult] = {
    import proc._

    val expanded = input.flatMap(expandApplyOrUpdateMethod(_, proc)).iterator
    var results  = ArrayBuffer.empty[ScalaResolveResult]

    //problemsFor make all the work, wrapping it in scala collection API adds 9 unnecessary methods to the stacktrace
    while (expanded.hasNext) {
      val (r, undefineTypeParams) = expanded.next()
      val typeArgElems =
        if (undefineTypeParams) Seq.empty
        else                    typeArgElements

      val conformanceResult = problemsFor(
        r,
        checkWithImplicits,
        ref,
        argumentClauses,
        typeArgElems,
        selfConstructorResolve,
        prevTypeInfo,
        if (useExpectedType) expectedOption else () => None,
        isUnderscore,
        isShapeResolve
      )

      val result = r.innerResolveResult match {
        case Some(rr) if argumentClauses.nonEmpty =>
          val innerCopy = rr.copy(
            problems             = conformanceResult.problems,
            defaultParameterUsed = conformanceResult.defaultParameterUsed
          )
          r.copy(innerResolveResult = Some(innerCopy))
        case _ =>
          r.copy(
            problems             = conformanceResult.problems,
            defaultParameterUsed = conformanceResult.defaultParameterUsed,
            resultUndef          = Some(conformanceResult.constraints)
          )
      }

      results += result
    }

    results.toSet
  }

  //Signature has repeated param, which is not the last one
  private def hasMalformedSignature(ml: ScMethodLike) = ml.parameterList.clauses.exists {
    _.parameters.dropRight(1).exists(_.isRepeatedParameter)
  }
}
