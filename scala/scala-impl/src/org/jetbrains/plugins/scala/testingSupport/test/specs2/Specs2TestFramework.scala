package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework

class Specs2TestFramework extends AbstractTestFramework {

  override def getName: String = "Specs2"

  override def testFileTemplateName = "Specs2 Class"

  override def getMnemonic: Char = 'p'

  override def getMarkerClassFQName: String = "org.specs2.mutable.Specification"

  override def getDefaultSuperClass: String = "org.specs2.mutable.Specification"

  override def suitePaths: Seq[String] = Specs2Util.suitePaths

  override protected def getAdditionalBuildCommands(scalaVersion: Option[String]): Seq[String] = Seq("\"-Yrangepos\"")

  override protected def getLibraryDependencies(scalaVersion: Option[String]): Seq[String] = scalaVersion match {
    case Some(v) if v.startsWith("2.11") => Seq(""""org.specs2" % "specs2-core_2.11" % "latest.integration" % "test"""")
    case Some(v) if v.startsWith("2.10") => Seq(""""org.specs2" % "specs2-core_2.10" % "latest.integration" % "test"""")
    case _                               => Seq(""""org.specs2" %% "specs2-core" % "latest.integration" % "test"""")
  }

  override protected def getLibraryResolvers(scalaVersion: Option[String]): Seq[String] = scalaVersion match {
    case Some(v) if v.startsWith("2.11") => Seq("\"scalaz-bintray\" at \"https://dl.bintray.com/scalaz/releases\"")
    case Some(v) if v.startsWith("2.10") => Seq("\"scalaz-bintray\" at \"https://dl.bintray.com/scalaz/releases\"")
    case _ => Seq()
  }
}
