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
                      val propertiesResource: Option[String] = None) {

  private val fileNameRegex = (prefix + "-(.*?)(?:-src|-sources|-javadoc)?\\.jar").r

  def versionOf(file: File): Option[String] = {
    val version = versionFromFileName(file.getName)
    version.orElse(versionFromPropertyFile(file.toURI.toString))
  }

  private def versionFromFileName(fileName: String): Option[String] = fileName match {
    case fileNameRegex(number) => Some(number)
    case _ => None
  }

  private def versionFromPropertyFile(fileUri: String): Option[String] =
    propertiesResource.flatMap {
      Artifact.readProperty(fileUri, _, "version.number")
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

  private def readProperty(jarFileUri: String, resource: String, property: String) =
    try {
      val url = new URL(s"jar:$jarFileUri!/$resource")
      Option(url.openStream).flatMap { in =>
        using(new BufferedInputStream(in)) { inStream =>
          val properties = new Properties()
          properties.load(inStream)
          Option(properties.getProperty(property))
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