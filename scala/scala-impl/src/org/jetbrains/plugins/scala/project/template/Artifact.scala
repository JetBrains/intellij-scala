package org.jetbrains.plugins.scala
package project
package template

import java.io.{BufferedInputStream, File, IOException}
import java.net.URL
import java.util.Properties

/**
 * @author Pavel Fatin
 */
sealed class Artifact(val prefix: String,
                      val resource: Option[String] = None) {

  private val fileNameRegex = (prefix + "-(.*?)(?:-src|-sources|-javadoc)?\\.jar").r

  def versionOf(file: File): Option[Version] =
    externalVersionOf(file.getName)
      .orElse(internalVersionOf(file.toURI.toString))
      .map(Version(_))

  private def externalVersionOf(fileName: String) = fileName match {
    case fileNameRegex(number) => Some(number)
    case _ => None
  }

  private def internalVersionOf(fileUri: String) =
    resource.flatMap {
      Artifact.readProperty(fileUri, _)
    }
}

object Artifact {
  val ScalaArtifacts: Set[Artifact] = Set(
    ScalaLibrary,
    ScalaCompiler,
    ScalaReflect,
    ScalaXml,
    ScalaSwing,
    ScalaCombinators,
    ScalaActors
  )

  private def readProperty(fileUri: String,
                           resource: String) =
    try {
      val url = new URL(s"jar:$fileUri!/$resource")
      Option(url.openStream).flatMap { in =>
        using(new BufferedInputStream(in)) { inStream =>
          val properties = new Properties()
          properties.load(inStream)
          Option(properties.getProperty("version.number"))
        }
      }
    } catch {
      case _: IOException => None
    }

  // Scala

  case object ScalaLibrary extends Artifact("scala-library", Some("library.properties"))

  case object ScalaCompiler extends Artifact("scala-compiler", Some("compiler.properties"))

  case object ScalaReflect extends Artifact("scala-reflect", Some("reflect.properties"))

  case object ScalaXml extends Artifact("scala-xml")

  case object ScalaSwing extends Artifact("scala-swing")

  case object ScalaCombinators extends Artifact("scala-parser-combinators")

  case object ScalaActors extends Artifact("scala-actors")

}