package org.jetbrains.bsp

import java.io.File
import java.net.URI
import java.nio.file.Paths

import ch.epfl.scala.bsp.Uri

object BspUtil {

  implicit class BspUriOps(bspUri: Uri) {
    def toURI: URI = new URI(bspUri.value)
    def toFile: File = Paths.get(bspUri.toURI.getPath).toFile
  }

}
