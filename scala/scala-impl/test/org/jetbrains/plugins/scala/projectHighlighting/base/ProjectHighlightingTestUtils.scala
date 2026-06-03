package org.jetbrains.plugins.scala.projectHighlighting.base

import com.intellij.testFramework.UsefulTestCase
import org.jetbrains.plugins.scala.util.{RevertableChange, TestUtils}

object ProjectHighlightingTestUtils {

  val isProjectCachingEnabledPropertySet: Boolean =
    sys.props.get("project.highlighting.enable.cache").contains("true")

  //NOTE: when updating, please also update `org.jetbrains.scalateamcity.common.Caching.highlightingPatterns`
  def projectsRootPath: String = s"${TestUtils.getTestDataPath}/projectsForHighlightingTests"

  def dontPrintErrorsAndWarningsToConsole(testCase: UsefulTestCase): Unit = {
    //See org.jetbrains.sbt.project.structure.SbtStructureDump.dontPrintErrorsAndWarningsToConsoleDuringTests
    //output from sbt process is already printed (presumably somewhere from ExternalSystemImportingTestCase or internals)
    RevertableChange
      .withModifiedSystemProperty("sbt.structure.dump.dontPrintErrorsAndWarningsToConsoleDuringTests", "true")
      .applyChange(testCase)
  }
}
