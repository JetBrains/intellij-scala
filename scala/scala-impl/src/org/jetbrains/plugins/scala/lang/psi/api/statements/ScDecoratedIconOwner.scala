package org.jetbrains.plugins.scala.lang.psi.api.statements

import javax.swing.Icon

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiModifier
import com.intellij.psi.impl.{ElementBase, ElementPresentationUtil}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

/**
  * @author Pavel Fatin
  */
// TODO Place directly in ScModifierListOwner?
trait ScDecoratedIconOwner { self: Iconable with ScModifierListOwner =>
  override final def getIcon(flags: Int): Icon = decorate(getBaseIcon(flags), flags)

  def decorate(baseIcon: Icon, flags: Int): Icon = if (baseIcon == null || !isValid) baseIcon else {
    val isLocked = (flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !isWritable
    val layeredIcon = ElementBase.createLayeredIcon(this, baseIcon, ScDecoratedIconOwner.flagsFor(this, isLocked))
    ElementPresentationUtil.addVisibilityIcon(this, flags, layeredIcon)
  }

  protected def getBaseIcon(flags: Int): Icon
}

private object ScDecoratedIconOwner {
  // See ElementPresentationUtil.getFlags
  private def flagsFor(element: ScModifierListOwner, locked: Boolean): Int =
    (if (element.hasModifierPropertyScala(PsiModifier.FINAL)) 0x400 else 0) |
      (if (element.hasModifierProperty(PsiModifier.ABSTRACT)) 0x100 else 0) |
      (if (locked) ElementBase.FLAGS_LOCKED else 0)
}