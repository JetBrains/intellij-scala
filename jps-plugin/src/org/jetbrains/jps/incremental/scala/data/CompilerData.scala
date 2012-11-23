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
case class CompilerData(libraryJar: File,
                        compilerJar: File,
                        extraJars: Seq[File],
                        javaHome: File)

object CompilerData {
  def from(context: CompileContext, chunk: ModuleChunk): Either[String, CompilerData] = {
    val model = context.getProjectDescriptor.getModel
    val target = chunk.representativeTarget
    val module = target.getModule

    compilerLibraryIn(module, model).flatMap { compilerLibrary =>
      val files = compilerLibrary.getFiles(JpsOrderRootType.COMPILED).asScala

      files.find(_.getName == "scala-library.jar")
              .toRight("No 'scala-library.jar' in Scala compiler library in " + target.getModuleName)
              .flatMap { libraryJar =>

        files.find(_.getName == "scala-compiler.jar")
                .toRight("No 'scala-compiler.jar' in Scala compiler library in " + target.getModuleName)
                .flatMap { compilerJar =>

          Option(module.getSdk(JpsJavaSdkType.INSTANCE))
                  .toRight("No JDK in module " + module.getName)
                  .flatMap { jdk =>

            val javaHomeDirectory = new File(jdk.getHomePath)

            Either.cond(javaHomeDirectory.exists, javaHomeDirectory,
              "JDK home directory does not exists: " + javaHomeDirectory).map { javaHome =>

              val extraJars = files.filterNot(file => file == libraryJar || file == compilerJar)

              new CompilerData(libraryJar, compilerJar, extraJars, javaHome)
            }
          }
        }
      }
    }
  }

  private def compilerLibraryIn(module: JpsModule, model: JpsModel): Either[String, JpsLibrary] = {
    val scalaFacet = Option(SettingsManager.getFacetSettings(module))
            .toRight("No Scala facet in module " + module.getName)

    scalaFacet.flatMap { facet =>
      val project = model.getProject
      val fsc = facet.isFscEnabled
      val settings = if (fsc) SettingsManager.getProjectSettings(project) else facet

      val libraryLevel = Option(settings.getCompilerLibraryLevel).toRight {
        if (fsc) "No FSC compiler library level set in project " + project.getName
        else "No compiler library level set in module " + module.getName
      }

      libraryLevel.flatMap { level =>
        val libraries = level match {
          case Global => model.getGlobal.getLibraryCollection
          case Project => model.getProject.getLibraryCollection
          case Module => module.getLibraryCollection
        }

        val libraryName = Option(settings.getCompilerLibraryName).toRight {
          if (fsc) "No FSC compiler library name set in project " + project.getName
          else "No compiler library name set in module " + module.getName
        }

        libraryName.flatMap { name =>
          Option(libraries.findLibrary(name)).toRight {
            if (fsc) String.format("FSC compiler library in project %s not found: %s / %s ", project.getName, libraryLevel, libraryName)
            else String.format("Сompiler library for module %s not found: %s / %s ", module.getName, libraryLevel, libraryName)
          }
        }
      }
    }
  }
}
