package org.jetbrains.sbt
package project
package data
package service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.ModuleManager
import org.jetbrains.sbt.resolvers._
import org.junit.Assert

import java.io.File
import java.net.URI

class SbtBuildModuleDataServiceTest extends ProjectDataServiceTestCase {

  import ExternalSystemDataDsl._

  private def generateProject(imports: Seq[String], resolvers: Set[SbtResolver]): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      modules += new javaModule {
        val moduleName = "Module 1"
        val uri: URI = new File(getProject.getBasePath).toURI
        val id: String = ModuleNode.combinedId(moduleName, Option(uri))
        projectId := id
        projectURI := uri
        name := moduleName
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
        arbitraryNodes += new SbtBuildModuleNode(SbtBuildModuleData(imports, resolvers, uri))
      }
    }.build.toDataNode


  def doTest(imports: Seq[String], resolvers: Set[SbtResolver]): Unit = {
    import module.SbtModule._

    importProjectData(generateProject(imports, resolvers))
    val sbtModule = ModuleManager.getInstance(getProject).findModuleByName("Module 1")

    val expected = if (imports.nonEmpty) imports else Sbt.DefaultImplicitImports
    Assert.assertEquals(expected, Imports(sbtModule))

    Assert.assertEquals(resolvers, Resolvers(sbtModule))
  }


  def testEmptyImportsAndResolvers(): Unit =
    doTest(Seq.empty, Set.empty)

  def testNonEmptyImports(): Unit =
    doTest(Seq("first import", "second import"), Set.empty)

  def testNonEmptyResolvers(): Unit =
    doTest(Seq.empty, Set(
      new SbtMavenResolver("maven resolver", "https:///nothing"),
      new SbtIvyResolver("ivy resolver", getProject.getBasePath, isLocal = false)))

  def testNonEmptyImportsAndResolvers(): Unit =
    doTest(Seq("first import", "second import"), Set(
      new SbtMavenResolver("maven resolver", "https:///nothing"),
      new SbtIvyResolver("ivy resolver", getProject.getBasePath, isLocal = false)))

  def testModuleIsNull(): Unit = {
    val testProject = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      arbitraryNodes += new SbtBuildModuleNode(SbtBuildModuleData(Seq("some import"), Set.empty[SbtResolver], new URI("somewhere")))
    }.build.toDataNode

    importProjectData(testProject)
  }
}
