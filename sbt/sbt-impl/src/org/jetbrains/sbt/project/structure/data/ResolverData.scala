package org.jetbrains.sbt.project.structure.data

import org.jetbrains.sbt.project.structure.data.Helpers.uri

case class ResolverData(name: String, root: String)

object ResolverData:
  given XmlDeserializer[ResolverData] = what =>
    val name = (what \ "@name").text
    val root = (what \ "@root").text
    val canonRoot = root.uri.normalize().toString
    Right(ResolverData(name, canonRoot))
