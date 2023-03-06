package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.navigation.ItemPresentation
import com.intellij.util.ui.UIUtil

trait InheritedLocationStringItemPresentation extends ItemPresentation  { self: Element =>

  override final def getLocationString: String =
    if (inherited)
      location.map(UIUtil.rightArrow + _).orNull
    else
      null

  protected def location: Option[String] = None
}
