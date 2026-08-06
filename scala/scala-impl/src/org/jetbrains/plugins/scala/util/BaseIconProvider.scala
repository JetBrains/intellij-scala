package org.jetbrains.plugins.scala.util

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiModifierListOwner

import javax.swing.Icon

//noinspection ScalaWrongMethodsUsage
trait BaseIconProvider extends Iconable {

  protected def delegate: PsiModifierListOwner

  protected def baseIcon: Icon

  /**
   * Adds visibility icons if specified in `flags`<br>
   * Similar logic is done in other places, examples:
   *  - [[com.intellij.psi.impl.source.PsiMethodImpl#getElementIcon]]
   *  - [[com.intellij.psi.impl.source.PsiEnumConstantImpl#getElementIcon]]
   *  - etc...
   */
  override def getIcon(flags: Int): Icon =
    getIconWithExtraLayerFlags(flags, 0)

  final def getIconWithExtraLayerFlags(flags: Int, extraLayerFlags: Int): Icon = {
    val icon = baseIcon
    // TODO baseIcon shouldn't return null in ScVariable and ScFunction
    if (icon != null && delegate.isValid) {
      val layerFlags = ScalaElementPresentationUtil.getBaseLayerFlags(delegate, flags) | extraLayerFlags
      ScalaElementPresentationUtil.getIconWithLayeredFlags(delegate, flags, icon, layerFlags)
    }
    else null
  }
}