package org.jetbrains.jps
package incremental
package scala
package data

import _root_.java.io.File

import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.java.JpsJavaSdkType
import org.jetbrains.jps.model.library.JpsLibrary
import org.jetbrains.jps.model.module.JpsModule

/**
 * @author Pavel Fatin
 */
case class CompilerData(compilerJars: Option[CompilerJars],
                        javaHome: Option[File],
                        incrementalType: model.IncrementalityType)

object CompilerData extends CompilerDataFactory {

  private val JarExtension = ".jar"

  private case class JarFileWithName(file: File, name: String)

  override def from(context: CompileContext, chunk: ModuleChunk): Either[String, CompilerData] = {
    val module = chunk.representativeTarget.getModule

    val compilerJars = SettingsManager.getScalaSdk(module)
      .fold(Right(None): Either[String, Option[CompilerJars]]) { sdk =>
        compilerJarsInSdk(sdk).fold(
          {
            case (kind, files) =>
              val (messagePrefix, messageInfix) = if (files.isEmpty)
                ("No", "")
              else
                ("Multiple", s" files (${files.map(_.name).mkString(", ")})")

              Left(s"$messagePrefix '$kind*$JarExtension'$messageInfix in Scala compiler classpath in Scala SDK ${sdk.getName}")
          }, {
            case jars@CompilerJars(library, compiler, extra) =>
              val absentJars = for {
                file <- library +: compiler +: extra
                if !file.exists
              } yield file.getPath

              Either.cond(absentJars.isEmpty,
                Some(jars),
                s"Scala compiler JARs not found (module '${module.getName}'): ${absentJars.mkString(", ")}"
              )
          }
        )
      }

    val descriptor = context.getProjectDescriptor
    for {
      jars <- compilerJars
      home <- javaHome(descriptor.getModel, module, ScalaBuilder.isCompileServerEnabled(context))
    } yield CompilerData(
      jars,
      home,
      SettingsManager.getProjectSettings(descriptor.getProject).getIncrementalityType
    )
  }

  private def javaHome(model: JpsModel,
                       module: JpsModule,
                       isCompileServerEnabled: Boolean): Either[String, Option[File]] = {
    Option(module.getSdk(JpsJavaSdkType.INSTANCE))
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

        if (jvmSdk.map(_.getProperties).contains(moduleJdk)) Right(None)
        else {
          val directory = new File(moduleJdk.getHomePath)
          Either.cond(directory.exists, Some(directory), "JDK home directory does not exists: " + directory)
        }
      }
  }

  def hasDotty(modules: Set[JpsModule]): Boolean = modules.exists {
    compilerJarsIn(_).exists {
      case CompilerJars(_, _, extra) => hasDotty(extra)
    }
  }

  def needBootCp(modules: Set[JpsModule]): Boolean = modules.exists {
    compilerJarsIn(_).forall {
      case CompilerJars(_, compiler, extra) => hasDotty(extra) || versionIn(compiler, "2.8", "2.9")
    }
  }

  private def compilerJarsIn(module: JpsModule) =
    SettingsManager.getScalaSdk(module)
      .flatMap(compilerJarsInSdk(_).toOption)

  private def compilerJarsInSdk(sdk: JpsLibrary) = {
    val files = sdk.getProperties match {
      case settings: model.LibrarySettings =>
        for {
          file <- settings.getCompilerClasspath.toSeq
          name = file.getName
          if name.endsWith(JarExtension)
        } yield JarFileWithName(file, name)
      case _ => Seq.empty
    }

    for {
      library <- find(files, "scala-library")
      compiler <- find(files, "scala-compiler")

      extra = files.filter {
        case `library` | `compiler` => false
        case _ => true
      }

      _ <- if (versionIn(compiler.file, "2.10"))
        find(extra, "scala-reflect")
      else
        Right(null)
    } yield CompilerJars(
      library.file,
      compiler.file,
      extra.map(_.file)
    )
  }

  private def find(files: Seq[JarFileWithName], kind: String): Either[(String, Seq[JarFileWithName]), JarFileWithName] =
    files.filter(_.name.startsWith(kind)) match {
      case Seq(file) => Right(file)
      case duplicates => Left(kind, duplicates)
    }

  // TODO implement a better version comparison
  private def versionIn(compiler: File,
                        versions: String*) = readProperty(
    compiler,
    "compiler.properties",
    "version.number"
  ).exists { version =>
    versions.exists(version.startsWith)
  }

  private def hasDotty(extra: Seq[File]) = extra.exists(_.getName.startsWith("dotty"))
}
