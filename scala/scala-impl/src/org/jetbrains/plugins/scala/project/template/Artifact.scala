package org.jetbrains.plugins.scala
package project
package template

import java.io.{BufferedInputStream, File, IOException}
import java.net.URL
import java.util.Properties
import scala.collection.immutable.ListSet
import scala.util.Using

sealed abstract class Artifact(
  val prefix: String,
  val propertiesResource: Option[String] = None
) {

  private val fileNameRegex = (prefix + "-(.*?)(?:-src|-sources|-javadoc)?\\.jar").r

  def versionOf(file: File): Option[String] = {
    val fromName = versionFromFileName(file.getName)
    val result = fromName.orElse(versionFromPropertyFile(file.toURI.toString))
    result
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

  // includes
  //  - org.scala-lang.scala-library
  //  - org.scala-lang.scala3-library
  // and some artifacts from
  //  - org.scala-lang.modules.*
  val ScalaLibraryAndModulesArtifacts: ListSet[Artifact] = ListSet(
    ScalaLibrary,
    Scala3Library,
    //
    ScalaReflect,
    ScalaXml,
    ScalaSwing,
    ScalaCombinators,
    ScalaActors
  )

  val ScalaArtifacts: ListSet[Artifact] = ListSet(
    ScalaCompiler,
    Scala3Compiler,
    Scala3Interfaces,
    TastyCore
  ) ++ ScalaLibraryAndModulesArtifacts

  private def readProperty(jarFileUri: String, resource: String, property: String) =
    try {
      val url = new URL(s"jar:$jarFileUri!/$resource")
      Option(url.openStream).flatMap { in =>
        Using.resource(new BufferedInputStream(in)) { inStream =>
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

  // Scala3
  case object Scala3Library extends Artifact("scala3-library_3") // in scala3-library, there is no library.properties, the one from scala2 (scala-library) is used
  case object Scala3Compiler extends Artifact("scala3-compiler_3", Some("compiler.properties"))
  case object Scala3Interfaces extends Artifact("scala3-interfaces")  // NOTE: scala3-interfaces doesn't have `_3` suffix because it only contains Java interfaces
  case object TastyCore extends Artifact("tasty-core_3")
}