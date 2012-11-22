package org.jetbrains.jps.incremental.scala

import data.{CompilationData, JavaData, SbtData}
import org.jetbrains.jps.incremental.MessageHandler
import java.io.File
import sbt._
import inc.{AnalysisFormats, FileBasedStore, AnalysisStore}
import xsbti.compile.CompileProgress
import xsbti.CompileFailed

/**
 * @author Pavel Fatin
 */
class CompilerRunner(compilerName: String, messageHandler: MessageHandler, fileHandler: FileHandler, progress: CompileProgress) {
  def compile(sources: Array[File], sbtData: SbtData, javaData: JavaData, compilationData: CompilationData, cacheFile: File) {
    try {
      doCompile(sources, sbtData, javaData, compilationData, cacheFile)
    } catch {
      case _: CompileFailed => // the problem should be already reported
    }
  }

  private def doCompile(sources: Array[File], sbtData: SbtData, javaData: JavaData, compilationData: CompilationData, cacheFile: File) {
    val compilerFactory = new CompilerFactoryImpl(sbtData.getSbtInterface,
      sbtData.getCompilerInterfaceSources, sbtData.getCompilerInterfacesHome) with Caching

    val compilerConfiguration = CompilerConfiguration(compilationData.getCompilerJar,
      compilationData.getLibraryJar, compilationData.getExtraJars, javaData.getHome)

    val compiler = compilerFactory.createCompiler(compilerConfiguration, CompilerRunner.createAnalysisStore, messageHandler)

    compiler.compile(sources.toSeq, compilationData.getCompilationClasspath.toSeq, compilationData.getCompilerOptions.toSeq,
      compilationData.getOutputDirectory, compilationData.isScalaFirst, cacheFile, messageHandler, fileHandler, progress)
  }
}

private object CompilerRunner {
  private def createAnalysisStore(cacheFile: File): AnalysisStore = {
    import sbinary.DefaultProtocol.{immutableMapFormat, immutableSetFormat, StringFormat, tuple2Format}
    import sbt.inc.AnalysisFormats._
    val store = FileBasedStore(cacheFile)(AnalysisFormats.analysisFormat, AnalysisFormats.setupFormat)
    AnalysisStore.sync(AnalysisStore.cached(store))
  }
}
