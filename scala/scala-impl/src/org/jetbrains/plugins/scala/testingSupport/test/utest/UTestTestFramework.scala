package org.jetbrains.plugins.scala
package testingSupport.test.utest

import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework

class UTestTestFramework extends AbstractTestFramework {

  override def getName: String = "uTest"

  override def getTestFileTemplateName = "uTest Object"

  override def getMnemonic: Char = 'm'

  override def getMarkerClassFQName: String = "utest.framework.TestSuite"

  override def getDefaultSuperClass: String = "utest.framework.TestSuite" // TODO: base class has changed to utest.TestSuite

  override def getSuitePaths: Seq[String] = UTestUtil.suitePaths

  override protected def getAdditionalBuildCommands(scalaVersion: Option[String]): Seq[String] = Seq()

  override protected def getLibraryDependencies(scalaVersion: Option[String]): Seq[String] = scalaVersion match {
    case Some(v) if v.startsWith("2.11") => Seq(""""com.lihaoyi" % "utest_2.11" % "latest.integration"""")
    case Some(v) if v.startsWith("2.10") => Seq(""""com.lihaoyi" % "utest_2.10" % "latest.integration"""")
    case _                               => Seq(""""com.lihaoyi" %% "utest" % "latest.integration"""")
  }

  override protected def getLibraryResolvers(scalaVersion: Option[String]): Seq[String] = Seq()
}
