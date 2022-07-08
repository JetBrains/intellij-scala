package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle}

private class TypeAlias(alias: ScTypeAlias, inherited: Boolean) extends AbstractTreeElementDelegatingChildrenToPsi(alias, inherited)  {
  override def location: Option[String] =
    Option(element.containingClass).map(_.name)

  override def getPresentableText: String =
    getTypeAliasPresentableText(element)

  @Nls
  private def getTypeAliasPresentableText(typeAlias: ScTypeAlias): String =
    if (typeAlias.nameId != null) NlsString.force(typeAlias.nameId.getText) else ScalaBundle.message("type.unnamed")

  override def getTextAttributesKey: TextAttributesKey =
    if (inherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
}
