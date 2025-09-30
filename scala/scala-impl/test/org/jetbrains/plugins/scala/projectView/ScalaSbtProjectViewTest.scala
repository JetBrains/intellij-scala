package org.jetbrains.plugins.scala.projectView

import org.jetbrains.plugins.scala.SlowTests2
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(classOf[JUnit4])
@Category(Array(classOf[SlowTests2]))
class ScalaSbtProjectViewTest extends ScalaSbtProjectViewTestBase {

  @Test
  def simple(): Unit = {
    val expectedStructure = """Project: root
                              | simple *[root]*
                              |  *foo*
                              |   src
                              |    *main*
                              |     scala
                              |      Foo
                              |  .bsp
                              |   sbt.json
                              |  build.sbt
                              |  project *[root-build]*
                              |   build.properties
                              |  src
                              |   *main*
                              |    scala
                              |     Dummy
                              |""".stripMargin
    runTest(expectedStructure)
  }

  @Test
  def sourcesOutsideOfProject(): Unit = {
    val expectedStructure = """Project: root
                              | GroupNode: root
                              |  *main*
                              |   externalSources
                              |    External
                              |    dummy
                              |     ExternalDummy
                              | testProject *[root]*
                              |  *foo*
                              |   src
                              |    *main*
                              |     scala
                              |      Foo
                              |  .bsp
                              |   sbt.json
                              |  build.sbt
                              |  project *[root-build]*
                              |   build.properties
                              |  src
                              |   *main*
                              |    scala
                              |     Dummy
                              |   *test*
                              |    scala
                              |     DummyTest
                              |""".stripMargin
    runTestWithOutsideSources(projectDirectory = "testProject", expectedStructure)
  }

  @Test
  def twoLinkedProjects(): Unit = {
    val expectedStructure = """Project: root
                              | GroupNode: simple
                              |  simple *[simple.root]*
                              |   *foo*
                              |    src
                              |     *main*
                              |      scala
                              |       Foo
                              |   .bsp
                              |    sbt.json
                              |   build.sbt
                              |   project *[root-build]*
                              |    build.properties
                              |   src
                              |    *main*
                              |     scala
                              |      Dummy
                              | testProject *[root]*
                              |  *dummy*
                              |  *foo*
                              |  .bsp
                              |   sbt.json
                              |  build.sbt
                              |  project *[root-build]*
                              |   build.properties
                              |""".stripMargin
    runtTestWithTwoLinkedProjects(rootProjectDirectory = "testProject", linkedProjectDirectory = "simple", expectedStructure)
  }

  // It tests the functionality of org.jetbrains.plugins.scala.projectView.ScalaTreeStructureProvider.convertGroupNodeToPsiDirectoryNode
  @Test
  def twoLinkedProjectsWithoutGroupingNode(): Unit = {
    val expectedStructure = """Project: root
                              | simple *[simple.root]*
                              |  *foo*
                              |   src
                              |    *main*
                              |     scala
                              |      Foo
                              |    *test*
                              |     scala
                              |      FooTest
                              |  .bsp
                              |   sbt.json
                              |  build.sbt
                              |  project *[root-build]*
                              |   build.properties
                              |  src
                              |   *main*
                              |    scala
                              |     Dummy
                              |   *test*
                              |    scala
                              |     DummyTest
                              | testProject *[root]*
                              |  *dummy*
                              |  *foo*
                              |  .bsp
                              |   sbt.json
                              |  build.sbt
                              |  project *[root-build]*
                              |   build.properties
                              |""".stripMargin
    runtTestWithTwoLinkedProjects(rootProjectDirectory = "testProject", linkedProjectDirectory = "simple", expectedStructure)
  }

  @Test
  def SCL23868(): Unit = {
    val expectedStructure = """Project: root
                              | SCL23868 *[root]*
                              |  *foo*
                              |   custom *[root.main]*
                              |    dummy
                              |  .bsp
                              |   sbt.json
                              |  build.sbt
                              |  project *[root-build]*
                              |   build.properties
                              |""".stripMargin
    runTest(expectedStructure)
  }
}
