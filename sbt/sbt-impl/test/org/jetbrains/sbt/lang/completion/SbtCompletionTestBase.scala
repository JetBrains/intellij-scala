package org.jetbrains.sbt.lang.completion

import org.jetbrains.plugins.scala.lang.completion3.base.ScalaCompletionTestBase
import org.jetbrains.sbt.language.SbtFileType

abstract class SbtCompletionTestBase extends ScalaCompletionTestBase {

  protected override def setUp(): Unit = {
    super.setUp()
    scalaFixture.setDefaultFileType(SbtFileType)
  }
}
