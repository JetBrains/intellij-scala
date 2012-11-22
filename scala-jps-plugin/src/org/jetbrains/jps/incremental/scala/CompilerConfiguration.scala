package org.jetbrains.jps.incremental.scala

import java.io.File

/**
 * @author Pavel Fatin
 */
case class CompilerConfiguration(compilerJar: File, libraryJar: File, extraJars: Seq[File], javaHome: File)