package org.jetbrains.jps.incremental.scala.data

import _root_.java.io.File

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.scala.data.CompilerJarsFactory.CompilerJarsResolveError
import org.jetbrains.jps.incremental.scala.{ScalaBuilder, SettingsManager}
import org.jetbrains.jps.incremental.scala.model.{IncrementalityType, LibrarySettings}
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule

/**
 * @author Pavel Fatin
 */
case class CompilerData(compilerJars: Option[CompilerJars],
                        javaHome: Option[File],
                        incrementalType: IncrementalityType)

object CompilerData extends CompilerDataFactory {

  override def from(context: CompileContext, chunk: ModuleChunk): Either[String, CompilerData] = {
    val module = chunk.representativeTarget.getModule

    val scalaSdkOpt = SettingsManager.getScalaSdk(module)

    val compilerJars: Either[String, Option[CompilerJars]] =
      scalaSdkOpt
        .map(extractCompilerJars(_, module).map(Some(_)))
        .getOrElse(Right(None))

    val descriptor = context.getProjectDescriptor
    for {
      jars <- compilerJars
      home <- javaHome(descriptor.getModel, module, ScalaBuilder.isCompileServerEnabled(context))
    } yield {
      val incrementality = SettingsManager.getProjectSettings(descriptor.getProject).getIncrementalityType
      CompilerData(jars, home, incrementality)
    }
  }

  private def extractCompilerJars(scalaSdk: JpsLibrary, module: JpsModule): Either[String, CompilerJars] =
    compilerJarsInSdk(scalaSdk)
      .flatMap(validateAllFilesExist)
      .left.map(toErrorMessage(_, scalaSdk, module))

  private def validateAllFilesExist(jars: CompilerJars): Either[CompilerJarsResolveError.FilesDoNotExist, CompilerJars] = {
    val absentJars = jars.allJars.filterNot(_.exists)
    Either.cond(
      absentJars.isEmpty,
      jars,
      CompilerJarsResolveError.FilesDoNotExist(absentJars)
    )
  }

  private def javaHome(model: JpsModel,
                       module: JpsModule,
                       isCompileServerEnabled: Boolean): Either[String, Option[File]] = {
    val jdkOpt = Option(module.getSdk(JpsJavaSdkType.INSTANCE))
    jdkOpt
      .toRight("No JDK in module " + module.getName)
      .flatMap { moduleJdk =>

        val jvmSdk = if (isCompileServerEnabled) {
          val global = model.getGlobal
          Option(SettingsManager.getGlobalSettings(global).getCompileServerSdk).flatMap { sdkName =>
            import collection.JavaConverters._
            global.getLibraryCollection
              .getLibraries(JpsJavaSdkType.INSTANCE)
              .asScala
              .find(_.getName == sdkName)
          }
        } else {
          Option(model.getProject.getSdkReferencesTable.getSdkReference(JpsJavaSdkType.INSTANCE))
            .flatMap(references => Option(references.resolve))
        }

        if (jvmSdk.map(_.getProperties).contains(moduleJdk)) {
          Right(None)
        } else {
          val directory = new File(moduleJdk.getHomePath)
          Either.cond(directory.exists, Some(directory), "JDK home directory does not exists: " + directory)
        }
      }
  }

  def hasDotty(modules: Set[JpsModule]): Boolean = modules.exists {
    compilerJarsIn(_).exists(_.hasDotty)
  }

  def bootCpArgs(modules: Set[JpsModule]): Seq[String] =
    if (hasDotty(modules)) {
      Seq("-javabootclasspath", File.pathSeparator)
    } else {
      val needBootCp = modules.exists {
        compilerJarsIn(_).forall {
          case CompilerJars(_, compiler, _) => CompilerJars.versionIn(compiler, "2.8", "2.9")
        }
      }
      if (needBootCp)
        Seq("-nobootcp", "-javabootclasspath", File.pathSeparator)
      else
        Seq.empty
    }

  private def compilerJarsIn(module: JpsModule): Option[CompilerJars] =
    SettingsManager.getScalaSdk(module)
      .flatMap(compilerJarsInSdk(_).toOption)

  private def compilerJarsInSdk(sdk: JpsLibrary): Either[CompilerJarsResolveError, CompilerJars] = {
    val files = compilerClasspath(sdk)
    val jarFiles = CompilerJars.collectJars(files)
    CompilerJars.fromFiles(jarFiles)
  }

  private def compilerClasspath(sdk: JpsLibrary): Seq[File] =
    sdk.getProperties match {
      case settings: LibrarySettings => settings.getCompilerClasspath.toSeq
      case _                         => Seq.empty
    }

  private def toErrorMessage(error: CompilerJarsResolveError, scalaSdk: JpsLibrary, module: JpsModule): String = {
    import CompilerJarsResolveError._
    import CompilerJars.JarFileWithName

    def inScalaCompiler = s"in Scala compiler classpath in Scala SDK ${scalaSdk.getName}"
    def filesNames(files: Seq[JarFileWithName]) = files.map(_.name).mkString(", ")
    def filePaths(absentJars: Seq[File]) = absentJars.map(_.getPath).mkString(", ")

    import CompilerJars.JarExtension

    error match {
      case NotFound(kind)               => s"No '$kind*$JarExtension' $inScalaCompiler"
      case DuplicatesFound(kind, files) => s"Multiple '$kind*$JarExtension' files (${filesNames(files)}) $inScalaCompiler"
      case FilesDoNotExist(absentJars)  => s"Scala compiler JARs not found (module '${module.getName}'): ${filePaths(absentJars)}"
    }
  }
}

