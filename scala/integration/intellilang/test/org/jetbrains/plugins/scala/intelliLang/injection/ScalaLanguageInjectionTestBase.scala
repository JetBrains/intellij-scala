package org.jetbrains.plugins.scala.intelliLang.injection

import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

import scala.compiletime.uninitialized
import scala.language.implicitConversions

/** @see AbstractLanguageInjectionTestCase.kt in main IntelliJ repository */
abstract class ScalaLanguageInjectionTestBase extends ScalaLightCodeInsightFixtureTestCase {

  protected var scalaInjectionTestFixture: ScalaInjectionTestFixture = uninitialized

  override protected def setUp(): Unit = {
    super.setUp()

    scalaInjectionTestFixture = new ScalaInjectionTestFixture(getProject, myFixture)
  }

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version >= ScalaVersion.Latest.Scala_2_13
}

