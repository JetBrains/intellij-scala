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
    selectVersion(defaultSdkVersion, allSupportedVersions)
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
  val defaultSdkVersion: ScalaVersion = ScalaVersion.default
  val allTestVersions: SortedSet[ScalaVersion] = SortedSet(ScalaVersion.allScalaVersions: _*)

  def selectVersion(wantedVersion: ScalaVersion, possibleVersions: SortedSet[ScalaVersion]): ScalaVersion =
    possibleVersions.iteratorFrom(wantedVersion).toStream.headOption.getOrElse(possibleVersions.last)

  lazy val configuredScalaVersion: Option[ScalaVersion] = {
    scala.util.Properties.envOrNone("SCALA_SDK_TEST_VERSION").map { versionString =>
      ScalaVersion.allScalaVersions.find(_.toString == versionString).getOrElse(
        throw new AssertionError("SDK Version specified in environment variable SCALA_SDK_TEST_VERSION is not one of " +
          ScalaVersion.allScalaVersions.mkString(", "))
      )
    }
  }
}
