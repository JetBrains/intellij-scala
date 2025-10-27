package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.navigation.GotoImplementationHandler
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.{PsiClass, PsiElement, PsiNamedElement}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.gutter.GutterUtil.namedParent
import org.jetbrains.plugins.scala.annotator.gutter.ScalaMarkerType.{findOverrides, newCellRenderer}
import org.jetbrains.plugins.scala.extensions.PsiNamedElementExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember

import java.awt.event.MouseEvent
import java.util
import javax.swing.JComponent

/**
 * @see [[ScalaInheritorsLineMarkerNavigator]]
 */
private class ScalaInheritorsMembersLineMarkerNavigator extends GutterIconNavigationHandler[PsiElement] {
  override def navigate(event: MouseEvent, element: PsiElement): Unit = {
    namedParent(element).collect {
      case member: ScMember =>
        val project = member.getProject
        if (DumbService.isDumb(project)) {
          DumbService.getInstance(project)
            .showDumbModeNotification(ScalaBundle.message("notification.navigation.to.overriding.members"))
        } else {
          var overrides: Seq[PsiNamedElement] = Seq.empty
          val res = ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () => {
              overrides = findOverrides(member, deep = true)
            },
            ScalaBundle.message("searching.for.overriding.members"),
            true,
            project,
            event.getComponent.asInstanceOf[JComponent]
          )
          if (res) {
            if (overrides.nonEmpty) {
              val name = overrides.headOption.fold("")(_.name)

              val (title, findUsagesTitle) =
                if (GutterUtil.isAbstract(member)) {
                  ScalaBundle.message("navigation.title.implementing.member", name, overrides.length.toString) ->
                    ScalaBundle.message("navigation.findUsages.title.implementing.member", name)
                } else {
                  ScalaBundle.message("navigation.title.overriding.member", name, overrides.length.toString) ->
                    ScalaBundle.message("navigation.findUsages.title.overriding.member", name)
                }

              val renderer = newCellRenderer
              val overridesArray = overrides.toArray[PsiElement]
              util.Arrays.sort(overridesArray, renderer.getComparator)
              ScalaNavigationUtils.navigate(event, overridesArray, project, title, findUsagesTitle, renderer)
            }
          }
        }
    }

  }
}
