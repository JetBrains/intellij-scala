package org.jetbrains.sbt
package model

import org.junit.{Test, Assert}
import scala.xml.XML
import org.jetbrains.sbt.project.model._
import java.io.File

/**
 * @author Pavel Fatin
 */
class ParserTest {
  @Test
  def testDataParsing() {
    val xml = XML.load(getClass.getResource("structure.xml"))
    val actual = Parser.parse(xml, new File("$HOME"))
    val expected = ParserTest.createExpectedStructure

    Assert.assertEquals(expected, actual)
  }
}

object ParserTest {
  def createExpectedStructure: Structure = {
    val moduleId = ModuleId("org.scala-lang", "scala-library", "2.10.1")

    val configuration = Configuration(
      id = "compile",
      sources = Seq(new File("$BASE/src/main/scala"), new File("$BASE/src/main/java")),
      resources = Seq(new File("$BASE/src/main/resources")),
      classes = new File("$BASE/target/scala-2.10/classes"),
      dependencies = Seq.empty,
      modules = Seq(moduleId),
      jars = Seq.empty)

    val build = Build(
      Seq(new File("$HOME/.sbt/boot/scala-2.9.2/org.scala-sbt/sbt/0.12.2/api-0.12.2.jar")),
      Seq("import sbt._, Process._, Keys._"))

    val java = Java(
      home = new File("$BASE/some/home"),
      options = Seq("-j1", "-j2"))

    val scala = Scala(
      version = "2.10.1",
      libraryJar = new File("$HOME/.sbt/boot/scala-2.10.1/lib/scala-library.jar"),
      compilerJar = new File("$HOME/.sbt/boot/scala-2.10.1/lib/scala-compiler.jar"),
      extraJars = Seq(new File("$HOME/.sbt/boot/scala-2.10.1/lib/scala-reflect.jar")),
      options = Seq("-s1", "-s2"))

    val project = Project(
      name = "some-name",
      organization = "some-organization",
      version = "1.2.3",
      base = new File("$BASE"),
      build,
      configurations = Seq(configuration),
      java = Some(java),
      scala = Some(scala),
      projects = Seq())

    val module = Module(
      id = moduleId,
      binaries = Seq(new File("$HOME/.sbt/boot/scala-2.10.1/lib/scala-library.jar")),
      docs = Seq(new File("$HOME/.ivy2/cache/org.scala-lang/scala-library/docs/scala-library-2.10.1-javadoc.jar")),
      sources = Seq(new File("$HOME/.ivy2/cache/org.scala-lang/scala-library/srcs/scala-library-2.10.1-sources.jar")))

    val repository = Repository(new File("."), Seq(module))

    Structure(project, Some(repository))
  }
}