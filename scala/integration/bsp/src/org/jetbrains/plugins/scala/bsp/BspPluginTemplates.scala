package org.jetbrains.plugins.scala.bsp

import scala.io.Source

object BspPluginTemplates {
  val defaultScalaProjectViewContent: String = {
    val inputStream = getClass.getResourceAsStream("/templates/defaultprojectview.scalaproject") // TODO: is this the right name?
    try {
      Option(inputStream)
        .map(Source.fromInputStream(_).mkString)
        .getOrElse("")
    } finally {
      inputStream.close()
    }
  }
}

