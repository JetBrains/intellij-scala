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
  /**
   * Adds visibility icons if specified in `flags`<br>
   * Similar logic is done in other places, examples:
   *  - [[com.intellij.psi.impl.source.PsiMethodImpl#getElementIcon]]
   *  - [[com.intellij.psi.impl.source.PsiEnumConstantImpl#getElementIcon]]
   *  - etc...
   */
  override def getIcon(flags: Int): Icon = baseIcon match {
    case icon: Icon if delegate.isValid =>
      val layerFlags = getLayerFlags(flags)
      val layeredIcon = IconManager.getInstance.createLayeredIcon(delegate, icon, layerFlags)
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