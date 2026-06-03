package org.jetbrains.plugins.scala.util

import com.intellij.codeInsight.TestFrameworks
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Iconable
import com.intellij.psi.impl.{ElementBase, ElementPresentationUtil}
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.{CommonClassNames, JavaPsiFacade, PsiModifier, PsiModifierListOwner}
import com.intellij.ui.{IconManager, PlatformIcons}
import org.jetbrains.plugins.scala.caches.{BlockModificationTracker, cachedInUserData}
import org.jetbrains.plugins.scala.extensions.PsiModifierListOwnerExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition

import javax.swing.Icon

/**
 * See also [[com.intellij.psi.impl.ElementPresentationUtil]]
 *
 * This class mirrors some private constants form [[com.intellij.psi.impl.ElementPresentationUtil]].
 * We can get rid of it once the constants become public
 */
//noinspection ApiStatus,UnstableApiUsage
object ScalaElementPresentationUtil {
  private val FLAGS_ABSTRACT = 0x100
  private val FLAGS_FINAL = 0x400
  private val FLAGS_JUNIT_TEST = 0x2000
  private val FLAGS_RUNNABLE = 0x4000

  private lazy val ExceptionIcon = IconManager.getInstance.getPlatformIcon(PlatformIcons.ExceptionClass)
  private lazy val AbstractExceptionIcon = IconManager.getInstance.getPlatformIcon(PlatformIcons.AbstractException)

  def getIconWithLayeredFlags(element: PsiModifierListOwner, flags: Int, icon: Icon, layerFlags: Int): Icon = {
    val layeredIcon = IconManager.getInstance.createLayeredIcon(element, icon, layerFlags)
    ElementPresentationUtil.addVisibilityIcon(element, flags, layeredIcon)
  }

  def getBaseLayerFlags(element: PsiModifierListOwner, flags: Int): Int = {
    val maybeFinalFlag = if (Option(element.getModifierList).exists(_.hasExplicitModifier(PsiModifier.FINAL))) FLAGS_FINAL else 0
    val maybeAbstractFlag = if (element.hasAbstractModifier) FLAGS_ABSTRACT else 0
    val isLocked = (flags & Iconable.ICON_FLAG_READ_STATUS) != 0 && !element.isWritable
    val maybeLockedFlag = if (isLocked) ElementBase.FLAGS_LOCKED else 0

    maybeFinalFlag | maybeAbstractFlag | maybeLockedFlag
  }

  /**
   * ATTENTION: this method involves traversal of definition inheritors which might be a costly operation
   */
  def getTypeDefinitionIconWithKind(definition: ScTypeDefinition, baseFlags: Int = 0): Icon = {
    val classKind = getScalaClassKind(definition)
    classKind match {
      case ScalaClassKind.Simple =>
        definition.getIconWithExtraLayerFlags(Iconable.ICON_FLAG_VISIBILITY, baseFlags)
      case ScalaClassKind.Runnable =>
        definition.getIconWithExtraLayerFlags(Iconable.ICON_FLAG_VISIBILITY, baseFlags | FLAGS_RUNNABLE)
      case ScalaClassKind.Test =>
        definition.getIconWithExtraLayerFlags(Iconable.ICON_FLAG_VISIBILITY, baseFlags | FLAGS_JUNIT_TEST)
      case ScalaClassKind.Exception if definition.hasAbstractModifier =>
        AbstractExceptionIcon
      case ScalaClassKind.Exception =>
        ExceptionIcon
    }
  }

  /**
   * Similar thing is done in Java version in [[com.intellij.psi.impl.ElementPresentationUtil.getFlags]]
   */
  private def getScalaClassKind(element: PsiModifierListOwner): ScalaClassKind = element match {
    case obj: ScTypeDefinition =>
      cachedInUserData("ScalaElementPresentationUtil.getRunnableFlags", obj, BlockModificationTracker(obj)) {
        getScalaClassKindImpl(obj)
      }
    case _ =>
      ScalaClassKind.Simple
  }

  /**
   * Similar logic for Java is located in [[com.intellij.psi.impl.ElementPresentationUtil.getClassKind]]
   * however we can't use the method as is because it can not handle the case when trait/enum have companion object with main method
   */
  private def getScalaClassKindImpl(clazz: ScTypeDefinition): ScalaClassKind = {
    if (DumbService.getInstance(clazz.getProject).isDumb)
      return ScalaClassKind.Simple

    if (ScalaMainMethodUtil.hasMainMethodFromProvidersOnly(clazz))
      return ScalaClassKind.Runnable

    val javaLangThrowable = JavaPsiFacade.getInstance(clazz.getProject).findClass(CommonClassNames.JAVA_LANG_THROWABLE, clazz.getResolveScope)
    val isException = javaLangThrowable != null && InheritanceUtil.isInheritorOrSelf(clazz, javaLangThrowable, true)
    if (isException)
      return ScalaClassKind.Exception

    if (TestFrameworks.getInstance.isTestClass(clazz))
      return ScalaClassKind.Test

    ScalaClassKind.Simple
  }

  private sealed trait ScalaClassKind
  private object ScalaClassKind {
    object Simple extends ScalaClassKind
    object Runnable extends ScalaClassKind
    object Exception extends ScalaClassKind
    object Test extends ScalaClassKind
  }
}
