package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.psi.{PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiElementExt}

import java.awt.event.MouseEvent

/**
 * @see [[ScalaInheritorsLineMarkerNavigator]]
 */
private class ScalaInheritorsMembersLineMarkerNavigator extends GutterIconNavigationHandler[PsiElement] {
  override def navigate(event: MouseEvent, element: PsiElement): Unit = {
    // Searching for a named element and not a ScMember to handle `val (v1, v2) = ...`
    val namedElement = element.withParentsInFile.find(_.is[PsiNamedElement]) match {
      case Some(value) => value
      case _ =>
        return
    }

    val dumbModeMessage = ScalaBundle.message("notification.navigation.to.overriding.members")
    new GotoImplementationHandler().navigateToImplementations(namedElement, event, dumbModeMessage)
  }
}
