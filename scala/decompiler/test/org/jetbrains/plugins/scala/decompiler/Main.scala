package org.jetbrains.plugins.scala.decompiler

import java.io.{BufferedInputStream, File, FileInputStream}
import java.nio.file.{Files, Path, Paths}
import java.util.jar.JarInputStream
import scala.util.chaining.scalaUtilChainingOps

object Main {
  sealed abstract class Mode
  object Mode {
    case object Parse extends Mode
    case object Test extends Mode
    case object Benchmark extends Mode
  }

  private val mode: Mode = Mode.Test

  private val Home: String = System.getProperty("user.home")

  private val Repository = Home + "/.ivy2/cache/"
  private val OutputDir = Home + "/IdeaProjects/scala-plugin-for-ultimate/community/scala/decompiler/target/comparison"

  private val Libraries = Seq(
    "org.scala-lang/scala-library/jars/scala-library-2.13.10.jar",
    "org.scala-lang/scala-reflect/jars/scala-reflect-2.13.10.jar",
    "org.scala-lang/scala-compiler/jars/scala-compiler-2.13.10.jar",

    "org.scalatest/scalatest-core_2.13/bundles/scalatest-core_2.13-3.2.14.jar",
    "org.scalatest/scalatest-diagrams_2.13/bundles/scalatest-diagrams_2.13-3.2.14.jar",
    "org.scalatest/scalatest-featurespec_2.13/bundles/scalatest-featurespec_2.13-3.2.14.jar",
    "org.scalatest/scalatest-flatspec_2.13/bundles/scalatest-flatspec_2.13-3.2.14.jar",
    "org.scalatest/scalatest-freespec_2.13/bundles/scalatest-freespec_2.13-3.2.14.jar",
    "org.scalatest/scalatest-funspec_2.13/bundles/scalatest-funspec_2.13-3.2.14.jar",
    "org.scalatest/scalatest-funsuite_2.13/bundles/scalatest-funsuite_2.13-3.2.14.jar",
    "org.scalatest/scalatest-matchers-core_2.13/bundles/scalatest-matchers-core_2.13-3.2.14.jar",
    "org.scalatest/scalatest-mustmatchers_2.13/bundles/scalatest-mustmatchers_2.13-3.2.14.jar",
    "org.scalatest/scalatest-propspec_2.13/bundles/scalatest-propspec_2.13-3.2.14.jar",
    "org.scalatest/scalatest-refspec_2.13/bundles/scalatest-refspec_2.13-3.2.14.jar",
    "org.scalatest/scalatest-shouldmatchers_2.13/bundles/scalatest-shouldmatchers_2.13-3.2.14.jar",
    "org.scalatest/scalatest-wordspec_2.13/bundles/scalatest-wordspec_2.13-3.2.14.jar",

    "org.scalactic/scalactic_2.13/jars/scalactic_2.13-3.2.14.jar",

    "org.scalacheck/scalacheck_2.13/jars/scalacheck_2.13-1.17.0.jar",

    "dev.zio/zio_2.13/jars/zio_2.13-2.0.2.jar",
    "dev.zio/zio-streams_2.13/jars/zio-streams_2.13-2.0.2.jar",
    "dev.zio/zio-stacktracer_2.13/jars/zio-stacktracer_2.13-2.0.2.jar",

    "org.typelevel/cats-core_2.13/jars/cats-core_2.13-2.8.0.jar",
    "org.typelevel/cats-effect_2.13/jars/cats-effect_2.13-3.3.14.jar",
    "org.typelevel/cats-effect-kernel_2.13/jars/cats-effect-kernel_2.13-3.3.14.jar",
    "org.typelevel/cats-effect-std_2.13/jars/cats-effect-std_2.13-3.3.14.jar",
    "org.typelevel/cats-free_2.13/jars/cats-free_2.13-2.8.0.jar",
    "org.typelevel/cats-kernel_2.13/jars/cats-kernel_2.13-2.8.0.jar",
    "org.typelevel/cats-kernel-laws_2.13/jars/cats-kernel-laws_2.13-2.8.0.jar",
    "org.typelevel/cats-laws_2.13/jars/cats-laws_2.13-2.8.0.jar",

    "org.scalaz/scalaz-core_2.13/jars/scalaz-core_2.13-7.3.7.jar",
    "org.scalaz/scalaz-effect_2.13/jars/scalaz-effect_2.13-7.3.7.jar",

    "com.typesafe.akka/akka-actor_2.13/jars/akka-actor_2.13-2.7.0.jar",
    "com.typesafe.akka/akka-actor-typed_2.13/jars/akka-actor-typed_2.13-2.7.0.jar",
    "com.typesafe.akka/akka-coordination_2.13/jars/akka-coordination_2.13-2.7.0.jar",
    "com.typesafe.akka/akka-cluster_2.13/jars/akka-cluster_2.13-2.7.0.jar",
    "com.typesafe.akka/akka-http_2.13/jars/akka-http_2.13-10.5.0.jar",
    "com.typesafe.akka/akka-http-core_2.13/jars/akka-http-core_2.13-10.5.0.jar",
    "com.typesafe.akka/akka-persistence_2.13/jars/akka-persistence_2.13-2.7.0.jar",
    "com.typesafe.akka/akka-parsing_2.13/jars/akka-parsing_2.13-10.5.0.jar",
    "com.typesafe.akka/akka-remote_2.13/jars/akka-remote_2.13-2.7.0.jar",
    "com.typesafe.akka/akka-stream_2.13/jars/akka-stream_2.13-2.7.0.jar",

    "com.typesafe.play/play_2.13/jars/play_2.13-2.9.0-M4.jar",
    "com.typesafe.play/play-configuration_2.13/jars/play-configuration_2.13-2.9.0-M4.jar",
    "com.typesafe.play/play-functional_2.13/jars/play-functional_2.13-2.10.0-RC7.jar",
    "com.typesafe.play/play-json_2.13/jars/play-json_2.13-2.10.0-RC7.jar",
    "com.typesafe.play/play-streams_2.13/jars/play-streams_2.13-2.9.0-M4.jar",

    "co.fs2/fs2-core_2.13/jars/fs2-core_2.13-3.6.1.jar",

    "io.getquill/quill-sql_2.13/jars/quill-sql_2.13-4.6.0.jar",
    "io.getquill/quill-jdbc-zio_2.13/jars/quill-jdbc-zio_2.13-4.6.0.jar",

    "org.tpolecat/doobie-core_2.13/jars/doobie-core_2.13-1.0.0-RC1.jar",
    "org.tpolecat/doobie-free_2.13/jars/doobie-free_2.13-1.0.0-RC1.jar",
  )

  def main(args: Array[String]): Unit = {
    assert(new File(OutputDir).getParentFile.exists)

    val start = System.currentTimeMillis()

    Libraries.sortBy(_.split('/').last).foreach { binaries =>
      println("Parsing signatures:\t\t" + binaries)
      new JarInputStream(new BufferedInputStream(new FileInputStream(Repository + "/" + binaries))).pipe { in =>
        Iterator.continually(in.getNextEntry).takeWhile(_ != null).filter(_.getName.endsWith(".class")).foreach { entry =>
          val file = new File(s"$OutputDir/${entry.getName}")
          val fileName = entry.getName.split('/').last
          val path = Paths.get(file.getPath.replaceFirst("\\.class$", ".scala"))
          mode match {
            case Mode.Parse =>
              file.getParentFile.mkdirs()
              Decompiler.sourceNameAndText(fileName, in.readAllBytes()) match {
                case Some((_, text)) => Files.write(path, text.getBytes)
                case None =>
              }
            case Mode.Test =>
              if (path.toFile.exists()) { // Inner classes don't have separate source files
                val expected = new String(Files.readAllBytes(path))
                Decompiler.sourceNameAndText(fileName, in.readAllBytes()) match {
                  case Some((_, actual)) =>
                    val actualPath = Path.of(path.toString.replaceFirst("\\.scala$", ".actual.scala"))
                    if (expected != actual) {
                      System.err.println(path.toString.substring(OutputDir.length + 1))
                      Files.write(actualPath, actual.getBytes)
                    } else {
                      val actualFile = actualPath.toFile
                      if (actualFile.exists()) {
                        actualFile.delete()
                      }
                    }
                  case None =>
                }
              }
            case Mode.Benchmark =>
              Decompiler.sourceNameAndText(entry.getName, in.readAllBytes())
          }
        }
      }

      val sources = Repository + "/" + binaries.replace("/jars/", "/srcs/").replaceFirst("\\.jar$", "-sources.jar")

      if (mode == Mode.Parse && new File(sources).exists()) {
        println("Extracting sources:\t" + sources)
        new JarInputStream(new BufferedInputStream(new FileInputStream(sources))).pipe { in =>
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

            Files.write(Paths.get(file.getPath.replaceFirst("\\.scala$", "-source.scala")), s.getBytes)
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
