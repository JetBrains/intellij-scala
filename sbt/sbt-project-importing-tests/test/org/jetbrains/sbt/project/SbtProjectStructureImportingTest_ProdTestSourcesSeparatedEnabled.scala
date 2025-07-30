package org.jetbrains.sbt.project

import com.intellij.openapi.util.io.FileUtil

/**
 * @see [[SbtProjectStructureImportingTest]]
 */
final class SbtProjectStructureImportingTest_ProdTestSourcesSeparatedEnabled
  extends SbtProjectStructureImportingTestBase_ProdTestSourcesSeparated {

  import ProjectStructureDsl._

  // note: this test is for the case in which an additional project is linked to the project.
  // The linked project is project "simple". The ideProject is generated from "twoLinkedProjects" project
  def testTwoLinkedProjects(): Unit = {
    val originalProjectName = "twoLinkedProjects"
    val linkedProjectName = "simple"
    val expectedScalaLibraries = ProjectStructureTestUtils.expectedScalaLibraryWithScalaSdkForSbt(useEnv = true)("2.13.14")
    val linkedSbtProjectPath = generateTestProjectPath(linkedProjectName)
    linkSbtProject(linkedSbtProjectPath, prodTestSourcesSeparated = true)
    val siProjectPath = FileUtil.toSystemIndependentName(getProjectPath)
    val siLinkedSbtProjectPath = FileUtil.toSystemIndependentName(linkedSbtProjectPath)
    runTest(
      new project("testTwoLinkedProjects") {
        modules := Seq(
          new module(originalProjectName) {
            contentRoots += siProjectPath
            excluded := Seq("target")
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(s"$originalProjectName.main") {
            contentRoots := Seq(s"$siProjectPath/src/main", s"$siProjectPath/target/scala-2.13/src_managed/main", s"$siProjectPath/target/scala-2.13/resource_managed/main")
            ProjectStructureDsl.sources := Seq("scala", "java")
            resources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(s"$originalProjectName.test") {
            contentRoots := Seq(s"$siProjectPath/src/test", s"$siProjectPath/target/scala-2.13/src_managed/test", s"$siProjectPath/target/scala-2.13/resource_managed/test")
            testSources := Seq("scala", "java")
            testResources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(s"$originalProjectName.$originalProjectName-build") {
            ProjectStructureDsl.sources := Seq("")
            excluded := Seq("project/target", "target")
            moduleFileDirectoryPath := "twoLinkedProjects"
          },
          new module(linkedProjectName) {
            contentRoots += siLinkedSbtProjectPath
            excluded := Seq("target")
            moduleFileDirectoryPath := "simple"
          },
          new module(s"$linkedProjectName.main") {
            contentRoots := Seq(s"$siLinkedSbtProjectPath/src/main", s"$siLinkedSbtProjectPath/target/scala-2.13/src_managed/main", s"$siLinkedSbtProjectPath/target/scala-2.13/resource_managed/main")
            ProjectStructureDsl.sources := Seq("scala", "java")
            resources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "simple"
          },
          new module(s"$linkedProjectName.test") {
            contentRoots := Seq(s"$siLinkedSbtProjectPath/src/test", s"$siLinkedSbtProjectPath/target/scala-2.13/src_managed/test", s"$siLinkedSbtProjectPath/target/scala-2.13/resource_managed/test")
            testSources := Seq("scala", "java")
            testResources := Seq("resources")
            libraryDependencies := expectedScalaLibraries
            moduleFileDirectoryPath := "simple"
          },
          new module(s"$linkedProjectName.$linkedProjectName-build") {
            ProjectStructureDsl.sources := Seq("")
            excluded := Seq("project/target", "target")
            moduleFileDirectoryPath := "simple"
          }
        )
      }
    )
    assertDirectoryCompletionVariantsForProjectPaths(
      DefaultSbtContentRootsScala213,
      DefaultMainSbtContentRootsScala213,
      DefaultTestSbtContentRootsScala213,
      linkedSbtProjectPath,
      getProjectPath
    )
  }
}
