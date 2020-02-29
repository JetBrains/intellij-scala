package org.jetbrains.plugins.scala
package testingSupport.test.scalatest

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework

class ScalaTestTestFramework extends AbstractTestFramework {

  override def getName: String = "ScalaTest"

  override def testFileTemplateName = "ScalaTest Class"

  override def getMnemonic: Char = 'c'

  override def getMarkerClassFQName: String = "org.scalatest.Suite"

  override def getDefaultSuperClass: String = {
    val project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext())
    val scalaProjectSettings = ScalaProjectSettings.getInstance(project)
    scalaProjectSettings.getScalaTestDefaultSuperClass
  }

  override def suitePaths: Seq[String] = ScalaTestUtil.suitePaths

  override protected def getAdditionalBuildCommands(scalaVersion: Option[String]): Seq[String] = Seq()

  override protected def getLibraryDependencies(scalaVersion: Option[String]): Seq[String] = scalaVersion match {
    case Some(v) if v.startsWith("2.11") => Seq(""""org.scalatest" % "scalatest_2.11" % "latest.integration" % "test"""")
    case Some(v) if v.startsWith("2.10") => Seq(""""org.scalatest" % "scalatest_2.10" % "latest.integration" % "test"""")
    case Some(v) if v.startsWith("2.9")  => Seq(""""org.scalatest" % "scalatest_2.9" % "latest.integration" % "test"""")
    case _                               => Seq(""""org.scalatest" %% "scalatest" % "latest.integration" % "test"""")
  }

  override protected def getLibraryResolvers(scalaVersion: Option[String]): Seq[String] = Seq()
}
