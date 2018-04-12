package org.jetbrains.bsp

import java.io.File
import java.net.URI
import java.nio.file.Paths

object BspUtil {

  implicit class BspStringOps(str: String) {

    /** interpret string as URI. */
    def toURI: URI = new URI(str) // TODO handle error

    /** Interpret string as file URI. */
    def toFileAsURI: File = Paths.get(str.toURI).toFile // TODO handle error
  }

}
