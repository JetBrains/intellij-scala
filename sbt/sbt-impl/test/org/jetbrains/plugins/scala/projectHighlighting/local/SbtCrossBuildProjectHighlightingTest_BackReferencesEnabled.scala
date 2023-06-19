package org.jetbrains.plugins.scala.projectHighlighting.local

class SbtCrossBuildProjectHighlightingTest_BackReferencesEnabled extends SbtCrossBuildProjectHighlightingTestBase {

  override def testHighlighting(): Unit = {
    withEnabledBackReferencesFromSharedSources(enabled = true) {
      super.testHighlighting()
    }
  }
}
