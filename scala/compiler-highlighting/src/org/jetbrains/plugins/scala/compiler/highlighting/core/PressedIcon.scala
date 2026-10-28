package org.jetbrains.plugins.scala.compiler.highlighting.core

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.util.ui.{GraphicsUtil, JBUI}

import java.awt.geom.RoundRectangle2D
import java.awt.{Component, Graphics, Graphics2D}
import javax.swing.Icon

/**
 * `icon` on the background of a pressed action button, the way a toggle that is on is shown: it reads as a state
 * that is already set rather than as something to click.
 *
 * The background covers the icon's own bounds. Its color is read on every paint, so it follows the theme.
 *
 * @param gapAfter empty space after the background, in unscaled pixels: a label puts its text right next to its
 *                 icon, which a background would then touch.
 */
private[highlighting] final class PressedIcon(icon: Icon, gapAfter: Int = 0) extends Icon:
  override def getIconWidth: Int = icon.getIconWidth + JBUI.scale(gapAfter)

  override def getIconHeight: Int = icon.getIconHeight

  override def paintIcon(c: Component, g: Graphics, x: Int, y: Int): Unit =
    val g2 = g.create().asInstanceOf[Graphics2D]
    try
      GraphicsUtil.setupAAPainting(g2)
      g2.setColor(JBUI.CurrentTheme.ActionButton.pressedBackground())
      val arc = DarculaUIUtil.BUTTON_ARC.getFloat
      g2.fill(new RoundRectangle2D.Float(x.toFloat, y.toFloat, icon.getIconWidth.toFloat, icon.getIconHeight.toFloat, arc, arc))
    finally g2.dispose()
    icon.paintIcon(c, g, x, y)
