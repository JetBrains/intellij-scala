package org.jetbrains.jps.incremental.scala
package data

import java.io.File
import org.jetbrains.jps.incremental.scala.model.LibraryLevel._
import org.jetbrains.jps.model.library.{JpsOrderRootType, JpsLibrary}
import org.jetbrains.jps.incremental.scala.SettingsManager
import org.jetbrains.jps.model.JpsModel
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.jps.incremental.CompileContext
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.model.java.JpsJavaSdkType
import collection.JavaConverters._

/**
 * @author Pavel Fatin
 */
case class CompilerData(compilerJars: Option[CompilerJars], javaHome: Option[File])

object CompilerData {
  def from(context: CompileContext, chunk: ModuleChunk): Either[String, CompilerData] = {
    val model = context.getProjectDescriptor.getModel
    val target = chunk.representativeTarget
    val module = target.getModule

    val compilerJars =
      if (SettingsManager.getFacetSettings(module) == null) Right(None)
      else compilerJarsIn(module, model).map(Some(_))

    compilerJars.flatMap { jars =>

      Option(module.getSdk(JpsJavaSdkType.INSTANCE))
              .toRight("No JDK in module " + module.getName)
              .flatMap { jdk =>

        val projectJdk = Option(model.getProject.getSdkReferencesTable.getSdkReference(JpsJavaSdkType.INSTANCE))
                .flatMap(references => Option(references.resolve))
                .map(_.getProperties)

        val javaHome = if (projectJdk.exists(_ == jdk)) Right(None) else {
          val directory = new File(jdk.getHomePath)
          Either.cond(directory.exists, Some(directory), "JDK home directory does not exists: " + directory)
        }

        javaHome.map(CompilerData(jars, _))
      }
    }
  }

  private def compilerJarsIn(module: JpsModule, model: JpsModel): Either[String, CompilerJars] = {
    compilerLibraryIn(module, model).flatMap { compilerLibrary =>

      val files = compilerLibrary.getFiles(JpsOrderRootType.COMPILED).asScala

      val library = find(files, "scala-library", ".jar") match {
        case Left(error) => Left(error + " in Scala compiler library in " + module.getName)
        case right => right
      }

      library.flatMap { libraryJar =>
        val compiler = find(files, "scala-compiler", ".jar") match {
          case Left(error) => Left(error + " in Scala compiler library in " + module.getName)
          case right => right
        }

        compiler.map { compilerJar =>
          val extraJars = files.filterNot(file => file == libraryJar || file == compilerJar)
          CompilerJars(libraryJar, compilerJar, extraJars)
        }
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

  private def compilerLibraryIn(module: JpsModule, model: JpsModel): Either[String, JpsLibrary] = {
    val scalaFacet = Option(SettingsManager.getFacetSettings(module))
            .toRight("No Scala facet in module " + module.getName)

    scalaFacet.flatMap { facet =>
      val libraryLevel = Option(facet.getCompilerLibraryLevel)
              .toRight("No compiler library set in module " + module.getName)

      libraryLevel.flatMap { level =>
        val libraries = level match {
          case Global => model.getGlobal.getLibraryCollection
          case Project => model.getProject.getLibraryCollection
          case Module => module.getLibraryCollection
        }

        val libraryName = Option(facet.getCompilerLibraryName)
                .toRight("No compiler library set in module " + module.getName)

        libraryName.flatMap { name =>
          Option(libraries.findLibrary(name)).toRight(String.format(
            "Сompiler library for module %s not found: %s / %s ", module.getName, libraryLevel, libraryName))
        }
      }
    }
  }
}
