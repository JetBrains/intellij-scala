package org.jetbrains.sbt.project

import com.intellij.notification.Notification
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.notifications.CollectingNotificationsListener
import org.jetbrains.sbt.project.ProjectStructureDsl.project
import org.jetbrains.sbt.project.utils.ProjectStructureComparisonContext

final class ProjectStructureAssertionsFixture(
  project: Project,
  matcher: ProjectStructureMatcher = ProjectStructureAssertionsFixture.DefaultMatcher
) {

  def defaultCompareContext: ProjectStructureComparisonContext =
    ProjectStructureComparisonContext.Implicit.default(using project)

  def assertProjectsEqual(
    expected: project,
    singleContentRootModules: Boolean = true
  )(using compareContext: ProjectStructureComparisonContext = defaultCompareContext): Unit =
    matcher.assertProjectsEqual(expected, project, singleContentRootModules)

  def assertNoNotificationsShown(
    notifications: Seq[Notification] = Nil,
    mutedNotificationTitles: Seq[String] = Nil
  ): Unit =
    matcher.assertNoNotificationsShown(project, notifications, mutedNotificationTitles)

  def subscribeOnWarningsAndErrors(): CollectingNotificationsListener =
    CollectingNotificationsListener.subscribeOnWarningsAndErrors(project)
}

object ProjectStructureAssertionsFixture {
  private object DefaultMatcher extends ProjectStructureMatcher with ExactMatch
}
