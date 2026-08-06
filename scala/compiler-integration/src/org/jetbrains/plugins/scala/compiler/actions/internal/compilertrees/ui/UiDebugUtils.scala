package org.jetbrains.plugins.scala.compiler.actions.internal.compilertrees.ui

import com.intellij.ui.JBColor

import java.awt.Color
import javax.swing.{BorderFactory, JComponent}

private object UiDebugUtils {
  private val debugColors: Array[Color] = Array(
    JBColor.RED,
    JBColor.BLUE,
    JBColor.GREEN,
    JBColor.YELLOW,
    JBColor.ORANGE,
    JBColor.MAGENTA,
    JBColor.CYAN,
    JBColor.PINK,
  )
  private var nextDebugColorIndex: Int = 0

  def addDebugBorderIfEnabled[T <: JComponent](component: T): T = {
    //NOTE: this return is intentionally used in PROD.
    //If you want to enable debug borders locally, comment this line locally
    return component

    val color = debugColors(nextDebugColorIndex)
    val borderSize = 3
    nextDebugColorIndex = (nextDebugColorIndex + borderSize) % debugColors.length

    val debugBorder = BorderFactory.createLineBorder(color, borderSize)

    val existingBorder = component.getBorder
    val combinedBorder =
      if (existingBorder == null) debugBorder
      else BorderFactory.createCompoundBorder(debugBorder, existingBorder)

    component.setBorder(combinedBorder)
    component
  }
}
