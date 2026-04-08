package org.jetbrains.plugins.scala.lang.psi.api.base
package types

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params._

trait ScTypeArgs extends ScArguments {
  import ScTypeArgs.TypeArgument

  def typeArgs: Seq[ScTypeElement]

  def typeArgsWithNamed: Seq[TypeArgument] =
    typeArgs.map(typeArg => TypeArgument(namedTypeArgNameOf(typeArg), typeArg))

  def namedTypeArgs: Seq[TypeArgument] =
    typeArgsWithNamed.filter(_.nameElement.isDefined)

  override def getArgsCount: Int = typeArgs.length

  private def namedTypeArgNameOf(typeArg: ScTypeElement): Option[PsiElement] =
    typeArg.prevSiblingNotWhitespaceComment
      .filter(_.textMatches("="))
      .flatMap(_.prevSiblingNotWhitespaceComment)
}

object ScTypeArgs {
  final case class TypeArgument(nameElement: Option[PsiElement], typeElement: ScTypeElement)
}
