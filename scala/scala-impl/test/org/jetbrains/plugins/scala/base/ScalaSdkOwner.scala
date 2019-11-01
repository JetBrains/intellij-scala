package org.jetbrains.plugins.scala
package base

import com.intellij.openapi.module.Module
import junit.framework.{Test, TestResult}
import org.jetbrains.plugins.scala.base.libraryLoaders.LibraryLoader

import scala.collection.immutable.SortedSet
import scala.collection.mutable
import scala.collection.mutable.ListBuffer

trait ScalaSdkOwner extends Test {
  import ScalaSdkOwner._

  implicit final def version: ScalaVersion = {
    val allSupportedVersions = allTestVersions.filter(supportedIn)
    selectVersion(configuredScalaVersion.getOrElse(defaultSdkVersion), allSupportedVersions)
  }

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

  abstract override def run(result: TestResult): Unit = {
    val skip =
      ScalaSdkOwner.configuredScalaVersion.exists(!supportedIn(_))

    if (!skip) {
      super.run(result)
    }
  }
}

object ScalaSdkOwner {
  // todo: eventually move to version Scala_2_13
  //       (or better, move ScalaLanguageLevel.getDefault to Scala_2_13 and use ScalaVersion.default again)
  val defaultSdkVersion: ScalaVersion = Scala_2_10 // ScalaVersion.default
  val allTestVersions: SortedSet[ScalaVersion] = SortedSet(ScalaVersion.allScalaVersions: _*)

  def selectVersion(wantedVersion: ScalaVersion, possibleVersions: SortedSet[ScalaVersion]): ScalaVersion =
    possibleVersions.iteratorFrom(wantedVersion).toStream.headOption.getOrElse(possibleVersions.last)

  lazy val configuredScalaVersion: Option[ScalaVersion] = {
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
