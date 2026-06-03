package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.testIntegration.TestFramework
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework.TestFrameworkSetupInfo
import org.jetbrains.plugins.scala.testingSupport.test.{AbstractTestFramework, TestFrameworkSetupSupportBase}

import scala.annotation.nowarn

final class ScalaTestTestFramework extends AbstractTestFramework with TestFrameworkSetupSupportBase {

  override def getName: String = "ScalaTest"

  override def testFileTemplateName = "ScalaTest Class"

  override def getMarkerClassFQName: String = "org.scalatest.Suite"

  override def baseSuitePaths: Seq[String] = ScalaTestUtil.suitePaths

  override def getDefaultSuperClass: String = {
    @nowarn("cat=deprecation")
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext())
    val scalaProjectSettings = ScalaProjectSettings.getInstance(project)
    scalaProjectSettings.getScalaTestDefaultSuperClass
  }

  override def frameworkSetupInfo(scalaVersion: Option[String]): TestFrameworkSetupInfo =
    TestFrameworkSetupInfo(Seq(""""org.scalatest" %% "scalatest" % "latest.integration" % Test"""), Seq())
}

object ScalaTestTestFramework {

  @deprecated("use `apply` instead", "2020.3")
  def instance: ScalaTestTestFramework = apply()

  def apply(): ScalaTestTestFramework =
    TestFramework.EXTENSION_NAME.findExtension(classOf[ScalaTestTestFramework])
}
