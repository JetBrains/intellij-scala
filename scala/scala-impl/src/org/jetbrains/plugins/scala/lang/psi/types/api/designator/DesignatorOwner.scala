package org.jetbrains.plugins.scala.lang.psi.types.api.designator

import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.extensions.PsiClassExt
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.types.api.{TypeConstructorOps, TypeParameter, ValueType}
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.ScTypePolymorphicType
import org.jetbrains.plugins.scala.lang.psi.types.recursiveUpdate.ScSubstitutor
import org.jetbrains.plugins.scala.lang.psi.types.{AliasType, Context, ScType}
import org.jetbrains.plugins.scala.project.ProjectContext

trait DesignatorOwner extends ValueType {
  val element: PsiNamedElement

  override implicit def projectContext: ProjectContext = element

  val isSingleton: Boolean = element match {
    case typedDefinition: ScTypedDefinition => typedDefinition.isStable
    case _                                  => false
  }

  def isStable: Boolean = isSingleton || element.isInstanceOf[ScObject]

  override def isFinalType: Boolean = element match {
    case clazz: PsiClass if clazz.isEffectivelyFinal => true
    case _                                           => false
  }

  private[types] def designatorSingletonType = element match {
    case _: ScObject                                          => None
    case parameter: ScParameter if parameter.isStable         => parameter.getRealParameterType.toOption
    case definition: ScTypedDefinition if definition.isStable => definition.`type`().toOption
    case _                                                    => None
  }

  protected def calculateAliasTypeAux(actualElement: PsiElement, subst: ScSubstitutor)(implicit context: Context): Option[AliasType] = {
    actualElement match {
      case ta: ScTypeAliasDefinition if ta.isOpaque && !context.isInScopeOf(ta) =>
        None
      case ta: ScTypeAlias if ta.typeParameters.isEmpty =>
        Some(AliasType(ta, ta.lowerBound.map(subst), ta.upperBound.map(subst)))
      case ta: ScTypeAlias => //higher kind case
        ta match {
          case ta: ScTypeAliasDefinition => //hack for simple cases, it doesn't cover more complicated examples
            ta.aliasedType match {
              case Right(tp) if tp == this => // recursive type alias
                return Some(AliasType(ta, Right(this), Right(this)))
              case _ =>
            }
          case _ =>
        }

        val tParams = ta.typeParameters

        def extractBound(tp: ScType): ScType =
          ScTypePolymorphicType(subst(tp), tParams.map(TypeParameter.apply))

        Option(
          AliasType(
            ta,
            ta.lowerBound.map(extractBound),
            ta.upperBound.map(extractBound)
          )
        )
      case _ => None
    }
  }
}

object DesignatorOwner {
  def unapply(`type`: ScType): Option[PsiNamedElement] = `type` match {
    case owner: DesignatorOwner => Some(owner.element)
    case _                      => None
  }
}
