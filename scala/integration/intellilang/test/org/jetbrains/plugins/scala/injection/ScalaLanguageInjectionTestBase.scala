package org.jetbrains.plugins.scala.injection

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

import scala.language.implicitConversions

/** @see AbstractLanguageInjectionTestCase.kt in main IntelliJ repository */
abstract class ScalaLanguageInjectionTestBase extends ScalaLightCodeInsightFixtureTestCase {

  protected var scalaInjectionTestFixture: ScalaInjectionTestFixture = _

  override protected def setUp(): Unit = {
    super.setUp()

    scalaInjectionTestFixture = new ScalaInjectionTestFixture(getProject, myFixture)
  }

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_13
}

