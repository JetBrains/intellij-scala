package org.jetbrains.plugins.scala.structureView.element

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension

// TODO: improve in SCL-19224
// - inherited extensions
final class Extension(extension: ScExtension)
  extends AbstractTreeElementDelegatingChildrenToPsi(extension)
    with InheritedLocationStringItemPresentation {

  override def getPresentableText: String = {
    val typeParameters = extension.typeParametersClause.fold("")(_.getTextByStub)
    val parametersText = FromStubsParameterRenderer.renderClauses(extension.allClauses)
    s"extension $typeParameters$parametersText"
  }

  override protected def location: Option[String] = None // TODO: extension can be inherited

  override def getTextAttributesKey: TextAttributesKey = super.getTextAttributesKey

  override protected def children: Seq[PsiElement] = extension.extensionMethods

  override def isAlwaysLeaf: Boolean = false

  override def isAlwaysShowsPlus: Boolean = super.isAlwaysShowsPlus
}
