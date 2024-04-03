package org.jetbrains.plugins.scala.base

import com.intellij.util.containers.ContainerUtil
import junit.framework.{AssertionFailedError, Test, TestListener, TestResult}
import org.jetbrains.plugins.scala.{LatestScalaVersions, ScalaVersion}

import scala.collection.immutable.SortedSet

trait ScalaSdkOwner extends Test
  with InjectableJdk
  with ScalaVersionProvider
  with LibrariesOwner {

  import ScalaSdkOwner._

  override final implicit def version: ScalaVersion = {
    val configuredOpt = configuredScalaVersion
    configuredOpt match {
      case Some(exactVersion) =>
        exactVersion
      case None =>
        val supportedVersions = allTestVersions.filter(supportedIn)
        val defaultVersion = defaultVersionOverride.getOrElse(defaultSdkVersion)
        val selectedVersion = selectVersion(defaultVersion, supportedVersions)
        selectedVersion.orElse(
          ScalaVersion.Latest.scalaNext.find(supportedIn)
        ).getOrElse(sys.error("Could not find a Scala version matching the test criteria"))
    }
  }

  private var _injectedScalaVersion: Option[ScalaVersion] = None
  def injectedScalaVersion: Option[ScalaVersion] = _injectedScalaVersion
  def injectedScalaVersion_=(version: ScalaVersion): Unit = _injectedScalaVersion = Option(version)

  private def configuredScalaVersion: Option[ScalaVersion] =
    injectedScalaVersion.orElse(globalConfiguredScalaVersion)

  protected def supportedIn(version: ScalaVersion): Boolean = true

  protected def defaultVersionOverride: Option[ScalaVersion] = None

  def skip: Boolean = configuredScalaVersion.exists(!supportedIn(_))

  protected def buildVersionsDetailsMessage: String = {
    val detail = configuredScalaVersion match {
      case Some(value) if value != version => s" (configured: $value)"
      case _                               => ""
    }
    s"scala: ${version.minor}$detail, jdk: $testProjectJdkVersion"
  }

  protected def reportFailedTestContextDetails: Boolean = true

  abstract override def run(result: TestResult): Unit = {
    if (!skip) {
      // Need to initialize before test is run because all tests fields can be reset to null
      // (including injectedScalaVersion) after test is finished
      // see HeavyPlatformTestCase.runBare & UsefulTestCase.clearDeclaredFields
      val listener =
        if (reportFailedTestContextDetails) {
          var shouldLogVersionFor = ContainerUtil.newConcurrentSet[Test]()
          val versionsDetailMessage = s"### $buildVersionsDetailsMessage ###"

          Some(new TestListener {
            override def addError(test: Test, t: Throwable): Unit = shouldLogVersionFor.add(test)
            override def addFailure(test: Test, t: AssertionFailedError): Unit = shouldLogVersionFor.add(test)
            override def startTest(test: Test): Unit = ()
            override def endTest(test: Test): Unit = {
              if (shouldLogVersionFor.contains(test)) {
                System.err.println(versionsDetailMessage)
                shouldLogVersionFor.remove(test)
              }
            }
          })
        }
        else None

      listener.foreach(result.addListener)
      super.run(result)
      listener.foreach(result.removeListener)
    }
  }
}

object ScalaSdkOwner {

  // todo: eventually move to version Scala_2_13
  //       (or better, move ScalaLanguageLevel.getDefault to Scala_2_13 and use ScalaVersion.default again)
  //       for now just use defaultVersionOverride with Some(preferableSdkVersion) for test-(base)classes
  //       that should already work in newest version (SCL-15634)
  val defaultSdkVersion: ScalaVersion = LatestScalaVersions.Scala_2_10 // ScalaVersion.default
  val preferableSdkVersion: ScalaVersion = LatestScalaVersions.Scala_2_13
  val allTestVersions: SortedSet[ScalaVersion] = SortedSet.from(LatestScalaVersions.all.flatMap(_.generateAllMinorVersions()))

  private def selectVersion(wantedVersion: ScalaVersion, possibleVersions0: SortedSet[ScalaVersion]): Option[ScalaVersion] = {
    val possibleVersions = possibleVersions0.iteratorFrom(wantedVersion).toSeq
    if (possibleVersions.nonEmpty) {
      val first = possibleVersions.head
      if (first.isScala3) {
        // choose latest possible Scala 3 version
        //e.g. `supportedIn >= 3.0.2` -> 3.2.1
        //e.g. `supportedIn == 3.0.2` -> 3.0.2
        Some(possibleVersions.last)
      }
      else {
        //otherwise choose version closes to the "supportedIn"
        //e.g. `supportedIn >= 2.12.10` -> 2.12.10
        //TODO: unify this with Scala 3, test failures are expected
        Some(first)
      }
    }
    else possibleVersions0.lastOption
  }

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
