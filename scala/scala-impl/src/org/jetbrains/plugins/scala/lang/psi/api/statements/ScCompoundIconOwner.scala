package org.jetbrains.plugins.scala.lang.psi.api.statements

import javax.swing.Icon

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiModifier
import com.intellij.psi.impl.{ElementBase, ElementPresentationUtil}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

/**
  * @author Pavel Fatin
  */
trait ScCompoundIconOwner { self: Iconable with ScModifierListOwner =>
  override final def getIcon(flags: Int): Icon = {
    getBaseIcon(flags) match {
      case baseIcon: Icon =>
        if (isValid) {
          val locked = (flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !isWritable
          val layeredIcon = ElementBase.createLayeredIcon(this, baseIcon, ScCompoundIconOwner.flagsFor(this, locked))
          ElementPresentationUtil.addVisibilityIcon(this, flags, layeredIcon)
        } else {
          baseIcon
        }
      case _ => null
    }
  }

  protected def getBaseIcon(flags: Int): Icon
}

private object ScCompoundIconOwner {
  // See ElementPresentationUtil.getFlags
  private def flagsFor(element: ScModifierListOwner, locked: Boolean): Int =
    (if (element.hasModifierPropertyScala(PsiModifier.FINAL)) 0x400 else 0) |
      (if (element.hasModifierProperty(PsiModifier.ABSTRACT)) 0x100 else 0) |
      (if (locked) ElementBase.FLAGS_LOCKED else 0)
}