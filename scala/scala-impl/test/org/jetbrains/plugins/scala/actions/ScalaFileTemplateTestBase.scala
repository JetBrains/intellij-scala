package org.jetbrains.plugins.scala.actions

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

abstract class ScalaFileTemplateTestBase extends ScalaLightCodeInsightFixtureTestCase {

  /** @see [[FileTemplateTestUtils.initFileHeaderTemplate]] */
  protected def initFileHeaderTemplate(isEmpty: Boolean = true): Unit = {
    FileTemplateTestUtils.initFileHeaderTemplate(getProject, getTestRootDisposable, isEmpty)
  }
}

