package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScExtension

import javax.swing.Icon

// TODO: improve in SCL-19224
final class Extension(extension: ScExtension) extends AbstractTreeElement(extension) {

  override def getPresentableText: String = {
    val parameterTypeText = extension.targetTypeElement.fold("")(_.getText)
    s"extension ($parameterTypeText)"
  }

  override protected def location: Option[String] = None // TODO: extension can be inherited

  override def getIcon(open: Boolean): Icon = Icons.EXTENSION // TODO: which icon should we use?

  override def getTextAttributesKey: TextAttributesKey = super.getTextAttributesKey

  override protected def children: Seq[PsiElement] = extension.extensionMethods

  override def isAlwaysLeaf: Boolean = false

  override def isAlwaysShowsPlus: Boolean = super.isAlwaysShowsPlus
}
