package org.jetbrains.plugins.scala.lang
package psi
package light.scala

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScTypeAliasDeclaration, ScTypeAliasDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeParameter
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

import scala.annotation.tailrec

sealed abstract class ScLightTypeAlias[A <: ScTypeAlias](override protected val delegate: A,
                                                         val lowerBound: TypeResult,
                                                         val upperBound: TypeResult)
                                                        (implicit private val parameters: Seq[TypeParameter])
  extends ScLightModifierOwner(delegate) with ScTypeAlias {

  override def getNavigationElement: PsiElement = super[ScLightModifierOwner].getNavigationElement

  override def getOriginalElement: PsiElement = super[ScTypeAlias].getOriginalElement

  override def physical: ScTypeAlias = delegate

  override final def typeParametersClause: Option[ScTypeParamClause] =
    delegate.typeParametersClause.map {
      new ScLightTypeParamClause(parameters, _)
    }
}

object ScLightTypeAlias {

  @tailrec
  def apply(typeAlias: ScTypeAlias,
            lowerBound: ScType,
            upperBound: ScType,
            parameters: Seq[TypeParameter]): ScLightTypeAlias[_] = typeAlias match {
    case light: ScLightTypeAlias[_] => apply(light.delegate, lowerBound, upperBound, parameters)
    case _ => apply(typeAlias, Right(lowerBound), Right(upperBound))(parameters)
  }

  private[this] def apply(typeAlias: ScTypeAlias,
                          lowerBound: TypeResult,
                          upperBound: TypeResult)
                         (implicit parameters: Seq[TypeParameter]) = typeAlias match {
    case declaration: ScTypeAliasDeclaration =>
      new ScLightTypeAliasDeclaration(declaration, lowerBound, upperBound)
    case definition: ScTypeAliasDefinition =>
      new ScLightTypeAliasDefinition(definition, lowerBound, upperBound)
  }

  private final class ScLightTypeAliasDeclaration(override protected val delegate: ScTypeAliasDeclaration,
                                                  override val lowerBound: TypeResult,
                                                  override val upperBound: TypeResult)
                                                 (implicit parameters: Seq[TypeParameter])
    extends ScLightTypeAlias(delegate, lowerBound, upperBound) with ScTypeAliasDeclaration

  private final class ScLightTypeAliasDefinition(override protected val delegate: ScTypeAliasDefinition,
                                                 override val lowerBound: TypeResult,
                                                 override val upperBound: TypeResult)
                                                (implicit parameters: Seq[TypeParameter])
    extends ScLightTypeAlias(delegate, lowerBound, upperBound) with ScTypeAliasDefinition {

    override def aliasedType: TypeResult = lowerBound

    override def aliasedTypeElement: Option[ScTypeElement] = delegate.aliasedTypeElement
  }

}
