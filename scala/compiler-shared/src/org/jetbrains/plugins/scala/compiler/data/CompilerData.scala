package org.jetbrains.plugins.scala.compiler.data

import java.io.File

case class CompilerData(compilerJars: Option[CompilerJars],
                        javaHome: Option[File],
                        incrementalType: IncrementalityType)
