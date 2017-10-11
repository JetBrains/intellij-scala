package org.jetbrains.plugins.cbt.project.model

import org.jetbrains.plugins.cbt._
import org.jetbrains.plugins.cbt.project.model.CbtProjectInfo._
import org.junit.Assert._
import org.junit.Test

import scala.xml._

class DeserializerTest {
  @Test
  def deserializeSimple(): Unit = {
    val xml =
      """
        |<project name="testProject" root="/projects/testProject" rootModule="rootModule">
        |    <modules>
        |        <module name="rootModule" root="/projects/testProject"
        |                target="/projects/testProject/target" scalaVersion="2.11.8" type="default">
        |            <sourceDirs>
        |                <dir>/projects/testProject/src</dir>
        |            </sourceDirs>
        |            <scalacOptions>
        |                <option>-deprecation</option>
        |            </scalacOptions>
        |            <dependencies>
        |                <binaryDependency>org.scala-lang:scala-library:2.11.8</binaryDependency>
        |            </dependencies>
        |            <classpath>
        |                <classpathItem>
        |                  scala-library-2.11.8.jar
        |                </classpathItem>
        |            </classpath>
        |        </module>
        |    </modules>
        |    <libraries>
        |        <library name="org.scala-lang:scala-library:2.11.8">
        |            <jar type="binary">
        |                CBT_HOME/cache/maven/org/scala-lang/scala-library/2.11.8/scala-library-2.11.8.jar
        |            </jar>
        |            <jar type="source">
        |                CBT_HOME/cache/maven/org/scala-lang/scala-library/2.11.8/scala-library-2.11.8-sources.jar
        |            </jar>
        |        </library>
        |    </libraries>
        |    <cbtLibraries>
        |        <library name="CBT:cbt">
        |            <jar type="binary">CBT_HOME/libraries/process/target/scala-2.11/process_2.11-0.9-SNAPSHOT.jar
        |            </jar>
        |        </library>
        |    </cbtLibraries>
        |    <scalaCompilers>
        |        <compiler version="2.11.8">
        |            <jar>scala-compiler-2.11.8.jar</jar>
        |        </compiler>
        |    </scalaCompilers>
        |</project>
        |
      """.stripMargin
    val result = Deserializer.apply(XML.loadString(xml))
    val expected = Project("testProject", "/projects/testProject".toFile, List(Module("rootModule", "/projects/testProject".toFile, "2.11.8", List("/projects/testProject/src".toFile), "/projects/testProject/target".toFile, ModuleType.Default, List(BinaryDependency("org.scala-lang:scala-library:2.11.8")), List(), List("scala-library-2.11.8.jar".toFile), None, List("-deprecation"))), List(Library("org.scala-lang:scala-library:2.11.8", List(LibraryJar("CBT_HOME/cache/maven/org/scala-lang/scala-library/2.11.8/scala-library-2.11.8.jar".toFile, JarType.Binary), LibraryJar("CBT_HOME/cache/maven/org/scala-lang/scala-library/2.11.8/scala-library-2.11.8-sources.jar".toFile, JarType.Source)))), List(Library("CBT:cbt", List(LibraryJar("CBT_HOME/libraries/process/target/scala-2.11/process_2.11-0.9-SNAPSHOT.jar".toFile, JarType.Binary)))), List(ScalaCompiler("2.11.8", List("scala-compiler-2.11.8.jar".toFile))))
    assertEquals(expected, result)
  }
}
