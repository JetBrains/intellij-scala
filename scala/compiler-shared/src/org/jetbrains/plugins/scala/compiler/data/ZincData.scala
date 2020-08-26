package org.jetbrains.plugins.scala.compiler.data

import java.io.File

case class ZincData(allSources: collection.Seq[File],
                    compilationStartDate: Long,
                    isCompile: Boolean)
