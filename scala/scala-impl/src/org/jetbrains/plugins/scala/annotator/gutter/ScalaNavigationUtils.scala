package org.jetbrains.plugins.scala.annotator.gutter

import com.intellij.codeInsight.navigation.PsiTargetNavigator
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.project.Project
import com.intellij.psi.{NavigatablePsiElement, PsiElement}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.gutter.ScalaMarkerType.newCellRenderer

import java.awt.event.MouseEvent

private[gutter]
object ScalaNavigationUtils {

  def navigate[T <: PsiElement](
    event: MouseEvent,
    targets: Array[T],
    project: Project,
    @Nls title: String,
    @Nls tabTitle: String,
    renderer: PsiElementListCellRenderer[T]
  ): Unit = {
    //noinspection ApiStatus,UnstableApiUsage
    new PsiTargetNavigator(targets)
      .tabTitle(tabTitle)
      .presentationProvider(renderer.computePresentation)
      .navigate(event, title, project)
  }

  def navigateToSuperMember[T <: NavigatablePsiElement](
    event: MouseEvent,
    members: Array[T],
    project: Project,
    @Nls title: String,
    @Nls findUsagesTitle: String,
    renderer: PsiElementListCellRenderer[T] = newCellRenderer.asInstanceOf[PsiElementListCellRenderer[T]]
  ): Unit = {
    navigate(event, members, project, title, findUsagesTitle, renderer)
  }

  def navigateToSuperType[T <: NavigatablePsiElement](event: MouseEvent, members: Array[T], project: Project, name: String): Unit = {
    val title = ScalaBundle.message("navigation.title.super.types", name)
    val findUsagesTitle = ScalaBundle.message("navigation.findUsages.title.super.types", name)
    navigateToSuperMember(event, members, project, title, findUsagesTitle)
  }

  def navigateToSuperMember[T <: NavigatablePsiElement](event: MouseEvent, members: Array[T], project: Project, name: String): Unit = {
    val title = ScalaBundle.message("navigation.title.super.members", name)
    val findUsagesTitle = ScalaBundle.message("navigation.findUsages.title.super.members", name)
    navigateToSuperMember(event, members, project, title, findUsagesTitle)
  }
}
