package org.jetbrains.plugins.scala.compiler.highlighting

import com.intellij.compiler.server.BuildManager
import com.intellij.openapi.compiler.CompilerPaths
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jps.incremental.scala.remote.{CommandIds, SerializablePath, SourceScope}
import org.jetbrains.jps.incremental.scala.{Client, DelegateClient}
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.data.{CompilerData, CompilerJarsFactory, DocumentCompilationArguments, DocumentCompilationData, IncrementalityType}
import org.jetbrains.plugins.scala.compiler.{CompilerManagerUtil, RemoteServerConnectorBase, RemoteServerRunner}
import org.jetbrains.plugins.scala.editor.DocumentExt
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.{ModuleExt, ScalaLanguageLevel, VirtualFileExt}
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings
import org.jetbrains.plugins.scala.util.ScalaPluginJars

import java.nio.file.{Files, Path}

@Service(Array(Service.Level.PROJECT))
private final class DocumentCompiler(project: Project) {

  private def workingDirectory(): Path = {
    val compilerDir =
      Option(CompilerManagerUtil.javacCompilerWorkingDir(project))
        .getOrElse {
          // This shouldn't happen, as the implementation of `CompilerManagerImpl#getJavacCompilerWorkingDir`
          // does not return a nullable file, but just in case, this is the same directory.
          BuildManager.getInstance().getProjectSystemDir(project)
        }.resolve("document-compiler")
    if (!compilerDir.exists) {
      Files.createDirectories(compilerDir)
    }
    compilerDir
  }

  def compile(
    module: Module,
    sourceScope: SourceScope,
    document: Document,
    virtualFile: VirtualFile,
    client: Client
  ): Unit = {
    val useInMemoryFile = Registry.is("scala.compiler.highlighting.document.use.in.memory.file")
    val originalSourceFile = virtualFile.toPath
    val sourceContent = document.textWithConvertedSeparators(virtualFile)

    if (useInMemoryFile) {
      compileInMemoryFile(
        sourcePath = originalSourceFile,
        sourceContent = sourceContent,
        module = module,
        sourceScope = sourceScope,
        client = client
      )
    } else {
      compilePhysicalFile(
        originalSourceFile = originalSourceFile,
        content = sourceContent,
        module = module,
        sourceScope = sourceScope,
        client = client
      )
    }
  }

  private def compilePhysicalFile(originalSourceFile: Path,
                                  content: String,
                                  module: Module,
                                  sourceScope: SourceScope,
                                  client: Client): Unit = {
    val tempSourceFile = workingDirectory().resolve("tempSourceFile")
    val connector =
      try {
        Files.writeString(tempSourceFile, content)
        new PhysicalFileConnector(tempSourceFile, module, sourceScope)
      } catch {
        case t: Throwable =>
          // Remove the temporary source file if creating the connector failed.
          NioFiles.deleteRecursively(tempSourceFile)
          throw t
      }

    try connector.compile(originalSourceFile, client)
    finally {
      if (connector.requiresCleanup) {
        cleanWorkingDirectory()
      } else {
        NioFiles.deleteRecursively(tempSourceFile)
      }
    }
  }

  private def compileInMemoryFile(sourcePath: Path,
                                  sourceContent: String,
                                  module: Module,
                                  sourceScope: SourceScope,
                                  client: Client): Unit = {
    val connector = new InMemoryFileConnector(sourcePath, sourceContent, module, sourceScope)
    try connector.compile(client)
    finally {
      if (connector.requiresCleanup) {
        cleanWorkingDirectory()
      }
    }
  }

  private def cleanWorkingDirectory(): Unit = {
    val files = workingDirectory().children()
    files.foreach(NioFiles.deleteRecursively)
  }

  private final class PhysicalFileConnector(tempSourceFile: Path, module: Module, sourceScope: SourceScope)
    extends AbstractRemoteServerConnector(Some(Seq(tempSourceFile)), module, sourceScope) {

    def compile(originalSourceFile: Path, client: Client): Unit = {
      val fixedClient = new DelegateClient(client) {
        override def message(msg: Client.ClientMsg): Unit = {
          /**
           * NOTE: some compiler errors can be with empty `source`<br>
           * Example: `bad option '-g:vars' was ignored`<br>
           * We do not want to loose such message.
           * We rely that they will be reported in the beginning of the file<br>
           * see [[org.jetbrains.plugins.scala.compiler.highlighting.ExternalHighlightersService.toHighlightInfo]]
           * (we assume that `from` and `to` are also empty for such files)
           */
          val fixedSource = Some(SerializablePath(originalSourceFile)) //msg.source.map(_ => originalSourceFile)
          val fixedMsg = msg.copy(source = fixedSource)
          client.message(fixedMsg)
        }

        override def compilationEnd(sources: Set[Path]): Unit = {
          val fixedSources = Set(originalSourceFile)
          client.compilationEnd(fixedSources)
        }
      }
      new RemoteServerRunner()
        .buildProcess(CommandIds.Compile, arguments.asStrings, fixedClient)
        .runSync()
    }
  }

  private final class InMemoryFileConnector(sourcePath: Path, sourceContent: String, module: Module, sourceScope: SourceScope)
    extends AbstractRemoteServerConnector(None, module, sourceScope) {

    def compile(client: Client): Unit = {
      val arguments = DocumentCompilationArguments(
        sbtData = sbtData,
        compilerData = CompilerData(
          compilerJars = CompilerJarsFactory.fromFiles(compilerClasspath, module.customScalaCompilerBridgeJar).toOption,
          javaHome = Some(findJdk),
          incrementalType = IncrementalityType.IDEA
        ),
        compilationData = DocumentCompilationData(
          sourcePath = sourcePath,
          sourceContent = sourceContent,
          output = workingDirectory(),
          classpath = runtimeClasspath,
          scalacOptions = scalaParameters
        )
      )

      new RemoteServerRunner()
        .buildProcess(CommandIds.CompileDocument, DocumentCompilationArguments.serialize(arguments), client)
        .runSync()
    }
  }

  private abstract class AbstractRemoteServerConnector(filesToCompile: Option[Seq[Path]], module: Module, sourceScope: SourceScope)
    extends RemoteServerConnectorBase(module, filesToCompile, workingDirectory()) {

    var requiresCleanup: Boolean = false

    override protected def scalaParameters: Seq[String] = {
      var scalacOptions = CompilerOptions.scalacOptions(module)
      // The setting is per-project rather than per-module
      if (ScalaProjectSettings.getInstance(project).isUseCompilerTypes) {
        val compilerPluginJar: Option[Path] = module.scalaLanguageLevel.flatMap {
          case ScalaLanguageLevel.Scala_2_12 if module.scalaMinorVersion.exists(_ >= ScalaVersion.fromString("2.12.13").get) => Some(ScalaPluginJars.compilerPluginJar_2_12)
          case ScalaLanguageLevel.Scala_2_13 if module.scalaMinorVersion.exists(_ >= ScalaVersion.fromString("2.13.1").get) => Some(ScalaPluginJars.compilerPluginJar_2_13)
          case level if level >= ScalaLanguageLevel.Scala_3_3 => Some(ScalaPluginJars.compilerPluginJar_3_3)
          case _ => None
        }
        compilerPluginJar.foreach { jar =>
          scalacOptions :++= Seq("-Xplugin:" + jar.toAbsolutePath.toString, "-Xplugin-require:intellij-compiler-plugin")
          if (module.scalaLanguageLevel.contains(ScalaLanguageLevel.Scala_2_12)) {
            scalacOptions :+= "-Yrangepos"
          }
        }
      }
      if (!CompilerOptions.containsStopAfter(scalacOptions)) {
        val stopAfter = module.scalaLanguageLevel match {
          case Some(ScalaLanguageLevel.Scala_2_10) => Some("-Ystop-after:dce")
          case Some(ScalaLanguageLevel.Scala_2_11) |
               Some(ScalaLanguageLevel.Scala_2_12) |
               Some(ScalaLanguageLevel.Scala_2_13) => Some("-Ystop-after:delambdafy")
          case Some(languageLevel) if languageLevel.isScala3 => Some("-Ystop-after:repeatableAnnotations")
          case _ =>
            // .class files will be produced, they should be cleaned up
            requiresCleanup = true
            None
        }
        scalacOptions ++= stopAfter
      } else {
        // .class/tasty files might be produced, they should be cleaned up if they exist
        requiresCleanup = true
      }
      if (module.scalaLanguageLevel.exists(_ >= ScalaLanguageLevel.Scala_3_3)) {
        if (!CompilerOptions.containsUnusedImports(scalacOptions)) {
          scalacOptions = scalacOptions :+ "-Wunused:imports"
        }
      }
      scalacOptions
    }

    override protected def assemblyRuntimeClasspath(): Seq[Path] = {
      val fromSuper = super.assemblyRuntimeClasspath()
      val forTestClasses = sourceScope match {
        case SourceScope.Production => false
        case SourceScope.Test => true
      }

      val outputDir =
        Option(CompilerPaths.getModuleOutputPath(module, forTestClasses))
          .map(Path.of(_))
      (fromSuper ++ outputDir).distinct
    }
  }
}

private object DocumentCompiler {
  def get(project: Project): DocumentCompiler =
    project.getService(classOf[DocumentCompiler])
}
