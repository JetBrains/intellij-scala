package org.jetbrains.plugins.scala

import com.intellij.testFramework.fixtures.CodeInsightTestFixture

/**
  * @author mucianm
  * @since 07.04.16.
  */
trait TestFixtureProvider {
  def getFixture: CodeInsightTestFixture

//  implicit final def module: Module = getFixture.getModule
}
