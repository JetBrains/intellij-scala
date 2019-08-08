package org.jetbrains.plugins.scala
package lang
package psi
package api
package statements

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiModifier
import com.intellij.psi.impl.{ElementBase, ElementPresentationUtil}
import com.intellij.ui.IconManager
import javax.swing.Icon
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

/**
 * @author Pavel Fatin
 */
// TODO Place directly in ScModifierListOwner?
trait ScDecoratedIconOwner {

  self: ScModifierListOwner =>

  // TODO baseIcon shouldn't return null in ScVariable and ScFunction
  override final def getIcon(flags: Int): Icon = decorate(baseIcon, flags)

  def decorate(baseIcon: Icon, flags: Int): Icon =
    if (baseIcon != null && isValid) {
      this.visibilityIcon(
        baseIcon,
        flags,
        this.layerFlags((flags & Iconable.ICON_FLAG_READ_STATUS) == 0 || isWritable)
      )
    } else {
      baseIcon
    }

  protected def baseIcon: Icon
}

private object ScDecoratedIconOwner {

  implicit class Ext(private val element: ScModifierListOwner) extends AnyVal {

    import ElementPresentationUtil._

    private[ScDecoratedIconOwner] def visibilityIcon(icon: Icon, flags: Int, layerFlags: Int): Icon =
      addVisibilityIcon(
        element,
        flags,
        IconManager.getInstance.createLayeredIcon(element, icon, layerFlags)
      )

    /**
     * @see [[getFlags]], [[FLAGS_ABSTRACT]], [[FLAGS_FINAL]]
     */
    private[ScDecoratedIconOwner] def layerFlags(isNotLocked: Boolean): Int =
      (if (element.hasModifierPropertyScala(PsiModifier.FINAL)) 0x400 else 0) |
        (if (element.hasModifierProperty(PsiModifier.ABSTRACT)) 0x100 else 0) |
        (if (isNotLocked) 0 else ElementBase.FLAGS_LOCKED)
  }
}