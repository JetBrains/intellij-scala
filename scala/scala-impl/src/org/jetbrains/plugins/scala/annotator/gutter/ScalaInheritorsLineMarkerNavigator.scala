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
 */
private class ScalaInheritorsLineMarkerNavigator extends GutterIconNavigationHandler[PsiElement] {
  override def navigate(event: MouseEvent, element: PsiElement): Unit = {
    val parent = element.getParent

    @NlsContexts.PopupContent
    val dumbModeMessage= parent match {
      case _: PsiClass =>
        ScalaBundle.message("notification.navigation.to.overriding.classes")
      case _=>
        ScalaBundle.message("notification.navigation.to.overriding.members")
    }

    new GotoImplementationHandler().navigateToImplementations(parent, event, dumbModeMessage)
  }
}
