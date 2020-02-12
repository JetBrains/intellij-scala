package org.jetbrains.plugins.scala.compiler.data

import java.io.File

import org.jetbrains.plugins.scala.compiler.IncrementalityType

/**
 * @author Pavel Fatin
 */
case class CompilerData(compilerJars: Option[CompilerJars],
                        javaHome: Option[File],
                        incrementalType: IncrementalityType)
