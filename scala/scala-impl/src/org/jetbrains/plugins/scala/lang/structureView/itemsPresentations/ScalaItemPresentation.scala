package org.jetbrains.plugins.scala.lang.structureView.itemsPresentations


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
abstract class ScalaItemPresentation(protected val myElement: PsiElement, inherited: Boolean = false) extends ColoredItemPresentation {
  override final def getLocationString: String =
    if (inherited) location.map(UIUtil.rightArrow + _).orNull else null

  protected def location: Option[String] = None

  def getIcon(open: Boolean): Icon = myElement.getIcon(Iconable.ICON_FLAG_VISIBILITY)

  def getTextAttributesKey: TextAttributesKey = null
}

private[itemsPresentations] object ScalaItemPresentation {
  private val FullyQualifiedName = "(?:\\w+\\.)+(\\w+)".r

  private[itemsPresentations] def withSimpleNames(presentation: String): String = {
    FullyQualifiedName.replaceAllIn(presentation, "$1")
  }
}