package org.jetbrains.plugins.scala.lang.resolve.processor

import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.scope.NameHint
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScTypeParametersOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.{ScProjectionType, ScThisType}
import org.jetbrains.plugins.scala.lang.psi.types.api.{Covariant, TypeParameter, TypeParameterType, Unit, Variance}
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, StdKinds}

class CompoundTypeCheckSignatureProcessor(s: TermSignature,
                                          retType: ScType,
                                          constraints: ConstraintSystem)
  extends BaseProcessor(StdKinds.methodRef + ResolveTargets.CLASS)(s.projectContext) {

  private def nameHint: NameHint = _ => s.name

  override def getHint[T](hintKey: Key[T]): T =
    if (hintKey == NameHint.KEY) nameHint.asInstanceOf[T]
    else super.getHint(hintKey)

  private var trueResult = false

  def getResult: Boolean = trueResult

  private var innerConstraints = constraints

  def getConstraints: ConstraintSystem = innerConstraints

  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {
    val subst = state.substitutor
    if (ScalaNamesUtil.clean(namedElement.name) != s.name) return true

    var undef = constraints

    def checkTypeParameters(tp1: PsiTypeParameter, tp2: TypeParameter, v: Variance = Covariant): Boolean = {
      tp1 match {
        case tp1: ScTypeParam =>
          if (tp1.typeParameters.length != tp2.typeParameters.length) return false
          val iter = tp1.typeParameters.zip(tp2.typeParameters).iterator
          while (iter.hasNext) {
            val (tp1, tp2) = iter.next()
            if (!checkTypeParameters(tp1, tp2, -v)) return false
          }
          //lower type
          val lower1 = tp1.lowerBound.getOrNothing
          val lower2 = tp2.lowerType
          val lowerConformance =
            if (v == Covariant) lower1.conforms(lower2, undef)
            else lower2.conforms(lower1, undef)

          if (lowerConformance.isLeft) return false
          undef = lowerConformance.constraints

          val upper1 = tp1.upperBound.getOrAny
          val upper2 = tp2.upperType
          val upperConformance =
            if (v == Covariant) upper2.conforms(upper1, undef)
            else upper1.conforms(upper2, undef)

          if (upperConformance.isLeft) return false
          undef = upperConformance.constraints

          //todo: view?
          true
        case _ =>
          if (tp2.typeParameters.nonEmpty) return false
          //todo: check bounds?
          true
      }
    }

    //let's check type parameters
    namedElement match {
      case o: ScTypeParametersOwner =>
        if (o.typeParameters.length != s.typeParamsLength) return true
        val iter = o.typeParameters.zip(s.typeParams).iterator
        while (iter.hasNext) {
          val (tp1, tp2) = iter.next()
          if (!checkTypeParameters(tp1, tp2)) return true
        }
      case p: PsiTypeParameterListOwner =>
        if (p.getTypeParameters.length != s.typeParams.length) return true
        val iter = p.getTypeParameters.toSeq.zip(s.typeParams).iterator
        while (iter.hasNext) {
          val (tp1, tp2) = iter.next()
          if (!checkTypeParameters(tp1, tp2)) return true
        }
      case _ => if (s.typeParamsLength > 0) return true
    }

    def checkSignature(sign1: TermSignature, returnType: ScType): Boolean = {

      val sign2 = s

      if (!sign1.parameterlessCompatible(sign2)) return true

      var t = sign1.paramTypesEquivExtended(sign2, undef, falseUndef = false)
      if (t.isLeft) return true
      undef = t.constraints
      innerConstraints = undef

      val typeParams = sign1.typeParams
      val otherTypeParams = s.typeParams
      val unified1 = subst.withBindings(typeParams, typeParams)
      val unified2 = ScSubstitutor.bind(otherTypeParams, typeParams.map(TypeParameterType(_)))

      val bType = unified1(returnType)
      val gType = unified2(retType)
      t = bType.conforms(gType, undef)
      if (t.isRight) {
        trueResult = true
        undef = t.constraints
        innerConstraints = undef
        return false
      }
      true
    }

    namedElement match {
      case _: ScBindingPattern | _: ScFieldId | _: ScParameter =>
        val rt = subst(namedElement match {
          case b: ScBindingPattern => b.`type`().getOrNothing
          case f: ScFieldId => f.`type`().getOrNothing
          case param: ScParameter => param.`type`().getOrNothing
        })
        val dcl: ScTypedDefinition = namedElement.asInstanceOf[ScTypedDefinition]
        val isVar = dcl.isVar
        if (!checkSignature(TermSignature(dcl, subst), rt)) return false
        if (isVar && !checkSignature(TermSignature.scalaSetter(dcl, subst), Unit)) return false
      case method: PsiMethod =>
        val sign1 = new PhysicalMethodSignature(method, subst)
        if (!checkSignature(sign1, method match {
          case fun: ScFunction => fun.returnType.getOrNothing
          case method: PsiMethod => method.getReturnType.toScType()
        })) return false
      case _ =>
    }
    true
  }
}

class CompoundTypeCheckTypeAliasProcessor(
  sign:        TypeAliasSignature,
  constraints: ConstraintSystem,
  substitutor: ScSubstitutor
) extends BaseProcessor(StdKinds.methodRef + ResolveTargets.CLASS)(sign.typeAlias.projectContext) {
  private val name = sign.name

  private var trueResult = false

  def getResult: Boolean = trueResult

  private var innerConstraints = constraints

  def getConstraints: ConstraintSystem = innerConstraints


  override protected def execute(namedElement: PsiNamedElement)
                                (implicit state: ResolveState): Boolean = {
    val subst = state.substitutor.followed(substitutor)
    if (namedElement.name != name) return true

    var undef = constraints

    def checkTypeParameters(tp1: PsiTypeParameter, tp2: TypeParameter, v: Variance = Covariant): Boolean = {
      tp1 match {
        case tp1: ScTypeParam =>
          if (tp1.typeParameters.length != tp2.typeParameters.length) return false
          val iter = tp1.typeParameters.zip(tp2.typeParameters).iterator
          while (iter.hasNext) {
            val (tp1, tp2) = iter.next()
            if (!checkTypeParameters(tp1, tp2, -v)) return false
          }
          //lower type
          val lower1 = tp1.lowerBound.getOrNothing
          val lower2 = subst(tp2.lowerType)
          val lowerConformance =
            if (v == Covariant) lower1.conforms(lower2, undef)
            else                lower2.conforms(lower1, undef)

          if (lowerConformance.isLeft) return false
          undef = lowerConformance.constraints

          val upper1 = tp1.upperBound.getOrAny
          val upper2 = subst(tp2.upperType)
          val upperConformance =
            if (v == Covariant) upper2.conforms(upper1, undef)
            else                upper1.conforms(upper2, undef)

          if (upperConformance.isLeft) return false
          undef = upperConformance.constraints

          //todo: view?
          true
        case _ =>
          if (tp2.typeParameters.nonEmpty) return false
          //todo: check bounds?
          true
      }
    }

    //let's check type parameters
    namedElement match {
      case o: ScTypeParametersOwner =>
        if (o.typeParameters.length != sign.typeParams.length) return true
        val iter = o.typeParameters.zip(sign.typeParams).iterator
        while (iter.hasNext) {
          val (tp1, tp2) = iter.next()
          if (!checkTypeParameters(tp1, tp2)) return true
        }
      case p: PsiTypeParameterListOwner =>
        if (p.getTypeParameters.length != sign.typeParams.length) return true
        val iter = p.getTypeParameters.toSeq.zip(sign.typeParams).iterator
        while (iter.hasNext) {
          val (tp1, tp2) = iter.next()
          if (!checkTypeParameters(tp1, tp2)) return true
        }
      case _ => if (sign.typeParams.nonEmpty) return true
    }

    def checkDeclarationForTypeAlias(tp: ScTypeAlias): Boolean = {
      sign.typeAlias match {
        case _: ScTypeAliasDeclaration =>
          var conformance = subst(sign.lowerBound).conforms(subst(tp.lowerBound.getOrNothing), undef)
          if (conformance.isRight) {
            conformance = subst(tp.upperBound.getOrAny).conforms(subst(sign.upperBound), conformance.constraints)
            if (conformance.isRight) {
              trueResult = true
              innerConstraints = conformance.constraints
              return true
            }
          }
        case tdef: ScTypeAliasDefinition =>
          val enclosingClass = tp.parentOfType(classOf[ScTemplateDefinition])
          val thisType       = enclosingClass.map(ScThisType).getOrElse(tdef.projectContext.stdTypes.Any)
          val asSeenFrom     = subst(ScProjectionType(thisType, tp))
          val aliased        = subst(sign.upperBound)
          val conforms       = aliased.equiv(asSeenFrom, constraints)

          if (conforms.isRight) {
            trueResult       = true
            innerConstraints = conforms.constraints
            return true
          }
        case _ =>
      }
      false
    }

    namedElement match {
      case tp: ScTypeAliasDefinition =>
        sign.typeAlias match {
          case adef: ScTypeAliasDefinition =>
            val tpts             = tp.typeParameters.map(TypeParameterType(_))
            val renameTypeParams = ScSubstitutor.bind(adef.typeParameters, tpts)
            val lhs              = subst(tp.aliasedType.getOrNothing)
            val rhs              = renameTypeParams(subst(sign.lowerBound))
            val conforms         = lhs.equiv(rhs, undef, falseUndef = false)

            if (conforms.isRight) {
              trueResult = true
              innerConstraints = conforms.constraints
              return false
            }
          case _: ScTypeAliasDeclaration => if (checkDeclarationForTypeAlias(tp)) return false
          case _ => throw new IllegalArgumentException("Type alias must be either a declaration of definition.")
        }
      case tp: ScTypeAliasDeclaration => if (checkDeclarationForTypeAlias(tp)) return false
      case _ => ()
    }
    true
  }
}