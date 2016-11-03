package org.jetbrains.plugins.scala
package project.template

import java.io.{BufferedInputStream, File, IOException, InputStream}
import java.net.URL
import java.util.Properties
import java.util.regex.Pattern

import org.jetbrains.plugins.scala.project.Version

/**
 * @author Pavel Fatin
 */
sealed class Artifact(val prefix: String, val resource: Option[String] = None) {
  def title: String = prefix + "*.jar"

  def versionOf(file: File): Option[Version] = externalVersionOf(file).orElse(internalVersionOf(file))

  private def externalVersionOf(file: File): Option[Version] = {
    val FileName = (prefix + "-(.*?)(?:-src|-sources|-javadoc)?\\.jar").r

    file.getName match {
      case FileName(number) => Some(Version(number))
      case _ => None
    }
  }

  private def internalVersionOf(file: File): Option[Version] =
    resource.flatMap(it => Artifact.readProperty(file, it, "version.number")).map(Version(_))
}

object Artifact {
  def values: Set[Artifact] = Set(ScalaLibrary, ScalaCompiler, ScalaReflect,
    ScalaXml, ScalaSwing, ScalaCombinators, ScalaActors)

  private def readProperty(file: File, resource: String, name: String): Option[String] = {
    try {
      val url = new URL("jar:%s!/%s".format(file.toURI.toString, resource))
      Option(url.openStream).flatMap(it => using(new BufferedInputStream(it))(readProperty(_, name)))
    } catch {
      case _: IOException => None
    }
  }

  private def readProperty(input: InputStream, name: String): Option[String] = {
    val properties = new Properties()
    properties.load(input)
    Option(properties.getProperty(name))
  }

  case object ScalaLibrary extends Artifact("scala-library", Some("library.properties"))

  case object ScalaCompiler extends Artifact("scala-compiler", Some("compiler.properties"))

  case object ScalaReflect extends Artifact("scala-reflect", Some("reflect.properties"))

  case object ScalaXml extends Artifact("scala-xml")

  case object ScalaSwing extends Artifact("scala-swing")

  case object ScalaCombinators extends Artifact("scala-parser-combinators")

  case object ScalaActors extends Artifact("scala-actors")
}

object DottyArtifact {
  val values: Set[Artifact] = Set(Main, Interfaces, JLine)

  case object Main extends Artifact("dotty_2.11")

  case object Interfaces extends Artifact("dotty-interfaces")

  case object JLine extends Artifact("jline")
}

sealed class Kind(regex: String) {
  def patternFor(prefix: String): Pattern = Pattern.compile(prefix + regex)
}

object Kind {
  def values: Set[Kind] = Set(Binaries, Sources, Docs)

  case object Binaries extends Kind(".*(?<!-src|-sources|-javadoc)\\.jar")

  case object Sources extends Kind(".*-(src|sources)\\.jar")

  case object Docs extends Kind(".*-javadoc\\.jar")
}

case class Component(artifact: Artifact, kind: Kind, version: Option[Version], file: File)

object Component {
  def discoverIn(artifacts: Set[Artifact], files: Seq[File]): Seq[Component] = {
    val patterns = artifacts.flatMap { artifact =>
      Kind.values.map(kind => (kind.patternFor(artifact.prefix), artifact, kind))
    }

    files.filter(it => it.isFile && it.getName.endsWith(".jar")).flatMap { file =>
      patterns.collect {
        case (pattern, artifact, kind) if pattern.matcher(file.getName).matches =>
          Component(artifact, kind, artifact.versionOf(file), file)
      }
    }
  }
  
  def discoverIn(files: Seq[File]): Seq[Component] = discoverIn(Artifact.values ++ DottyArtifact.values, files)
}
