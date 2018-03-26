package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import javax.swing._

import com.intellij.navigation.ColoredItemPresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import com.intellij.util.ui.UIUtil

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/
abstract class ScalaItemPresentation(element: PsiElement, inherited: Boolean = false) extends ColoredItemPresentation {
  override final def getLocationString: String =
    if (inherited) location.map(UIUtil.rightArrow + _).orNull else null

  protected def location: Option[String] = None

  override def getIcon(open: Boolean): Icon = element.getIcon(Iconable.ICON_FLAG_VISIBILITY)

  override def getTextAttributesKey: TextAttributesKey = null
}

private[elements] object ScalaItemPresentation {
  private val FullyQualifiedName = "(?:\\w+\\.)+(\\w+)".r

  private[elements] def withSimpleNames(presentation: String): String = {
    FullyQualifiedName.replaceAllIn(presentation, "$1")
  }
}