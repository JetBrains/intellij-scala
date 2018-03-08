package org.jetbrains.plugins.scala.lang.psi.api.statements

import javax.swing.Icon

import com.intellij.openapi.util.Iconable
import com.intellij.psi.impl.{ElementBase, ElementPresentationUtil}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner

/**
  * @author Pavel Fatin
  */
trait ScVisibilityIconOwner { self: Iconable with ScModifierListOwner =>
  override final def getIcon(flags: Int): Icon =
    Option(getBaseIcon(flags))
      .map(baseIcon => ElementPresentationUtil.addVisibilityIcon(this, flags, ElementBase.createLayeredIcon(this, baseIcon, flags)))
      .orNull

  protected def getBaseIcon(flags: Int): Icon
}
