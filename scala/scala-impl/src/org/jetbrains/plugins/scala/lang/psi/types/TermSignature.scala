package org.jetbrains.plugins.scala
package lang
package psi
package types

import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi._
import com.intellij.psi.util.MethodSignatureUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.inNameContext
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods.methodName
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScFieldId, ScMethodLike}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameters}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDeclaration, ScFunctionDefinition, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.light.{ScFunctionWrapper, ScPrimaryConstructorWrapper}
import org.jetbrains.plugins.scala.lang.psi.types.TermSignature._
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, FunctionType, PsiTypeParamatersExt, TypeParameter}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.SubtypeUpdater._
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.project.{ProjectContext, ProjectContextOwner}
import org.jetbrains.plugins.scala.util.HashBuilder._

import scala.annotation.tailrec
import scala.collection.{Seq, mutable}

class TermSignature(_name: String,
                    private val typesEval: Seq[Seq[() => ScType]],
                    private val tParams: Seq[TypeParameter],
                    override val substitutor: ScSubstitutor,
                    override val namedElement: PsiNamedElement,
                    val hasRepeatedParam: Array[Int] = Array.empty) extends Signature with ProjectContextOwner {

  override implicit def projectContext: ProjectContext = namedElement

  override val name: String = ScalaNamesUtil.clean(_name)

  val paramClauseSizes: Array[Int] = typesEval.map(_.length).toArray
  val paramLength: Int = paramClauseSizes.arraySum

  def substitutedTypes: Seq[Seq[() => ScType]] = typesEval.map(_.map(f => () => substitutor(f()).unpackedType))

  def typeParams: Seq[TypeParameter] = tParams.map(_.update(substitutor))

  def typeParamsLength: Int = tParams.length

  private def isField = namedElement.isInstanceOf[PsiField]

  override def equiv(other: Signature): Boolean = other match {
    case otherTerm: TermSignature =>
      ProgressManager.checkCanceled()

      paramLength == otherTerm.paramLength &&
        isField == otherTerm.isField &&
        name == otherTerm.name &&
        typeParamsLength == otherTerm.typeParamsLength &&
        (javaErasedEquiv(otherTerm) || paramTypesEquiv(otherTerm))

    case _ => false
  }

  def javaErasedEquiv(other: TermSignature): Boolean = {
    (this, other) match {
      case (ps1: PhysicalMethodSignature, ps2: PhysicalMethodSignature) if ps1.isJava && ps2.isJava =>
        implicit val elementScope: ElementScope = ps1.method.elementScope
        val psiSub1 = ScalaPsiUtil.getPsiSubstitutor(ps1.substitutor)
        val psiSub2 = ScalaPsiUtil.getPsiSubstitutor(ps2.substitutor)
        val psiSig1 = ps1.method.getSignature(psiSub1)
        val psiSig2 = ps2.method.getSignature(psiSub2)
        MethodSignatureUtil.METHOD_PARAMETERS_ERASURE_EQUALITY.equals(psiSig1, psiSig2)
      case _ => false
    }
  }

  def paramTypesEquiv(other: TermSignature): Boolean = {
    paramTypesEquivExtended(other, ConstraintSystem.empty, falseUndef = true).isRight
  }


  def paramTypesEquivExtended(
    other:       TermSignature,
    constraints: ConstraintSystem,
    falseUndef:  Boolean
  ): ConstraintsResult = {

    if (paramLength != other.paramLength ||
        paramLength > 0 && paramClauseSizes =!= other.paramClauseSizes ||
        hasRepeatedParam =!= other.hasRepeatedParam)
      return ConstraintsResult.Left

    val depParamTypeSubst   = depParamTypeSubstitutor(other)
    val unified             = other.substitutor.withBindings(typeParams, other.typeParams)
    val clauseIterator      = substitutedTypes.iterator
    val otherClauseIterator = other.substitutedTypes.iterator
    var lastConstraints     = constraints

    while (clauseIterator.hasNext && otherClauseIterator.hasNext) {
      val clause1            = clauseIterator.next()
      val clause2            = otherClauseIterator.next()
      val typesIterator      = clause1.iterator
      val otherTypesIterator = clause2.iterator

      while (typesIterator.hasNext && otherTypesIterator.hasNext) {
        val t1  = typesIterator.next()
        val t2  = otherTypesIterator.next()
        val tp1 = unified.followed(depParamTypeSubst)(t1())
        val tp2 = unified(t2())
        var t   = tp2.equiv(tp1, lastConstraints, falseUndef)

        if (t.isLeft && tp1.equiv(api.AnyRef) && this.isJava) {
          t = tp2.equiv(Any, lastConstraints, falseUndef)
        }
        if (t.isLeft && tp2.equiv(api.AnyRef) && other.isJava) {
          t = Any.equiv(tp1, lastConstraints, falseUndef)
        }
        if (t.isLeft) {
          return ConstraintsResult.Left
        }
        lastConstraints = t.constraints
      }
    }
    lastConstraints
  }

  override def equals(that: Any): Boolean = that match {
    case s: TermSignature => isCompatibleParameterSizes(s) && parameterlessKind == s.parameterlessKind && equiv(s)
    case _ => false
  }

  private def isCompatibleParameterSizes(other: TermSignature): Boolean = {
    if (paramLength == 0) other.paramLength == 0
    else paramClauseSizes === other.paramClauseSizes
  }

  private def parameterSizeHash: Int = {
    if (paramLength == 0) 0
    else paramClauseSizes.hash
  }

  private def depParamTypeSubstitutor(target: TermSignature): ScSubstitutor = {
    (namedElement, target.namedElement) match {
      case (from: ScFunction, to: ScFunction) =>
        val fromParams = from.effectiveParameterClauses.flatMap(_.effectiveParameters)
        val toParams = to.effectiveParameterClauses.flatMap(_.effectiveParameters)
        ScSubstitutor.paramToParam(fromParams, toParams)
      case _ =>
        ScSubstitutor.empty
    }
  }

  override def hashCode: Int = equivHashCode * 31 + parameterlessKind


  /**
    * Use it, while building class hierarchy.
    * Because for class hierarchy def foo(): Int is the same thing as def foo: Int and val foo: Int.
    */
  override def equivHashCode: Int = name #+ parameterSizeHash

  def isJava: Boolean = false

  def parameterlessKind: Int = {
    if (paramLength > 0) HasParameters
    else namedElement match {
      case f: ScFunction                => if (!f.hasParameterClause) Parameterless else EmptyParentheses
      case _: PsiMethod                 => EmptyParentheses
      case inNameContext(_: ScVariable) => Parameterless
      case _: ScNamedElement            => ScalaVal
      case _                            => JavaField
    }
  }

  //this method is used in conformance and is not symmetric:
  //   val x: {  def foo: Int } = new { val foo = 1 } is valid, but
  //   val x: {  val foo: Int } = new { def foo = 1 } is not
  def parameterlessCompatible(other: TermSignature): Boolean = {
    val thisKind = parameterlessKind
    val otherKind = other.parameterlessKind

    thisKind == otherKind || (thisKind == ScalaVal && otherKind == Parameterless)
  }

  override def toString = s"Signature($namedElement, $substitutor)"

  override def isAbstract: Boolean = namedElement match {
    case _: ScFunctionDeclaration => true
    case _: ScFunctionDefinition => false
    case _: ScFieldId => true
    case m: PsiModifierListOwner if m.hasModifierPropertyScala(PsiModifier.ABSTRACT) => true
    case _ => false
  }

  override def isImplicit: Boolean = ScalaPsiUtil.isImplicit(namedElement)

  override def isSynthetic: Boolean = namedElement match {
    case m: ScMember                => m.isSynthetic
    case inNameContext(m: ScMember) => m.isSynthetic
    case _                          => false
  }
}

object TermSignature {

  def apply(name: String, paramTypes: Seq[() => ScType], substitutor: ScSubstitutor, namedElement: PsiNamedElement): TermSignature =
    new TermSignature(name, List(paramTypes), Seq.empty, substitutor, namedElement)

  def apply(definition: PsiNamedElement, substitutor: ScSubstitutor = ScSubstitutor.empty): TermSignature = definition match {
    case function: ScFunction =>
      new TermSignature(
        function.name,
        PhysicalMethodSignature.typesEval(function),
        function.getTypeParameters.instantiate,
        substitutor,
        function,
        PhysicalMethodSignature.hasRepeatedParam(function)
      )
    case _ =>
      new TermSignature(
        definition.name,
        Seq.empty,
        Seq.empty,
        substitutor,
        definition
      )
  }

  def withoutParams(name: String, subst: ScSubstitutor, namedElement: PsiNamedElement): TermSignature =
    TermSignature(name, Seq.empty, subst, namedElement)

  def setter(name: String, definition: ScTypedDefinition, subst: ScSubstitutor = ScSubstitutor.empty): TermSignature = TermSignature(
    name,
    Seq(() => definition.`type`().getOrAny),
    subst,
    definition
  )

  def scalaSetter(definition: ScTypedDefinition, subst: ScSubstitutor = ScSubstitutor.empty): TermSignature =
    setter(methodName(definition.name, PropertyMethods.EQ), definition, subst)

  private val Parameterless = 1
  private val EmptyParentheses = 2
  private val ScalaVal = 3
  private val JavaField = 4
  private val HasParameters = 5
}

object PhysicalMethodSignature {
  @tailrec
  def typesEval(method: PsiMethod): List[Seq[() => ScType]] = method match {
    case fun: ScFunction =>
      fun.effectiveParameterClauses.toList
        .map(_.effectiveParameters.map(p => () => scalaParamType(p)))

    case wrapper: ScFunctionWrapper => typesEval(wrapper.delegate)
    case wrapper: ScPrimaryConstructorWrapper => typesEval(wrapper.delegate)
    case _ =>
      val lazyParamTypes = method.getParameterList match {
        case ps: ScParameters => ps.params.map(p => () => scalaParamType(p))
        case p => p.getParameters.toSeq.map(p => () => javaParamType(p))
      }
      List(lazyParamTypes)
  }

  def hasRepeatedParam(method: PsiMethod): Array[Int] = {
    val originalMethod = method match {
      case s: ScMethodLike                      => s
      case wrapper: ScFunctionWrapper           => wrapper.delegate
      case wrapper: ScPrimaryConstructorWrapper => wrapper.delegate
      case _                                    => method
    }

    originalMethod.getParameterList match {
      case p: ScParameters =>
        val params = p.params
        val res = mutable.ArrayBuffer.empty[Int]
        var i = 0
        while (i < params.length) {
          if (params(i).isRepeatedParameter) res += i
          i += 1
        }
        res.toArray
      case p =>
        val parameters = p.getParameters

        if (parameters.isEmpty) Array.emptyIntArray
        else if (parameters(parameters.length - 1).isVarArgs) Array(parameters.length - 1)
        else Array.emptyIntArray
    }
  }

  def unapply(signature: PhysicalMethodSignature): Option[(PsiMethod, ScSubstitutor)] = {
    Some(signature.method, signature.substitutor)
  }

  private def javaParamType(p: PsiParameter): ScType = {
    val treatJavaObjectAsAny = p.parentsInFile.instanceOf[PsiClass] match {
      case Some(cls) if cls.qualifiedName == "java.lang.Object" => true // See SCL-3036
      case _                                                    => false
    }

    val paramTypeNoVarargs = p.paramType(extractVarargComponent = true, treatJavaObjectAsAny = treatJavaObjectAsAny)

    if (p.isVarArgs) paramTypeNoVarargs.tryWrapIntoSeqType(p.elementScope)
    else             paramTypeNoVarargs
  }

  private def scalaParamType(p: ScParameter): ScType = {
    val typeElementType = p.`type`().getOrAny
    implicit val scope: ElementScope = p.elementScope

    if (p.isRepeatedParameter)        typeElementType.tryWrapIntoSeqType
    else if (p.isCallByNameParameter) FunctionType(typeElementType, Seq.empty)
    else                              typeElementType
  }
}

final class PhysicalMethodSignature(
  val method: PsiMethod,
  override val substitutor: ScSubstitutor
) extends TermSignature(
  method.name,
  PhysicalMethodSignature.typesEval(method),
  method.getTypeParameters.instantiate,
  substitutor,
  method,
  PhysicalMethodSignature.hasRepeatedParam(method)
) {

  override def isJava: Boolean = method.getLanguage == JavaLanguage.INSTANCE
}
