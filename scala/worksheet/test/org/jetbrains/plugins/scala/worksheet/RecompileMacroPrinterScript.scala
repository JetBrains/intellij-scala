package org.jetbrains.plugins.scala.worksheet

import com.intellij.openapi.roots.CompilerModuleExtension
import com.intellij.pom.java.LanguageLevel
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.compiler.ScalaCompilerTestBase
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.project.VirtualFileExt
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.runner.{JUnitCore, RunWith}
import org.junit.runners.JUnit4
import org.junit.{Ignore, Test}

import java.nio.file.{Files, Path, StandardCopyOption}
import scala.jdk.CollectionConverters.CollectionHasAsScala

@Ignore("for local running only")
class RecompileMacroPrinterScript {
  import RecompileMacroPrinterScript._

  @Test
  def recompileMacroPrinterForScala3(): Unit = {
    runScript(classOf[RecompileMacroPrinter_3_0_0])
  }
}

private object RecompileMacroPrinterScript {
  class RecompileMacroPrinter_3_0_0 extends AbstractRecompileMacroPrinter(
    scalaVersion = ScalaVersion.fromString("3.0.0").getOrElse(versionError("3.0.0")),
    macroPrinterName = "MacroPrinter_3_0_0"
  ) {
    override def testProjectJdkVersion: LanguageLevel = LanguageLevel.JDK_1_8
  }
  @RunWith(classOf[JUnit4])
  abstract class AbstractRecompileMacroPrinter(scalaVersion: ScalaVersion, macroPrinterName: String)
    extends ScalaCompilerTestBase {

    // Set an exact Scala version.
    injectedScalaVersion = scalaVersion

    override protected val includeCompilerAsLibrary: Boolean = true

    private def log(msg: String): Unit =
      println(s"${this.getClass.getSimpleName}: $msg")

    @Test
    def recompileMacroPrinter(): Unit = {
      log("start")

      val resourcesPath = scalaUltimateProjectDir.resolve(Path.of(
        "community", "scala", "runners", "resources"
      ))
      val packagePath = Path.of("org", "jetbrains", "plugins", "scala", "worksheet")
      val sourceFileName = s"${macroPrinterName}_source.scala"
      val targetDir = resourcesPath.resolve(packagePath)
      val sourceFile = targetDir.resolve(Path.of("src", sourceFileName))
      assertTrue(Path.of(sourceFile.toUri).exists)

      log("reading source file")
      val sourceContent = Files.readString(sourceFile)
      addFileToProjectSources(sourceFileName, sourceContent)
      log(s"compiling using Scala ${scalaVersion.minor}")
      compiler.make().assertNoProblems()

      val compileOutput = CompilerModuleExtension.getInstance(getModule).getCompilerOutputPath
      assertTrue("compilation output not found", compileOutput.exists())

      val folderWithClasses = compileOutput.toPath.resolve(packagePath)
      assertTrue(folderWithClasses.exists)

      val classes = folderWithClasses.children()
      assertEquals(
        Set(s"$macroPrinterName$$.class", s"$macroPrinterName.class", s"$macroPrinterName.tasty"),
        classes.map(_.getFileName.toString).toSet
      )

      log(
        s"""copying ${classes.length} classes: $targetDir
           |    from : $folderWithClasses
           |    to   : $targetDir""".stripMargin
      )

      classes.foreach { compiledFile =>
        val resultFile = targetDir.resolve(compiledFile.getFileName)
        Files.copy(compiledFile, resultFile, StandardCopyOption.REPLACE_EXISTING)
      }
      log("end")
    }
  }

  private def runScript(cls: Class[?]): Unit = {
    val result = JUnitCore.runClasses(cls)
    result.getFailures.asScala.headOption match {
      case Some(failure) => throw failure.getException
      case None =>
    }
  }

  private def versionError(version: String): Nothing =
    sys.error(s"Scala $version is not recognized as an official Scala release")

  private def scalaUltimateProjectDir: Path = {
    val file = Path.of(getClass.getProtectionDomain.getCodeSource.getLocation.toURI)
    file         // <ultimate repo>/community/scala/worksheet/target/scala-2.13/test-classes
      .getParent // <ultimate repo>/community/scala/worksheet/target/scala-2.13
      .getParent // <ultimate repo>/community/scala/worksheet/target
      .getParent // <ultimate repo>/community/scala/worksheet
      .getParent // <ultimate repo>/community/scala
      .getParent // <ultimate repo>/community
      .getParent // <ultimate repo>
  }
}
