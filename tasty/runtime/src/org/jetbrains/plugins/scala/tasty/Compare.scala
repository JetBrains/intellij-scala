package org.jetbrains.plugins.scala.tasty

import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.file.{Files, Paths}
import java.util.jar.JarInputStream
import scala.util.chaining.scalaUtilChainingOps

object Compare {
  private val Home: String = System.getProperty("user.home")

  private val Repository = Home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/"
  private val OutputDir = Home + "/IdeaProjects/scala-plugin-for-ultimate/community/tasty/runtime/target/comparison"

  // scalaVersion := "3.0.0",
  // libraryDependencies += "dev.zio" %% "zio" % "1.0.9",
  // libraryDependencies += "dev.zio" %% "zio-streams" % "1.0.9",
  // libraryDependencies += "org.typelevel" %% "cats-core" % "2.6.1",
  // libraryDependencies += "org.typelevel" %% "cats-effect" % "3.1.1",
  // libraryDependencies += "org.scala-lang" %% "scala3-compiler" % "3.0.0",
  private val Libraries = Seq(
    ("scala3-library", "org/scala-lang/scala3-library_3/3.0.0/scala3-library_3-3.0.0.jar"),
    ("scalatest-core", "org/scalatest/scalatest-core_3/3.2.9/scalatest-core_3-3.2.9.jar"),
    ("scalatest-funspec", "org/scalatest/scalatest-funspec_3/3.2.9/scalatest-funspec_3-3.2.9.jar"),
    ("scalatest-funspec", "org/scalatest/scalatest-funsuite_3/3.2.9/scalatest-funsuite_3-3.2.9.jar"),
    ("zio", "dev/zio/zio_3/1.0.9/zio_3-1.0.9.jar"),
    ("zio-streams", "dev/zio/zio-streams_3/1.0.9/zio-streams_3-1.0.9.jar"),
    ("cats-core", "org/typelevel/cats-core_3/2.6.1/cats-core_3-2.6.1.jar"),
    ("cats-effect", "org/typelevel/cats-effect_3/3.1.1/cats-effect_3-3.1.1.jar"),
    ("scala3-compiler", "org/scala-lang/scala3-compiler_3/3.0.0/scala3-compiler_3-3.0.0.jar"),
  )

  def main(args: Array[String]): Unit = {
    assert(new File(OutputDir).getParentFile.exists)

    Libraries.foreach {case (name, binaries) =>
      println("Parsing TASTy:\t\t" + binaries)
      new JarInputStream(new BufferedInputStream(new FileInputStream(Repository + "/" + binaries))).pipe { in =>
        Iterator.continually(in.getNextEntry).takeWhile(_ != null).filter(_.getName.endsWith(".tasty")).foreach { entry =>
          val file = new File(s"$OutputDir/$name/${entry.getName}")
          file.getParentFile.mkdirs()
          val tree = TreeReader.treeFrom(in.readAllBytes())
//          Files.write(Paths.get(file.getPath.replaceFirst("\\.tasty", ".tree")), tree.toString.getBytes)
          Files.write(Paths.get(file.getPath.replaceFirst("\\.tasty", ".scala")), TreePrinter.textOf(tree).getBytes)
        }
      }

      val sources = binaries.replaceFirst("\\.jar", "-sources.jar")

      println("Extracting sources:\t" + sources)
      new JarInputStream(new BufferedInputStream(new FileInputStream(Repository + "/" + sources))).pipe { in =>
        Iterator.continually(in.getNextEntry).takeWhile(_ != null).filter(_.getName.endsWith(".scala")).foreach { entry =>
          val file = new File(s"$OutputDir/$name/${entry.getName}")
          file.getParentFile.mkdirs()
          val s = new String(in.readAllBytes)
            .replaceAll(raw"(?m)^\s*import.*?$$", "") // Import
            .replaceAll(raw"\s*//.*?\n", "") // Line comment
            .replaceAll(raw"(?s)/\*.*?\*/", "") // Block comment
            .replaceAll(raw"(?m)^\s+$$", "") // Whitespaces on empty line
            .replaceAll(raw"\n{3,}", "\n\n") // Multiple empty lines
            .replaceAll(raw"\{\n\n", "{\n") // Empty line after {
            .replaceAll(raw"\n\n}", "\n}") // Empty line before }
            .trim

          Files.write(Paths.get(file.getPath.replaceFirst("\\.scala", "-source.scala")), s.getBytes)
        }
      }
    }

    println("Done:\t\t\t\t" + OutputDir)
  }
}
