package org.jetbrains.plugins.scala.tasty.reader

import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.file.{Files, Paths}
import java.util.jar.JarInputStream
import scala.util.chaining.scalaUtilChainingOps

object Main {
  enum Mode { case Parse, Test, Benchmark }

  private val mode = Mode.Test

  private val Home: String = System.getProperty("user.home")

  private val Repository = Home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/"
  private val OutputDir = Home + "/IdeaProjects/scala-plugin-for-ultimate/community/scala/tasty-reader/target/comparison"

  // scalaVersion := "3.1.1",
  // libraryDependencies += "dev.zio" %% "zio" % "1.0.12",
  // libraryDependencies += "dev.zio" %% "zio-streams" % "1.0.12",
  // libraryDependencies += "org.typelevel" %% "cats-core" % "2.7.0",
  // libraryDependencies += "org.typelevel" %% "cats-effect" % "3.3.8",
  // libraryDependencies += "org.scala-lang" %% "scala3-compiler" % "3.1.1",
  private val Libraries = Seq(
    "org/scala-lang/scala3-library_3/3.1.1/scala3-library_3-3.1.1.jar",
    "org/scalatest/scalatest-core_3/3.2.11/scalatest-core_3-3.2.11.jar",
    "org/scalatest/scalatest-funspec_3/3.2.11/scalatest-funspec_3-3.2.11.jar",
    "org/scalatest/scalatest-funsuite_3/3.2.11/scalatest-funsuite_3-3.2.11.jar",
    "dev/zio/zio_3/1.0.12/zio_3-1.0.12.jar",
    "dev/zio/zio-streams_3/1.0.12/zio-streams_3-1.0.12.jar",
    "io/getquill/quill-sql_3/3.7.2.Beta1.4/quill-sql_3-3.7.2.Beta1.4.jar",
    "org/typelevel/cats-core_3/2.7.0/cats-core_3-2.7.0.jar",
    "org/typelevel/cats-effect_3/3.3.8/cats-effect_3-3.3.8.jar",
    "org/scala-lang/scala3-compiler_3/3.1.1/scala3-compiler_3-3.1.1.jar",
  )

  def main(args: Array[String]): Unit = {
    assert(new File(OutputDir).getParentFile.exists)

    val start = System.currentTimeMillis()

    Libraries.foreach { binaries =>
      println("Parsing TASTy:\t\t" + binaries)
      new JarInputStream(new BufferedInputStream(new FileInputStream(Repository + "/" + binaries))).pipe { in =>
        Iterator.continually(in.getNextEntry).takeWhile(_ != null).filter(_.getName.endsWith(".tasty")).foreach { entry =>
          val file = new File(s"$OutputDir/${entry.getName}")
          val tree = TreeReader.treeFrom(in.readAllBytes())
          val path = Paths.get(file.getPath.replaceFirst("\\.tasty", ".scala"))
          val treePrinter = new TreePrinter()
          mode match {
            case Mode.Parse =>
              file.getParentFile.mkdirs()
              //Files.write(Paths.get(file.getPath.replaceFirst("\\.tasty", ".tree")), tree.toString.getBytes)
              Files.write(path, treePrinter.fileAndTextOf(tree)._2.getBytes)
            case Mode.Test =>
              val expected = new String(Files.readAllBytes(path))
              val (_, actual) = treePrinter.fileAndTextOf(tree)
              if (expected != actual) {
                System.err.println(path)
                System.err.println("Expected:\n" + expected)
                System.err.println("Actual:\n" + actual)
                System.exit(-1)
              }
            case Mode.Benchmark =>
              treePrinter.fileAndTextOf(tree)
          }
        }
      }

      val sources = binaries.replaceFirst("\\.jar", "-sources.jar")

      if (mode == Mode.Parse) {
        println("Extracting sources:\t" + sources)
        new JarInputStream(new BufferedInputStream(new FileInputStream(Repository + "/" + sources))).pipe { in =>
          Iterator.continually(in.getNextEntry).takeWhile(_ != null).filter(_.getName.endsWith(".scala")).foreach { entry =>
            val file = new File(s"$OutputDir/${entry.getName}")
            file.getParentFile.mkdirs()
            val s = new String(in.readAllBytes) // TODO store pre-compiled regex
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
    }

    if (mode == Mode.Benchmark) {
      printf("Elapsed: %.2f s.\n", (System.currentTimeMillis() - start) / 1000.0D)
    } else {
      println("Done:\t\t\t\t" + OutputDir)
    }
  }
}
