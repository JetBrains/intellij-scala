package org.jetbrains.plugins.scala.compiler.data

import java.nio.file.Path

case class CompilationData(sources: Seq[Path],
                           classpath: Seq[Path],
                           output: Path,
                           scalaOptions: Seq[String],
                           javaOptions: Seq[String],
                           order: CompileOrder,
                           cacheFile: Path,
                           outputToCacheMap: Map[Path, Path],
                           outputGroups: Seq[(Path, Path)],
                           zincData: ZincData)
