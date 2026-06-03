package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.plugins.scala.compiler.data.{CompilerData, CompilerJars, SbtData}
import sbt.internal.inc.AnalyzingCompiler
import xsbti.compile.AnalysisStore

import java.nio.file.Path

class CachingFactory(delegate: CompilerFactory, compilersLimit: Int, scalacLimit: Int) extends CompilerFactory {
  private val compilerCache = new Cache[CompilerData, Compiler](compilersLimit)

  private val scalacCache = new Cache[(SbtData, Option[CompilerJars]), Option[AnalyzingCompiler]](scalacLimit)

  override def createCompiler(compilerData: CompilerData, client: Client, fileToStore: Path => AnalysisStore): Compiler = {
    compilerCache.getOrUpdate(compilerData) { () =>
      delegate.createCompiler(compilerData, client, fileToStore)
    }
  }

  override def getScalac(sbtData: SbtData, compilerJars: Option[CompilerJars], client: Client): Option[AnalyzingCompiler] = {
    scalacCache.getOrUpdate((sbtData, compilerJars)) { () =>
      delegate.getScalac(sbtData, compilerJars, client)
    }
  }
}
