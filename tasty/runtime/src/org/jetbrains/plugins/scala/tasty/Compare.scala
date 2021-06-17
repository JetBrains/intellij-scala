package org.jetbrains.plugins.scala.tasty

import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.file.{Files, Paths}
import java.util.jar.JarInputStream
import scala.util.chaining.scalaUtilChainingOps

object Compare {
  private val Home: String = System.getProperty("user.home")

  private val Binaries = Home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.0.0/scala3-library_3-3.0.0.jar"
  private val Sources = Home + "/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala3-library_3/3.0.0/scala3-library_3-3.0.0-sources.jar"

  private val OutputDir = Home + "/IdeaProjects/scala-plugin-for-ultimate/community/tasty/runtime/compared"

  def main(args: Array[String]): Unit = {
    assert(new File(OutputDir).getParentFile.exists)

    println("Parsing TASTy:\t\t" + Binaries)
    new JarInputStream(new BufferedInputStream(new FileInputStream(Binaries))).pipe { in =>
      Iterator.continually(in.getNextEntry).takeWhile(_ != null).filter(_.getName.endsWith(".tasty")).foreach { entry =>
        val file = new File(s"$OutputDir/${entry.getName}")
        file.getParentFile.mkdirs()
        Files.write(Paths.get(file.getPath.replaceFirst("\\.tasty", ".scala")), TreePrinter.textOf(TreeReader.treeFrom(in.readAllBytes())).getBytes)
      }
    }

    println("Extracting sources:\t" + Sources)
    new JarInputStream(new BufferedInputStream(new FileInputStream(Sources))).pipe { in =>
      Iterator.continually(in.getNextEntry).takeWhile(_ != null).filter(_.getName.endsWith(".scala")).foreach { entry =>
        val file = new File(s"$OutputDir/${entry.getName}")
        file.getParentFile.mkdirs()
        val s = new String(in.readAllBytes)
          .replaceAll(raw"\s*//.*?\n", "") // Line comment
          .replaceAll(raw"(?s)/\*.*?\*/", "") // Block comment
          .replaceAll(raw"(?m)^\s+$$", "") // Whitespaces on empty line
          .replaceAll(raw"\n{3,}", "\n\n") // Multiple empty lines
          .replaceAll(raw"\{\n\n", "{\n") // Empty line after {
          .replaceAll(raw"\n\n}", "\n}") // Empty line before }
          .replaceAll(raw"\s+$$", "") // Trailing whitespace

        Files.write(Paths.get(file.getPath.replaceFirst("\\.scala", "-source.scala")), s.getBytes)
      }
    }

    println("Done:\t\t\t\t" + OutputDir)
  }
}
