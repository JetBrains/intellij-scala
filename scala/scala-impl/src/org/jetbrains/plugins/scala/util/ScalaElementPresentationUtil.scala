package org.jetbrains.plugins.scala
package util

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Iconable
import com.intellij.psi.impl.{ElementBase, ElementPresentationUtil}
import com.intellij.psi.util.PsiMethodUtil
import com.intellij.psi.{PsiClass, PsiModifier, PsiModifierListOwner}
import com.intellij.ui.IconManager
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

import javax.swing.Icon

/**
 * See also [[com.intellij.psi.impl.ElementPresentationUtil]]
 *
 * This class mirrors some private constants form [[com.intellij.psi.impl.ElementPresentationUtil]].
 * We can get rid of it once the constants become public
 */
object ScalaElementPresentationUtil {
  private val FLAGS_ABSTRACT = 0x100
  private val FLAGS_FINAL = 0x400
  private val FLAGS_RUNNABLE = 0x4000

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

  /**
   * Similar thing is done in Java version in [[com.intellij.psi.impl.ElementPresentationUtil.getFlags]]
   */
  def getRunnableObjectFlags(element: PsiModifierListOwner): Int = element match {
    case obj: ScObject =>
      cachedInUserData("ScalaElementPresentationUtil.getRunnableFlags", obj, BlockModificationTracker(obj)) {
        getRunnableFlagsImpl(obj)
      }
    case _ =>
      0
  }

  private def getRunnableFlagsImpl(obj: ScObject): Int =
    if (isRunnable(obj)) FLAGS_RUNNABLE else 0

  /**
   * Similar logic for Java is located in [[com.intellij.psi.impl.ElementPresentationUtil.getClassKind]]
   * however we can't use the method as is.
   * It can't handle the case when trait/enum have companion object with main method
   */
  private def isRunnable(clazz: PsiClass): Boolean = {
    if (DumbService.getInstance(clazz.getProject).isDumb)
      return false

    PsiMethodUtil.findMainMethod(clazz) != null
  }
}
