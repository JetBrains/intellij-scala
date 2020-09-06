package org.jetbrains.sbt.project.data.service

import java.io.File
import java.net.URI

import com.intellij.facet.FacetManager
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.sbt.project.data.{AndroidFacetData, AndroidFacetNode, ModuleNode}
import org.junit.Assert._
import org.junit.Ignore

import scala.io.Source

/**
 * @author Nikolay Obedin
 * @since 6/15/15.
 */
@Ignore
class AndroidFacetDataServiceTest extends ProjectDataServiceTestCase {

  import ExternalSystemDataDsl._

  private def generateProject(proguardConfig: Seq[String]): DataNode[ProjectData] =
    new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath

      modules += new javaModule {
        val uri: URI = new File(getProject.getBasePath).toURI
        val moduleName = "Module 1"
        projectId := ModuleNode.combinedId(moduleName, Option(uri))
        projectURI := uri
        name := moduleName
        moduleFileDirectoryPath := getProject.getBasePath + "/module1"
        externalConfigPath := getProject.getBasePath + "/module1"
        arbitraryNodes += new AndroidFacetNode(AndroidFacetData(
          version = "21",
          manifest = new File(getProject.getBasePath + "/manifest.xml"),
          apk = new File(getProject.getBasePath + "/test.apk"),
          res = new File(getProject.getBasePath + "/res"),
          assets = new File(getProject.getBasePath + "/assets"),
          gen = new File(getProject.getBasePath + "/gen"),
          libs = new File(getProject.getBasePath + "/libs"),
          isLibrary = true,
          proguardConfig = proguardConfig
        ))
      }
    }.build.toDataNode

  private def doTestFacetSetup(proguardConfig: Seq[String]): Unit = {
    importProjectData(generateProject(proguardConfig))

    val module = ModuleManager.getInstance(getProject).findModuleByName("Module 1")
    assertNotNull("Module expected", module)

    val facet = FacetManager.getInstance(module).getFacetByType(AndroidFacet.ID)
    assertNotNull("Android faced expected", facet)

    val properties = facet.getConfiguration.getState
    assertEquals("/../gen", properties.GEN_FOLDER_RELATIVE_PATH_APT)
    assertEquals("/../gen", properties.GEN_FOLDER_RELATIVE_PATH_AIDL)
    assertEquals("/../manifest.xml", properties.MANIFEST_FILE_RELATIVE_PATH)
    assertEquals("/../res", properties.RES_FOLDER_RELATIVE_PATH)
    assertEquals("/../assets", properties.ASSETS_FOLDER_RELATIVE_PATH)
    assertEquals("/../libs", properties.LIBS_FOLDER_RELATIVE_PATH)
    assertEquals("/../test.apk", properties.APK_PATH)
    assertEquals(proguardConfig.isEmpty, properties.myProGuardCfgFiles.isEmpty)
    assertEquals(proguardConfig.nonEmpty, properties.RUN_PROGUARD)

    if (proguardConfig.nonEmpty) {
      import scala.collection.JavaConverters._
      val proguardConfigPath = FileUtil.toSystemDependentName(getProject.getBasePath + "/proguard-sbt.txt")
      assertEquals(Seq(proguardConfigPath), properties.myProGuardCfgFiles.asScala)
      val actualProguardConfig = using(Source.fromFile(proguardConfigPath))(_.getLines().toVector)
      assertEquals(proguardConfig, actualProguardConfig)
    }
  }

  def testWithoutProguard(): Unit =
    doTestFacetSetup(Seq.empty)

  def testWithProguard(): Unit =
    doTestFacetSetup(Seq("-some-option", "-another-option"))

  def testModuleIsNull(): Unit = {
    val testProject = new project {
      name := getProject.getName
      ideDirectoryPath := getProject.getBasePath
      linkedProjectPath := getProject.getBasePath
      arbitraryNodes += new AndroidFacetNode(AndroidFacetData("", null, null, null, null, null, null, false, Seq.empty))
    }.build.toDataNode
    importProjectData(testProject)
  }
}
