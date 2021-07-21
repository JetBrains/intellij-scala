package org.jetbrains.plugins.scala
package util

import com.intellij.openapi.util.Iconable
import com.intellij.psi.impl.{ElementBase, ElementPresentationUtil}
import com.intellij.psi.{PsiModifier, PsiModifierListOwner}
import com.intellij.ui.IconManager
import javax.swing.Icon

//noinspection ScalaWrongMethodsUsage
trait BaseIconProvider extends Iconable {

  protected def delegate: PsiModifierListOwner

  protected def baseIcon: Icon

  // TODO baseIcon shouldn't return null in ScVariable and ScFunction
  override def getIcon(flags: Int): Icon = baseIcon match {
    case _: Icon if delegate.isValid =>
      val layerFlags = getLayerFlags(flags)
      val layeredIcon = IconManager.getInstance.createLayeredIcon(delegate, baseIcon, layerFlags)
      ElementPresentationUtil.addVisibilityIcon(delegate, flags, layeredIcon)
    case _ => null
  }

  /**
   * @see [[ElementPresentationUtil.getFlags]]
   * @see [[ElementPresentationUtil.FLAGS_ABSTRACT]]
   * @see [[ElementPresentationUtil.FLAGS_FINAL]]
   */
  private[this] def getLayerFlags(flags: Int): Int =
    (if (Option(delegate.getModifierList).exists(_.hasExplicitModifier(PsiModifier.FINAL))) 0x400 else 0) |
      (if (delegate.hasModifierProperty(PsiModifier.ABSTRACT)) 0x100 else 0) |
      (if ((flags & Iconable.ICON_FLAG_READ_STATUS) == 0 || delegate.isWritable) 0 else ElementBase.FLAGS_LOCKED)
}