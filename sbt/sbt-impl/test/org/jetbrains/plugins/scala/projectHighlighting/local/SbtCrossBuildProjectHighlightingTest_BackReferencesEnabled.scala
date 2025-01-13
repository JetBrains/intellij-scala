package org.jetbrains.plugins.scala.projectHighlighting.local

import com.intellij.openapi.util.registry.Registry

class SbtCrossBuildProjectHighlightingTest_BackReferencesEnabled extends SbtCrossBuildProjectHighlightingTestBase {

  override def setUp(): Unit = {
    super.setUp()

    //disable AstLoadingFilter, enabled in super-class TODO: fix SCL-23436 and remove this disabling
    Registry.get("ast.loading.filter").setValue(false, getTestRootDisposable)
  }


  override def testHighlighting(): Unit = {
    withEnabledBackReferencesFromSharedSources(enabled = true) {
      super.testHighlighting()
    }
  }
}
