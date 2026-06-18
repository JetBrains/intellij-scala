package org.jetbrains.plugins.scala.projectHighlighting.base

import org.jetbrains.plugins.scala.util.TestUtils

object ProjectHighlightingTestUtils {

  val isProjectCachingEnabledPropertySet: Boolean =
    sys.props.get("project.highlighting.enable.cache").contains("true")

  //NOTE: when updating, please also update `org.jetbrains.scalateamcity.common.Caching.highlightingPatterns`
  def projectsRootPath: String = s"${TestUtils.getTestDataPath}/projectsForHighlightingTests"
}
