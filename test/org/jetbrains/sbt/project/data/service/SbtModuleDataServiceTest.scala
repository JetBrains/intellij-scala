package org.jetbrains.sbt.project.data.service

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.sbt.Sbt
import org.jetbrains.sbt.project.data.SbtModuleNode
import org.jetbrains.sbt.project.module.SbtModule
import org.jetbrains.sbt.resolvers.{SbtResolver, SbtResolverIndexesManager}

/**
 * @author Nikolay Obedin
 * @since 6/9/15.
 */
class SbtModuleDataServiceTest extends ProjectDataServiceTestCase {

  import ExternalSystemDataDsl._

  private def generateProject(imports: Seq[String], resolvers: Set[SbtResolver]): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      modules += new javaModule {
        name := "Module 1"
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
        arbitraryNodes += new SbtModuleNode(imports, resolvers)
      }
    }.build.toDataNode


  def doTest(imports: Seq[String], resolvers: Set[SbtResolver]): Unit = {
    FileUtil.delete(SbtResolverIndexesManager.DEFAULT_INDEXES_DIR)
    importProjectData(generateProject(imports, resolvers))
    val module = ModuleManager.getInstance(getProject).findModuleByName("Module 1")

    if (imports.nonEmpty)
      assert(SbtModule.getImportsFrom(module) == imports)
    else
      assert(SbtModule.getImportsFrom(module) == Sbt.DefaultImplicitImports)

    assert(SbtModule.getResolversFrom(module) == resolvers)
    resolvers.forall(r => SbtResolverIndexesManager().find(r).isDefined)
  }


  def testEmptyImportsAndResolvers(): Unit =
    doTest(Seq.empty, Set.empty)

  def testNonEmptyImports(): Unit =
    doTest(Seq("first import", "second import"), Set.empty)

  def testNonEmptyResolvers(): Unit =
    doTest(Seq.empty, Set(
      SbtResolver(SbtResolver.Kind.Maven, "maven resolver", "http:///nothing"),
      SbtResolver(SbtResolver.Kind.Ivy, "ivy resolver", getProject.getBasePath)))

  def testNonEmptyImportsAndResolvers(): Unit =
    doTest(Seq("first import", "second import"), Set(
      SbtResolver(SbtResolver.Kind.Maven, "maven resolver", "http://nothing"),
      SbtResolver(SbtResolver.Kind.Ivy, "ivy resolver", getProject.getBasePath)))

  def testModuleIsNull(): Unit = {
    val testProject = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      arbitraryNodes += new SbtModuleNode(Seq("some import"), Set.empty)
    }.build.toDataNode

    importProjectData(testProject)
  }
}
