package org.jetbrains.plugins.scala.testingSupport.test.scalatest

import junit.framework.Assert
import org.jetbrains.plugins.scala.base.{ScalaLightPlatformCodeInsightTestCaseAdapter, TestScalaProjectSettings}

class ScalaTestTestFrameworkTest extends ScalaLightPlatformCodeInsightTestCaseAdapter with TestScalaProjectSettings {

  val scalaTestFramework = new ScalaTestTestFramework

  def testDefaultSuperClass(): Unit = {

    scalaProjectSettings.setScalaTestDefaultSuperClass("org.scalatest.FlatSpec")
    Assert.assertEquals("org.scalatest.FlatSpec", scalaTestFramework.getDefaultSuperClass)

    scalaProjectSettings.setScalaTestDefaultSuperClass("org.scalatest.WordSPec")
    Assert.assertEquals("org.scalatest.WordSPec", scalaTestFramework.getDefaultSuperClass)
  }

}
