package scala.macros.macroannotations

import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

import scala.meta.Compilable

abstract class MacroAnnotationTestBase extends JavaCodeInsightFixtureTestCase with Compilable {
  override def rootProject = getProject
  override def rootModule = myModule
}
