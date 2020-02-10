package org.jetbrains.plugins.scala
package base

import com.intellij.openapi.module.Module
import junit.framework.{AssertionFailedError, Test, TestListener, TestResult}
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader

import scala.collection.immutable.SortedSet
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

trait ScalaSdkOwner extends Test with InjectableJdk  {
  import ScalaSdkOwner._

  implicit final def version: ScalaVersion = {
    val supportedVersions = allTestVersions.filter(supportedIn)
    val configuredVersion = configuredScalaVersion.getOrElse(defaultSdkVersion)
    selectVersion(configuredVersion, supportedVersions)
  }

  private var _injectedScalaVersion: Option[ScalaVersion] = None
  def injectedScalaVersion: Option[ScalaVersion] = _injectedScalaVersion
  def injectedScalaVersion_=(version: ScalaVersion): Unit = _injectedScalaVersion = Some(version)

  protected def localConfiguredScalaVersion: Option[ScalaVersion] = injectedScalaVersion

  protected def configuredScalaVersion: Option[ScalaVersion] = localConfiguredScalaVersion.orElse(globalConfiguredScalaVersion)

  protected def supportedIn(version: ScalaVersion): Boolean = true

  protected def librariesLoaders: Seq[LibraryLoader]

  protected lazy val myLoaders: ListBuffer[LibraryLoader] = mutable.ListBuffer.empty[LibraryLoader]

  protected def setUpLibraries(implicit module: Module): Unit = {

    librariesLoaders.foreach { loader =>
      myLoaders += loader
      loader.init
    }
  }

  protected def disposeLibraries(implicit module: Module): Unit = {
    myLoaders.foreach(_.clean)
    myLoaders.clear()
  }

  def skip: Boolean = configuredScalaVersion.exists(!supportedIn(_))

  abstract override def run(result: TestResult): Unit = {
    if (!skip) {
      // Need to initialize before test is run because all tests fields can be reset to null
      // (including injectedScalaVersion) after test is finished
      // see HeavyPlatformTestCase.runBare & UsefulTestCase.clearDeclaredFields
      val scalaVersionMessage = {
        val detail = configuredScalaVersion match {
          case Some(value) if value != version => s" (configured: $value)"
          case _                               => ""
        }
        s"### Scala version used: ${version.minor}$detail, jdk: $testProjectJdkVersion ###"
      }
      lazy val logVersion: Unit = System.err.println(scalaVersionMessage) // lazy val to log only once
      val listener = new TestListener {
        override def addError(test: Test, t: Throwable): Unit = logVersion
        override def addFailure(test: Test, t: AssertionFailedError): Unit = logVersion
        override def endTest(test: Test): Unit = ()
        override def startTest(test: Test): Unit = ()
      }
      result.addListener(listener)
      super.run(result)
      result.removeListener(listener)
    }
  }
}

object ScalaSdkOwner {
  // todo: eventually move to version Scala_2_13
  //       (or better, move ScalaLanguageLevel.getDefault to Scala_2_13 and use ScalaVersion.default again)
  val defaultSdkVersion: ScalaVersion = Scala_2_10 // ScalaVersion.default
  val allTestVersions: SortedSet[ScalaVersion] = {
    val allScalaMinorVersions = for {
      latestVersion <- ScalaVersion.allScalaVersions.filterNot(_ == Scala_3_0)
      minor <- 0 to latestVersion.minorSuffix.toInt
    } yield latestVersion.withMinor(minor)

    SortedSet(allScalaMinorVersions :+ Scala_3_0: _*)
  }

  private def selectVersion(wantedVersion: ScalaVersion, possibleVersions: SortedSet[ScalaVersion]): ScalaVersion =
    possibleVersions.iteratorFrom(wantedVersion).toStream.headOption.getOrElse(possibleVersions.last)

  lazy val globalConfiguredScalaVersion: Option[ScalaVersion] = {
    val property = scala.util.Properties.propOrNone("scala.sdk.test.version")
      .orElse(scala.util.Properties.envOrNone("SCALA_SDK_TEST_VERSION"))
    property.map(
      ScalaVersion.fromString(_).filter(allTestVersions.contains).getOrElse(
        throw new AssertionError(
          "Scala SDK Version specified in environment variable SCALA_SDK_TEST_VERSION is not one of "
            + allTestVersions.mkString(", ")
        )
      )
    )
  }
}
