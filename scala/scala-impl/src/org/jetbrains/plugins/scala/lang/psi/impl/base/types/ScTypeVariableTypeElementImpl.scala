package org.jetbrains.plugins.scala
package lang.psi.impl.base.types

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt, PsiElementExt, ifReadAllowed}
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScPattern, ScTypePattern, ScTypedPatternLike}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScTypeArgs, ScTypeElement, ScTypeVariableTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.api.{Any, Nothing}
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.lang.psi.types.{ScExistentialArgument, ScExistentialType, ScParameterizedType, ScType}
import org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard

class ScTypeVariableTypeElementImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeVariableTypeElement {
  override protected def innerType: TypeResult =
    Right(ScExistentialArgument(name, List.empty, Nothing, Any))

  // https://www.scala-lang.org/files/archive/spec/2.13/08-pattern-matching.html#type-parameter-inference-in-patterns
  @CachedWithRecursionGuard(this, Failure(ScalaBundle.message("recursive.type.of.type.element")), BlockModificationTracker(this))
  override def inferredType: TypeResult = inferredType0

  private def inferredType0: TypeResult = {
    // There's no case in a pattern definition and no typed pattern in a match type case.
    this.parents.takeWhile(_.is[ScTypeArgs, ScTypeElement, ScPattern, ScTypePattern]).findByType[ScTypedPatternLike] match {
      case Some(typedPattern) =>
        def patternConformsTo(t: ScParameterizedType) =
          getParent.getParent.asInstanceOf[ScParameterizedTypeElementImpl].typeElement.`type`().exists(t.designator.conforms)
        typedPattern.expectedType match {
          case Some(existentialType: ScExistentialType) =>
            existentialType.quantified match {
              case expected: ScParameterizedType if patternConformsTo(expected) => Right(existentialArgumentWith(boundsGiven(expected)))
              case _ => Failure("Fruitless type test")
            }
          case Some(expected: ScParameterizedType) if patternConformsTo(expected) => Right(existentialArgumentWith(boundsGiven(expected)))
          case _ => Failure("Fruitless type test")
        }
      case None => Right(existentialArgumentWith(None))
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
    val typeVariableArgumentPosition = getParent.asInstanceOf[ScTypeArgs].typeArgs.indexOf(this)
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
