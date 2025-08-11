package org.jetbrains.plugins.scala.projectView

class ScalaSbtProjectViewTest extends ScalaSbtProjectViewTestBase {

  def testSimple(): Unit = {
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

  def testSourcesOutsideOfProject(): Unit = {
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

  def testTwoLinkedProjects(): Unit = {
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
  def testTwoLinkedProjectsWithoutGroupingNode(): Unit = {
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

  def testSCL23868(): Unit = {
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
