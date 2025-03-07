import CompatibilityTest._
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.plugins.scala.util.ScalaPluginJars
import org.jetbrains.plugins.scala.{DependencyManagerBase, ScalaVersion}
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

import java.io.File
import java.nio.file.{Files, Path}
import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

@RunWith(classOf[Parameterized])
class CompatibilityTest(version: String) {
  @Test
  def compatibilityWith(): Unit = {
    val scalaVersion = ScalaVersion.fromString(version).getOrElse(throw new IllegalArgumentException(version))

    val jars = download(scalaVersion)

    val compiler = compilerFor(scalaVersion, jars)

    val pluginPath =
      if (version.startsWith("2.12")) ScalaPluginJars.compilerPluginJar_2_12
      else if (version.startsWith("2.13")) ScalaPluginJars.compilerPluginJar_2_13
      else if (version.startsWith("3.")) ScalaPluginJars.compilerPluginJar_3_3
      else throw new IllegalArgumentException(version)

    var commonOptions = Seq(
      "-usejavacp",
      "-Xplugin:" + pluginPath,
      "-Xplugin-require:intellij-compiler-plugin")

    inTempDirectory { directory =>
      def compile(name: String, code: String, options: String*): String = {
        val path = directory.resolve(name)
        Files.write(path, code.getBytes)
        compiler.compile(Seq(path), directory, commonOptions ++ options)
      }
      if (scalaVersion.isScala2) {
        val output1 = compile("Definition.scala", Definition, "-feature", "-language:experimental.macros")
        val output2 = compile("Usage.scala", Usage, "-cp", directory.toString, "-Yrangepos")
        assertPresent(scalaVersion, output1 + output2, Type)
      } else {
        val output = compile("TransparentInline.scala", TransparentInline, "-color:never")
        assertPresent(scalaVersion, output, Type)
      }
    }
  }
}

object CompatibilityTest {
  private val Versions = Seq(
//  "2.12.0", "2.12.1", "2.12.2", "2.12.3", "2.12.4", "2.12.5", "2.12.6", "2.12.7", "2.12.8", "2.12.9", "2.12.10", "2.12.11", "2.12.12",
    "2.12.13", "2.12.14", "2.12.15", "2.12.16", "2.12.17", "2.12.18", "2.12.19", "2.12.20",
//  "2.13.0",
    "2.13.1", "2.13.2", "2.13.3", "2.13.4", "2.13.5", "2.13.6", "2.13.7", "2.13.8", "2.13.9", "2.13.10", "2.13.11", "2.13.12", "2.13.13", "2.13.14", "2.13.15", "2.13.16",
//  "3.0.0", "3.0.1", "3.0.2",
//  "3.1.0", "3.1.1", "3.1.2", "3.1.3",
//  "3.2.0", "3.2.1", "3.2.2",
    "3.3.0",
    "3.3.1", "3.3.3", "3.3.4", "3.3.5",
    "3.4.0", "3.4.1", "3.4.2", "3.4.3",
    "3.5.0", "3.5.1", "3.5.2",
    "3.6.2", "3.6.3",
  )

  private val Definition =
    "object Definition { def id_impl(c: scala.reflect.macros.whitebox.Context)(x: c.Expr[Int]): c.Expr[Any] = x; def id(x: Int): Any = macro id_impl }"

  private val Usage =
    "object Usage { val v = Definition.id(123) }"

  private val TransparentInline =
    "transparent inline def id(x: Any): Any = x; val _ = id(123)"

  private val Type =
    "<type>123</type>"

  @Parameters(name = "{0}")
  def versions: util.Collection[String] = Versions.asJava

  private def download(version: ScalaVersion): Seq[Path] = {
    val dependencyManager = new DependencyManagerBase() { override protected def progressIndicator: Option[ProgressIndicator] = None }
    dependencyManager.resolveSafe(DependencyManagerBase.scalaCompilerDescription(version).transitive())
      .getOrElse(throw new RuntimeException(s"Cannot download Scala $version")).map(_.file)
  }

  private def compilerFor(version: ScalaVersion, classpath: Seq[Path]): Compiler = (sources: Seq[Path], output: Path, options: Seq[String]) => {
    val args = {
      val mainClass = if (version.isScala2) "scala.tools.nsc.Main" else "dotty.tools.dotc.Main"
      Seq("java", "-cp", classpath.map(_.toString).mkString(File.pathSeparator), mainClass, "-d", output.toString) ++ sources.map(_.toString) ++ options
    }
    val process = new ProcessBuilder().command(args: _*).start()
    process.waitFor()
    val stdin = new String(process.getInputStream.readAllBytes())
    val stderr = new String(process.getErrorStream.readAllBytes())
    stdin + stderr
  }

  private trait Compiler { def compile(sources: Seq[Path], output: Path, options: Seq[String]): String }

  private def inTempDirectory[A](f: Path => A): A = {
    val directory = Files.createTempDirectory(getClass.getName).toFile
    try {
      f(directory.toPath)
    } finally {
      directory.listFiles().foreach(_.delete())
      directory.delete()
    }
  }

  private def assertPresent(version: ScalaVersion, output: String, tpe: String): Unit = {
    val lines = output.lines.toList
    val (count, index) = if (version.isScala2) (3, 0) else (4, 3)
    assertTrue(lines.size() == count)
    assertTrue(lines.get(index).contains(tpe))
  }
}
