package org.jetbrains.sbt
package project

import ProjectStructureDsl._
import org.jetbrains.plugins.scala.SlowTests
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests]))
class ProjectImportingTest extends ImportingTestCase with InexactMatch {
  def testSimple(): Unit = {
    importProject()
    assertProjectsEqual(new project {
      modules += new module {
        name := "simple"
        contentRoots += getProjectPath
        ProjectStructureDsl.sources := Seq("src/main/scala", "src/main/java")
        testSources := Seq("src/test/scala", "src/test/java")
        resources := Seq("src/main/resources")
        testResources := Seq("src/test/resources")
        excluded := Seq("target")
      }

      modules += new module {
        name := "simple-build"
        ProjectStructureDsl.sources := Seq("")
        excluded := Seq("project/target", "target")
      }
    })
  }
}
