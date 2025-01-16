package org.jetbrains.plugins.scala.structureView.element

import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.structureView.ScalaStructureViewBundle

private class TypeAlias(alias: ScTypeAlias, inherited: Boolean)
  extends AbstractTreeElementDelegatingChildrenToPsi(alias, inherited)
  with InheritedLocationStringItemPresentation {

  override def location: Option[String] =
    Option(element.containingClass).map(_.name)

  override def getPresentableText: String =
    getTypeAliasPresentableText(element)

  @Nls
  private def getTypeAliasPresentableText(typeAlias: ScTypeAlias): String =
    typeAlias.nameId.explicitName match {
      case Some(name) => NlsString.force(name)
      case None => ScalaStructureViewBundle.message("type.unnamed")
    }
}
