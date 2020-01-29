package org.jetbrains.jps.incremental.scala.data

import _root_.java.io.File

import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.incremental.scala.{ScalaBuilder, SettingsManager}
import org.jetbrains.jps.incremental.scala.model.{IncrementalityType, LibrarySettings}
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.incremental.scala.readProperty

/**
 * @author Pavel Fatin
 */
case class CompilerData(compilerJars: Option[CompilerJars],
                        javaHome: Option[File],
                        incrementalType: IncrementalityType)

object CompilerData extends CompilerDataFactory {

  private val JarExtension = ".jar"

  private case class JarFileWithName(file: File, name: String)

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

  private def validateAllFilesExist(jars: CompilerJars): Either[CompilerJarsError.FilesDoNotExist, CompilerJars] = {
    val absentJars = jars.allJars.filterNot(_.exists)
    Either.cond(
      absentJars.isEmpty,
      jars,
      CompilerJarsError.FilesDoNotExist(absentJars)
    )
  }

  private def toErrorMessage(error: CompilerJarsResolveError, scalaSdk: JpsLibrary, module: JpsModule): String = {
    import CompilerJarsError._

    def inScalaCompiler = s"in Scala compiler classpath in Scala SDK ${scalaSdk.getName}"
    def filesNames(files: Seq[JarFileWithName]) = files.map(_.name).mkString(", ")
    def filePaths(absentJars: Seq[File]) = absentJars.map(_.getPath).mkString(", ")

    error match {
      case NotFound(kind)               => s"No '$kind*$JarExtension' $inScalaCompiler"
      case DuplicatesFound(kind, files) => s"Multiple '$kind*$JarExtension' files (${filesNames(files)}) $inScalaCompiler"
      case FilesDoNotExist(absentJars)  => s"Scala compiler JARs not found (module '${module.getName}'): ${filePaths(absentJars)}"
    }
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
          case CompilerJars(_, compiler, _) => versionIn(compiler, "2.8", "2.9")
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
    val files = sdk.getProperties match {
      case settings: LibrarySettings =>
        for {
          file <- settings.getCompilerClasspath.toSeq
          name = file.getName
          if name.endsWith(JarExtension)
        } yield JarFileWithName(file, name)
      case _ =>
        Seq.empty
    }

    val compilerPrefix = if (CompilerJars.hasDotty(files.map(_.file))) "dotty" else "scala"
    for {
      library <- find(files, "scala-library")
      compiler <- find(files, s"$compilerPrefix-compiler")

      extra = files.filter {
        case `library` | `compiler` => false
        case _ => true
      }

      _ <- if (versionIn(compiler.file, "2.10")) find(extra, "scala-reflect") else Right(null)
    } yield CompilerJars(
      library.file,
      compiler.file,
      extra.map(_.file)
    )
  }

  private def find(files: Seq[JarFileWithName], kind: String): Either[CompilerJarsResolveError, JarFileWithName] = {
    val filesOfKind = files.filter(_.name.startsWith(kind)).distinct
    filesOfKind match {
      case Seq(file)  => Right(file)
      case Seq()      => Left(CompilerJarsError.NotFound(kind))
      case duplicates => Left(CompilerJarsError.DuplicatesFound(kind, duplicates))
    }
  }

  private sealed trait CompilerJarsResolveError
  private object CompilerJarsError {
    case class NotFound(kind: String) extends CompilerJarsResolveError
    case class DuplicatesFound(kind: String, duplicates: Seq[JarFileWithName]) extends CompilerJarsResolveError
    case class FilesDoNotExist(files: Seq[File]) extends CompilerJarsResolveError
  }

  // TODO implement a better version comparison
  private def versionIn(compiler: File,
                        versions: String*) =
    compilerVersion(compiler).exists { version => versions.exists(version.startsWith) }

  private def compilerVersion(compiler: File): Option[String] = readProperty(
    compiler,
    "compiler.properties",
    "version.number"
  )
}
