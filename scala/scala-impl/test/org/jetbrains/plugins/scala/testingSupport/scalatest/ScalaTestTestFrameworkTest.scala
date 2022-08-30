package org.jetbrains.plugins.scala.testingSupport.scalatest

import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.testingSupport.test.scalatest.ScalaTestTestFramework
import org.junit.Assert

class ScalaTestTestFrameworkTest extends ScalaLightCodeInsightFixtureTestCase {

  val scalaTestFramework = new ScalaTestTestFramework

  def testDefaultSuperClass(): Unit = {
    val scalaProjectSettings = ScalaProjectSettings.getInstance(getProject)

    scalaProjectSettings.setScalaTestDefaultSuperClass("org.scalatest.FlatSpec")
    Assert.assertEquals("org.scalatest.FlatSpec", scalaTestFramework.getDefaultSuperClass)

    scalaProjectSettings.setScalaTestDefaultSuperClass("org.scalatest.WordSPec")
    Assert.assertEquals("org.scalatest.WordSPec", scalaTestFramework.getDefaultSuperClass)
  }
}
