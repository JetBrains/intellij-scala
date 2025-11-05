package org.jetbrains.sbt.project.structure.data

import org.jetbrains.plugins.scala.extensions.PathExt

import java.net.URI
import java.nio.file.Paths
import scala.language.implicitConversions
import scala.xml.Node

private object Helpers:

  extension (node: Node)
    infix def !(name: String): Node = node \ name match
      case Seq() => throw new RuntimeException("None of " + name + " nodes is found in " + node)
      case Seq(child) => child
      case _ => throw new RuntimeException("Multiple " + name + " nodes are found in " + node)

  extension (str: String)
    def uri: URI = canonUri(new URI(str.replace("\\", "/"))) // handle windows separators

  private def canonUri(uri: URI): URI =
    val uri1 =
      if uri.getScheme == "file" then Paths.get(uri).toCanonicalPath.toUri
      else uri
    uri1.normalize()
