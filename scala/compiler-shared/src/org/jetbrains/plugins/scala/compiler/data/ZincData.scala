package org.jetbrains.plugins.scala.compiler.data

import java.nio.file.Path

case class ZincData(allSources: Seq[Path],
                    compilationStartDate: Long,
                    isCompile: Boolean)
