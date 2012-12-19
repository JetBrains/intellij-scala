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

      files.find(_.getName == "scala-library.jar")
              .toRight("No 'scala-library.jar' in Scala compiler library in " + module.getName)
              .flatMap { libraryJar =>

        files.find(_.getName == "scala-compiler.jar")
                .toRight("No 'scala-compiler.jar' in Scala compiler library in " + module.getName)
                .map { compilerJar =>

          val extraJars = files.filterNot(file => file == libraryJar || file == compilerJar)

          CompilerJars(libraryJar, compilerJar, extraJars)
        }
      }
    }
  }

  private def compilerLibraryIn(module: JpsModule, model: JpsModel): Either[String, JpsLibrary] = {
    val scalaFacet = Option(SettingsManager.getFacetSettings(module))
            .toRight("No Scala facet in module " + module.getName)

    scalaFacet.flatMap { facet =>
      val libraryLevel = Option(facet.getCompilerLibraryLevel)
              .toRight("No compiler library level set in module " + module.getName)

      libraryLevel.flatMap { level =>
        val libraries = level match {
          case Global => model.getGlobal.getLibraryCollection
          case Project => model.getProject.getLibraryCollection
          case Module => module.getLibraryCollection
        }

        val libraryName = Option(facet.getCompilerLibraryName)
                .toRight("No compiler library name set in module " + module.getName)

        libraryName.flatMap { name =>
          Option(libraries.findLibrary(name)).toRight(String.format(
            "Сompiler library for module %s not found: %s / %s ", module.getName, libraryLevel, libraryName))
        }
      }
    }
  }
}
