package org.jetbrains.plugins.scala
package testingSupport.test.utest

import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework

/**
 * @author Roman.Shein
 *         Date: 21.04.14
 */
class UTestTestFramework extends AbstractTestFramework {

  def getDefaultSuperClass: String = "utest.framework.TestSuite"

  def getName: String = "uTest"

  def getMarkerClassFQName: String = "utest.framework.TestSuite"

  def getMnemonic: Char = 'm'

  override def generateObjectTests = true

  override protected def getLibraryDependencies(scalaVersion: Option[String]): Seq[String] = scalaVersion match {
    case Some(v) if v.startsWith("2.11") => Seq("\"com.lihaoyi\" % \"utest_2.11\" % \"latest.integration\"")
    case Some(v) if v.startsWith("2.10") => Seq("\"com.lihaoyi\" % \"utest_2.10\" % \"latest.integration\"")
    case _ => Seq("\"com.lihaoyi\" %% \"utest\" % \"latest.integration\"")
  }

  override protected def getLibraryResolvers(scalaVersion: Option[String]): Seq[String] = Seq()

  override protected def getAdditionalBuildCommands(scalaVersion: Option[String]): Seq[String] = Seq()

  override def getSuitePaths: Seq[String] = UTestUtil.suitePaths
}
