package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.ScalaBundle

import java.awt.event.MouseEvent

/**
 * Java version is in [[com.intellij.codeInsight.daemon.impl.InheritorsLineMarkerNavigator]]
 * that delegates to [[com.intellij.codeInsight.navigation.GotoImplementationHandler]]
 *
 * Note, that the Java version handles not only classes but also methods.
 *
 * @see [[ScalaInheritorsMembersLineMarkerNavigator]]
 */
private class ScalaInheritorsLineMarkerNavigator extends GutterIconNavigationHandler[PsiElement] {
  override def navigate(event: MouseEvent, element: PsiElement): Unit = {
    val clazz = element.getParent match {
      case aClass: PsiClass => aClass
      case _ =>
        return
    }

    @NlsContexts.PopupContent
    val dumbModeMessage = ScalaBundle.message("notification.navigation.to.overriding.classes")
    new GotoImplementationHandler().navigateToImplementations(clazz, event, dumbModeMessage)
  }
}
