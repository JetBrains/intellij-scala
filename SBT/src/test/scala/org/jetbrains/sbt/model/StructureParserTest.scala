package org.jetbrains.sbt
package model

import org.junit.{Test, Assert}
import scala.xml.XML
import org.jetbrains.sbt.project.structure._
import java.io.File

/**
 * @author Pavel Fatin
 */
class StructureParserTest {
  @Test
  def testDataParsing() {
    val xml = XML.load(getClass.getResource("structure.xml"))
    val actual = StructureParser.parse(xml, new File("$HOME"))
    val expected = StructureParserTest.createExpectedStructure

    Assert.assertEquals(expected, actual)
  }
}

object StructureParserTest {
  def createExpectedStructure: Structure = {
    val moduleId = ModuleId("org.scala-lang", "scala-library", "2.10.1")

    val configuration = Configuration(
      id = "compile",
      sources = Seq(Directory(new File("$BASE/src/main/scala"), managed = false), Directory(new File("$BASE/src/main/java"), managed = true)),
      resources = Seq(Directory(new File("$BASE/src/main/resources"), managed = false)),
      classes = new File("$BASE/target/scala-2.10/classes"))

    val build = Build(
      imports = Seq("import sbt._, Process._, Keys._"),
      classes = Seq(new File("$HOME/.sbt/boot/scala-2.9.2/org.scala-sbt/sbt/0.12.2/api-0.12.2.jar")),
      docs = Seq.empty,
      sources = Seq.empty)

    val java = Java(
      home = Some(new File("$BASE/some/home")),
      options = Seq("-j1", "-j2"))

    val scala = Scala(
      version = "2.10.1",
      libraryJar = new File("$HOME/.sbt/boot/scala-2.10.1/lib/scala-library.jar"),
      compilerJar = new File("$HOME/.sbt/boot/scala-2.10.1/lib/scala-compiler.jar"),
      extraJars = Seq(new File("$HOME/.sbt/boot/scala-2.10.1/lib/scala-reflect.jar")),
      options = Seq("-s1", "-s2"))

    val dependencies = Dependencies(
      projects = Seq.empty,
      modules = Seq(ModuleDependency(moduleId, Seq("test"))),
      jars = Seq(JarDependency(new File("/foo/bar.jar"), Seq("test"))))

    val project = Project(
      id = "root",
      name = "some-name",
      organization = "some-organization",
      version = "1.2.3",
      base = new File("$BASE"),
      target = new File(new File("$BASE"), "target"),
      build,
      configurations = Seq(configuration),
      java = Some(java),
      scala = Some(scala),
      dependencies = dependencies)

    val module = Module(
      id = moduleId,
      binaries = Seq(new File("$HOME/.sbt/boot/scala-2.10.1/lib/scala-library.jar")),
      docs = Seq(new File("$HOME/.ivy2/cache/org.scala-lang/scala-library/docs/scala-library-2.10.1-javadoc.jar")),
      sources = Seq(new File("$HOME/.ivy2/cache/org.scala-lang/scala-library/srcs/scala-library-2.10.1-sources.jar")))

    val repository = Repository(new File("."), Seq(module))

    Structure(Seq(project), Some(repository))
  }
}