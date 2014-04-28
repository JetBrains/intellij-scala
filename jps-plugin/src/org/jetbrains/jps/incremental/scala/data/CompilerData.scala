package org.jetbrains.jps.incremental.scala
package data

import java.io.File
import org.jetbrains.jps.incremental.scala.SettingsManager
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.model.java.JpsJavaSdkType
import collection.JavaConverters._
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental.scala.model.LibrarySettings
import org.jetbrains.jps.incremental.scala.model.IncrementalityType

/**
 * @author Pavel Fatin
 */
case class CompilerData(compilerJars: Option[CompilerJars], javaHome: Option[File], incrementalType: IncrementalityType)

object CompilerData {
  def from(context: CompileContext, chunk: ModuleChunk): Either[String, CompilerData] = {
    val project = context.getProjectDescriptor
    val model = project.getModel
    val target = chunk.representativeTarget
    val module = target.getModule

    val compilerJars =
      if (SettingsManager.hasScalaSdk(module)) compilerJarsIn(module).map(Some(_))
      else Right(None)

    compilerJars.flatMap { jars =>

      Option(module.getSdk(JpsJavaSdkType.INSTANCE))
              .toRight("No JDK in module " + module.getName)
              .flatMap { moduleJdk =>

        val globalSettings = SettingsManager.getGlobalSettings(model.getGlobal)

        val jvmSdk = if (globalSettings.isCompileServerEnabled && JavaBuilderUtil.CONSTANT_SEARCH_SERVICE.get(context) != null) {
          Option(globalSettings.getCompileServerSdk).flatMap { sdkName =>
            val libraries = model.getGlobal.getLibraryCollection.getLibraries(JpsJavaSdkType.INSTANCE).asScala
            libraries.find(_.getName == sdkName).map(_.getProperties)
          }
        } else {
          Option(model.getProject.getSdkReferencesTable.getSdkReference(JpsJavaSdkType.INSTANCE))
                  .flatMap(references => Option(references.resolve)).map(_.getProperties)
        }

        val javaHome = if (jvmSdk.exists(_ == moduleJdk)) Right(None) else {
          val directory = new File(moduleJdk.getHomePath)
          Either.cond(directory.exists, Some(directory), "JDK home directory does not exists: " + directory)
        }

        val incrementalType = SettingsManager.getProjectSettings(project.getProject).getIncrementalityType

        javaHome.map(CompilerData(jars, _, incrementalType))
      }
    }
  }

  private def compilerJarsIn(module: JpsModule): Either[String, CompilerJars] = {
    val sdk = SettingsManager.getScalaSdk(module)

    val files = sdk.getProperties.asInstanceOf[LibrarySettings].getCompilerClasspath

    val library = find(files, "scala-library", ".jar") match {
      case Left(error) => Left(error + " in Scala compiler classpath in Scala SDK " + sdk.getName)
      case right => right
    }

    library.flatMap { libraryJar =>
      val compiler = find(files, "scala-compiler", ".jar") match {
        case Left(error) => Left(error + " in Scala compiler classpath in Scala SDK " + sdk.getName)
        case right => right
      }

      compiler.flatMap { compilerJar =>
        val extraJars = files.filterNot(file => file == libraryJar || file == compilerJar)

        val reflectJarError = {
          readProperty(compilerJar, "compiler.properties", "version.number").flatMap {
            case version if version.startsWith("2.10") => // TODO implement a better version comparison
              find(extraJars, "scala-reflect", ".jar").left.toOption
                      .map(_ + " in Scala compiler classpath in Scala SDK " + sdk.getName)
            case _ => None
          }
        }

        reflectJarError.toLeft(CompilerJars(libraryJar, compilerJar, extraJars))
      }
    }
  }

  private def find(files: Seq[File], prefix: String, suffix: String): Either[String, File] = {
    files.filter(it => it.getName.startsWith(prefix) && it.getName.endsWith(suffix)) match {
      case Seq() =>
        Left("No '%s*%s'".format(prefix, suffix))
      case Seq(file) =>
        Right(file)
      case Seq(duplicates @ _*) =>
        Left("Multiple '%s*%s' files (%s)".format(prefix, suffix, duplicates.map(_.getName).mkString(", ")))
    }
  }
}
