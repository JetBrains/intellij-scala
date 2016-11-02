package org.jetbrains.plugins.scala
package testingSupport.test.specs2

import org.jetbrains.plugins.scala.testingSupport.test.AbstractTestFramework

/**
 * @author Ksenia.Sautina
 * @since 5/15/12
 */

class Specs2TestFramework extends AbstractTestFramework {

  def getDefaultSuperClass: String = "org.specs2.mutable.Specification"

  def getName: String = "Specs2"

  def getMarkerClassFQName: String = "org.specs2.mutable.Specification"

  def getMnemonic: Char = 'p'

  override protected def getLibraryDependencies(scalaVersion: Option[String]): Seq[String] = scalaVersion match {
    case Some(v) if v.startsWith("2.11") => Seq("\"org.specs2\" % \"specs2-core_2.11\" % \"latest.integration\" % \"test\"")
    case Some(v) if v.startsWith("2.10") => Seq("\"org.specs2\" % \"specs2-core_2.10\" % \"latest.integration\" % \"test\"")
    case _ => Seq("\"org.specs2\" %% \"specs2-core\" % \"latest.integration\" % \"test\"")
  }

  override protected def getLibraryResolvers(scalaVersion: Option[String]): Seq[String] = scalaVersion match {
    case Some(v) if v.startsWith("2.11") => Seq("\"scalaz-bintray\" at \"http://dl.bintray.com/scalaz/releases\"")
    case Some(v) if v.startsWith("2.10") => Seq("\"scalaz-bintray\" at \"http://dl.bintray.com/scalaz/releases\"")
    case _ => Seq()
  }

  override protected def getAdditionalBuildCommands(scalaVersion: Option[String]): Seq[String] =
    Seq("\"-Yrangepos\"")

  override def getSuitePaths: Seq[String] = Specs2Util.suitePaths
}
