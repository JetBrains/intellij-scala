package org.jetbrains.plugins.scala.lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScTypePattern, ScTypedPatternLike}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScInfixTypeElement, ScParameterizedTypeElement, ScParenthesisedTypeElement, ScTypeArgs, ScTypeVariableTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard

class ScTypeVariableTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeVariableTypeElement {
  override protected def innerType: TypeResult =
    Right(ScExistentialArgument(name, List.empty, Nothing, Any))

  // https://www.scala-lang.org/files/archive/spec/2.13/08-pattern-matching.html#type-parameter-inference-in-patterns
  @CachedWithRecursionGuard(this, Failure(ScalaBundle.message("recursive.type.of.type.element")), BlockModificationTracker(this))
  override def inferredType: TypeResult = inferredType0

  private def inferredType0: TypeResult = getParent match {
    case (_: ScTypeArgs) &&
      Parent((_: ScParameterizedTypeElement) &&
        Parent((_: ScTypePattern) &&
          Parent(typedPattern: ScTypedPatternLike))) => inferredType1(typedPattern)

    case (_: ScInfixTypeElement) &&
      Parent((_: ScParenthesisedTypeElement) &&
        Parent((_: ScTypePattern) &&
          Parent(typedPattern: ScTypedPatternLike))) => inferredType1(typedPattern)

    case _ => Right(existentialArgumentWith(None))
  }

  private def inferredType1(typedPattern: ScTypedPatternLike) = {
    def patternConformsTo(t: ScParameterizedType) = getParent.getParent match {
      case e: ScParameterizedTypeElement => e.typeElement.`type`().exists(t.designator.conforms)
      case _ => typedPattern.`type`() match {
        case Right(ct: ScCompoundType) => ct.components.headOption.exists(t.conforms) // See ScTypedPatternImpl.type
        case _ => false
      }
    }

    typedPattern.expectedType match {
      case Some(existentialType: ScExistentialType) =>
        existentialType.quantified match {
          case expected: ScParameterizedType if patternConformsTo(expected) => Right(existentialArgumentWith(boundsGiven(expected)))
          case _ => Failure("Fruitless type test")
        }
      case Some(expected: ScParameterizedType) if patternConformsTo(expected) => Right(existentialArgumentWith(boundsGiven(expected)))
      case _ => Failure("Fruitless type test")
    }
  }

  private def existentialArgumentWith(bounds: Option[(ScType, ScType)]): ScExistentialArgument = bounds match {
    case Some((lower, upper)) => ScExistentialArgument(name, List.empty, lower, upper)
    case None => ScExistentialArgument(name, List.empty, Nothing, Any)
  }

  private def boundsGiven(expected: ScParameterizedType): Option[(ScType, ScType)] = {
    val typeParameters = expected.designator match {
      case dt: ScDesignatorType => dt.element match {
        case tpo: ScTypeParametersOwner => tpo.typeParameters
        case _ => Seq.empty
      }
      case _ => Seq.empty
    }
    val typeVariableArgumentPosition = getParent match {
      case e: ScTypeArgs => e.typeArgs.indexOf(this)
      case e: ScInfixTypeElement => if (e.left == this) 0 else 1
    }
    (typeParameters.lift(typeVariableArgumentPosition), expected.typeArguments.lift(typeVariableArgumentPosition)) match {
      case (Some(parameter), Some(argument)) =>
        val variance = parameter.variance
        val (pLower, pUpper) = (parameter.lowerBound.getOrNothing, parameter.upperBound.getOrAny)
        val (aLower, aUpper) = argument match {
          case ea: ScExistentialArgument => (ea.lower, ea.upper)
          case t => (t, t)
        }
        val (lower, upper): (ScType, ScType) =
          if (variance.isInvariant) (aLower.lub(pLower), aUpper.glb(pUpper))
          else if (variance.isCovariant) (pLower.glb(pUpper), aUpper.glb(pUpper))
          else if (variance.isContravariant) (aLower.lub(pLower), pLower.lub(pUpper))
          else throw new RuntimeException()
        Some((lower, upper))
      case _ => None
    }
  }

  override def nameId: PsiElement = findChildByType[PsiElement](ScalaTokenTypes.tIDENTIFIER)

  override def toString: String = s"$typeName: ${ifReadAllowed(name)("")}"
}
