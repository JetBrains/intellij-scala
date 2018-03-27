package org.jetbrains.plugins.scala.lang.structureView.element

import javax.swing._

import com.intellij.navigation.ColoredItemPresentation
import com.intellij.openapi.editor.colors.{CodeInsightColors, TextAttributesKey}
import com.intellij.openapi.util.Iconable
import com.intellij.util.ui.UIUtil

// TODO make private (after decoupling Test)
trait AbstractItemPresentation extends ColoredItemPresentation { self: Element =>
  override def getPresentableText: String = ""

  override final def getLocationString: String =
    if (inherited) location.map(UIUtil.rightArrow + _).orNull else null

  protected def location: Option[String] = None

  override def getIcon(open: Boolean): Icon =
    element.getIcon(Iconable.ICON_FLAG_VISIBILITY)

  override def getTextAttributesKey: TextAttributesKey =
    if (inherited) CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES else null
}

private object AbstractItemPresentation {
  private val FullyQualifiedName = "(?:\\w+\\.)+(\\w+)".r

  def withSimpleNames(presentation: String): String =
    FullyQualifiedName.replaceAllIn(presentation, "$1")
}