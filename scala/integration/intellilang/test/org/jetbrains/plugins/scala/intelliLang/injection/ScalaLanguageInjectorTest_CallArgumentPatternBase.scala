package org.jetbrains.plugins.scala.intelliLang.injection

import org.intellij.plugins.intelliLang.inject.config.{BaseInjection, InjectionPlace}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.intelliLang.injection.InjectionTestUtils.RegexpLangId

import scala.jdk.CollectionConverters.SeqHasAsJava

abstract class ScalaLanguageInjectorTest_CallArgumentPatternBase extends InjectionInBodyTestBase {

  override protected def supportedIn(version: ScalaVersion): Boolean =
    version == ScalaVersion.Latest.Scala_2_13

  private var testInjections: List[BaseInjection] = scala.compiletime.uninitialized

  override def setUp(): Unit = {
    super.setUp()
    testInjections = List.empty
  }

  override def tearDown(): Unit = {
    try {
      if (testInjections != null) {
        replaceInjections(testInjectionsToAdd = List.empty, testInjectionsToRemove = testInjections)
      }
    } finally {
      super.tearDown()
    }
  }

  protected final def registerRegexpCallArgumentPattern(injectionPlace: InjectionPlace): Unit = {
    val injection = createTestInjection(injectionPlace)
    testInjections = injection :: testInjections
    replaceInjections(testInjectionsToAdd = List(injection), testInjectionsToRemove = List.empty)
  }

  protected final def doRegexpInjectionTest(code: String, expectedText: String): Unit = {
    scalaInjectionTestFixture.doTest(
      RegexpLangId,
      code,
      expectedText
    )
  }

  private def createTestInjection(place: InjectionPlace): BaseInjection = {
    val injection = new BaseInjection("scala")
    injection.setInjectedLanguageId(RegexpLangId)
    injection.setInjectionPlaces(place)
    injection
  }

  private def replaceInjections(testInjectionsToAdd: List[BaseInjection], testInjectionsToRemove: List[BaseInjection]): Unit = {
    scalaInjectionTestFixture.intelliLangConfig.replaceInjections(
      testInjectionsToAdd.asJava,
      testInjectionsToRemove.asJava,
      false
    )
  }
}
