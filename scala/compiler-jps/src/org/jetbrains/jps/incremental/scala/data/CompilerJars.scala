package org.jetbrains.jps
package incremental.scala
package data

import java.io.File

/**
 * @author Pavel Fatin
 */
case class CompilerJars(library: File, compiler: File, extra: Seq[File])
