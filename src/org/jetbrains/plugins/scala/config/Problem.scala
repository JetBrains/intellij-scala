package org.jetbrains.plugins.scala.config

import java.io.File

/**
 * Pavel.Fatin, 01.07.2010
 */

sealed abstract case class Problem(message: String)

case class NotScalaSDK extends Problem("Not valid Scala SDK")

case class ComplierMissing(version: String) 
        extends Problem("Compiler library version %s missing in Scala SDK or maven repository".format(version))

case class InvalidArchive(file: File) 
        extends Problem("Invalid archive file in Scala SDK: %s".format(file.getPath))

case class InconsistentVersions(library: String, compiler: String) 
        extends Problem("Library / compiler versions mismatch: %s / %s".format(library, compiler))

case class UnsupportedVersion(version: String) 
        extends Problem("Unsupported Scala version: %s".format(version))