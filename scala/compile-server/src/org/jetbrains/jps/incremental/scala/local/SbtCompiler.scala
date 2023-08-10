package org.jetbrains.jps.incremental.scala
package local

import org.jetbrains.jps.incremental.scala.local.zinc.Utils.virtualFileConverter
import org.jetbrains.jps.incremental.scala.local.zinc._
import org.jetbrains.plugins.scala.compiler.data.{CompilationData, CompileOrder}
import sbt.internal.inc._
import xsbti.compile.analysis.ReadStamps
import xsbti.compile.{CompileOrder => SbtCompileOrder, _}

import java.io.File
import java.util.Optional
import scala.jdk.OptionConverters._
import scala.util.Try
import scala.util.matching.Regex

class SbtCompiler(javaTools: JavaTools, optScalac: Option[ScalaCompiler], fileToStore: File => AnalysisStore) extends AbstractCompiler {

  private val stampReaderCache: Cache[true, ReadStamps] = new Cache(1)

  override def compile(compilationData: CompilationData, client: Client): Unit = optScalac match {
    case Some(scalac) => doCompile(compilationData, client, scalac)
    case _ =>
      client.error(CompileServerBundle.message("no.scalac.found.to.compile.scala.sources", compilationData.sources.take(10).mkString("\n")))
  }

  private def doCompile(compilationData: CompilationData, client: Client, scalac: ScalaCompiler): Unit = {
    client.progress(CompileServerBundle.message("loading.cached.results"))

    val incrementalCompiler = new IncrementalCompilerImpl

    val order = compilationData.order match {
      case CompileOrder.Mixed => SbtCompileOrder.Mixed
      case CompileOrder.JavaThenScala => SbtCompileOrder.JavaThenScala
      case CompileOrder.ScalaThenJava => SbtCompileOrder.ScalaThenJava
    }

    val analysisStore = fileToStore(compilationData.cacheFile)
    val zincMetadata = CompilationMetadata.load(analysisStore, client, compilationData)
    import zincMetadata._

    client.progress(CompileServerBundle.message("searching.for.changed.files"))

    val progress = getProgress(client, compilationData.sources.size)
    val reporter = getReporter(client)
    val logger = getLogger(client, zincLogFilter)

    val intellijLookup = IntellijExternalLookup(compilationData, client, cacheDetails.isCached)
    val intellijClassfileManager = new IntellijClassfileManager

    DefinesClassCache.invalidateCacheIfRequired(compilationData.zincData.compilationStartDate)

    val incOptions = IncOptions.of()
      .withExternalHooks(IntelljExternalHooks(intellijLookup, intellijClassfileManager))
      .withRecompileOnMacroDef(Optional.of(boolean2Boolean(false)))
      .withTransitiveStep(5) // Default 3 was not enough for us

    val compilers = incrementalCompiler.compilers(javaTools, scalac)
    val setup = incrementalCompiler.setup(
      IntellijEntryLookup(compilationData, fileToStore),
      skip = false,
      compilationData.cacheFile.toPath,
      CompilerCache.fresh,
      incOptions,
      reporter,
      Option(progress),
      None,
      Array.empty)
    val previousResult = PreviousResult.create(
      Optional.of(previousAnalysis: CompileAnalysis),
      previousSetup.toJava
    )

    val stampReader = stampReaderCache.getOrUpdate(true)(Stamps.timeWrapBinaryStamps(virtualFileConverter))

    val inputs = incrementalCompiler.inputs(
      compilationData.classpath.toArray.map(file => Utils.virtualFileConverter.toVirtualFile(file.toPath)),
      compilationData.zincData.allSources.toArray.map(file => Utils.virtualFileConverter.toVirtualFile(file.toPath)),
      compilationData.output.toPath,
      None,
      compilationData.scalaOptions.toArray,
      compilationData.javaOptions.toArray,
      100,
      Array.empty,
      order,
      compilers,
      setup,
      previousResult,
      Optional.empty(),
      virtualFileConverter,
      stampReader
    )

    val compilationResult = Try {
      client.progress(CompileServerBundle.message("collecting.incremental.compiler.data"))
      val result: CompileResult = incrementalCompiler.compile(inputs, logger)

      if (result.hasModified || cacheDetails.isCached) {
        analysisStore.set(AnalysisContents.create(result.analysis(), result.setup()))

        val binaryToSource = BinaryToSource(result.analysis, compilationData)

        def findClassFileOnDisk(classFile: File): (File, String) = {
          val fullJavaName = binaryToSource.className(classFile)
          if (classFile.exists()) {
            (classFile, fullJavaName)
          } else {
            // The file doesn't exist on disk, it must have been compactified by scalac
            // https://github.com/scala/scala/blob/714c2f84fc3d093cb424f6e0c421ba3881d09bbc/src/reflect/scala/reflect/internal/StdNames.scala#L49
            // Classes that would result in class files with names longer than ~255 characters cannot be saved to disk,
            // because of limitations in the native file systems.
            // The Scala compiler uses a process called "compactification", which computes an md5 hash of the full
            // name of a class and uses the hash to shorten the name.
            // At a high level, the final name has the following form: prefix + marker + md5 + marker + suffix,
            // where the prefix, md5 and suffix are derived from the full name, and the marker is a fixed string defined
            // by the compiler.
            classFile.getParentFile.listFiles().map(_.getName).find {
              case SbtCompiler.CompactifiedName(prefix, suffix) =>
                val javaClassName = fullJavaName match {
                  case SbtCompiler.PackageAndClassPattern(_, cls) => cls
                  case cls => cls
                }
                javaClassName.startsWith(prefix) && javaClassName.endsWith(suffix)
              case _ => false
            }.map { fileName =>
              val file = classFile.toPath.getParent.resolve(fileName).toFile
              val withoutClass = fileName.dropRight(".class".length)
              val className = fullJavaName match {
                case SbtCompiler.PackageAndClassPattern(pkg, _) => pkg + withoutClass
                case _ => withoutClass
              }
              (file, className)
            }.getOrElse((classFile, fullJavaName))
          }
        }

        intellijClassfileManager.deletedDuringCompilation().foreach(_.filter(_.getName.endsWith(".class")).foreach { cf =>
          val (file, _) = findClassFileOnDisk(cf)
          client.deleted(file)
        })

        def processGeneratedFile(classFile: File): Unit = {
          for (source <- binaryToSource.classfileToSources(classFile)) {
            val (file, className) = findClassFileOnDisk(classFile)
            client.generated(source, file, className)
          }
        }

        intellijClassfileManager.generatedDuringCompilation().flatten.foreach(processGeneratedFile)

        if (cacheDetails.isCached)
          previousAnalysis.stamps.allProducts.foreach { virtualFileRef =>
            processGeneratedFile(virtualFileConverter.toPath(virtualFileRef).toFile)
          }
      }
      result
    }

    compilationResult.recover {
      case _: CompileFailed =>
        // The error should be already handled via the `reporter`
        // However we need to invalidate source from last compilation
        val sourcesForInvalidation: Iterable[File] =
          if (intellijClassfileManager.deletedDuringCompilation().isEmpty) compilationData.sources
          else BinaryToSource(previousAnalysis, compilationData)
            .classfilesToSources(intellijClassfileManager.deletedDuringCompilation().last) ++ compilationData.sources

        sourcesForInvalidation.foreach(source => client.sourceStarted(source.getAbsolutePath))
      case e: Throwable =>
        // Invalidate analysis
        previousSetup.foreach(previous => analysisStore.set( AnalysisContents.create(Analysis.empty, previous)))

        // Keep files dirty
        compilationData.zincData.allSources.foreach(source => client.sourceStarted(source.getAbsolutePath))

        val msg = CompileServerBundle.message("compilation.failed.when.compiling", compilationData.output, e.getMessage, e.getStackTrace.mkString("\n  "))
        client.error(msg, None)
    }

    zincMetadata.compilationFinished(compilationData, compilationResult, intellijClassfileManager, cacheDetails)
  }
}

private object SbtCompiler {
  private val CompactifiedName: Regex = """(.*)\$\$\$\$[0-9a-f]*\$\$\$\$(.*)\.class""".r
  private val PackageAndClassPattern: Regex = """(.*\.)(.*)$""".r
}
