package org.jetbrains.plugins.scala
package util

import com.intellij.openapi.util.Iconable
import com.intellij.psi.impl.{ElementBase, ElementPresentationUtil}
import com.intellij.psi.{PsiClass, PsiModifier, PsiModifierListOwner}
import com.intellij.ui.IconManager

import javax.swing.Icon

/**
 * See also [[com.intellij.psi.impl.ElementPresentationUtil]]
 *
 * This class mirrors some private constants form [[com.intellij.psi.impl.ElementPresentationUtil]].
 * We can get rid of it once the constants become public
 */
object ElementPresentationUtilScala {
  private val FLAGS_ABSTRACT = 0x100
  private val FLAGS_FINAL = 0x400

  def getIconWithLayeredFlags(element: PsiModifierListOwner, flags: Int, icon: Icon, layerFlags: Int): Icon = {
    val layeredIcon = IconManager.getInstance.createLayeredIcon(element, icon, layerFlags)
    ElementPresentationUtil.addVisibilityIcon(element, flags, layeredIcon)
  }

  def getBaseLayerFlags(element: PsiModifierListOwner, flags: Int): Int = {
    val maybeFinalFlag = if (Option(element.getModifierList).exists(_.hasExplicitModifier(PsiModifier.FINAL))) FLAGS_FINAL else 0
    val maybeAbstractFlag = if (element.hasModifierProperty(PsiModifier.ABSTRACT)) FLAGS_ABSTRACT else 0
    val isLocked = (flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !element.isWritable
    val maybeLockedFlag = if (isLocked) ElementBase.FLAGS_LOCKED else 0

    maybeFinalFlag | maybeAbstractFlag | maybeLockedFlag
  }
}
