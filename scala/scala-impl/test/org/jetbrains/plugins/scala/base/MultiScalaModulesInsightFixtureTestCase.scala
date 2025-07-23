package org.jetbrains.plugins.scala.base

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaSDKLoader
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaVersion}

abstract class MultiScalaModulesInsightFixtureTestCase(thisModuleVersion: ScalaVersion, otherModuleVersion: ScalaVersion) extends JavaCodeInsightFixtureTestCase {
  val scalaModuleSdkLoader = ScalaSDKLoader()

  val otherModuleName = "otherModule"
  val otherModuleSourceDir = s"$otherModuleName/src"
  var otherModule: Module = _

  override def setUp(): Unit = {
    super.setUp()

    scalaModuleSdkLoader.init(getModule, thisModuleVersion)

    otherModule =
      PsiTestUtil.addModule(
        getProject,
        JavaModuleType.getModuleType.asInstanceOf[ModuleType[_ <: ModuleBuilder]],
        otherModuleName,
        myFixture.getTempDirFixture.findOrCreateDir(otherModuleName)
      )

    PsiTestUtil.addSourceRoot(otherModule, myFixture.getTempDirFixture.findOrCreateDir(otherModuleSourceDir))
    scalaModuleSdkLoader.init(otherModule, otherModuleVersion)
    ModuleRootModificationUtil.addDependency(getModule, otherModule)
  }

  override def tearDown(): Unit = {
    scalaModuleSdkLoader.clean(otherModule)
    scalaModuleSdkLoader.clean(getModule)
    super.tearDown()
  }

  protected def doSimpleHighlightingTest(otherModuleCode: String, thisModuleCode: String): Unit = {
    myFixture.addFileToProject(s"$otherModuleSourceDir/Test.scala", otherModuleCode)
    myFixture.configureByText(ScalaFileType.INSTANCE, thisModuleCode)
    myFixture.testHighlighting(false, false, false, myFixture.getFile.getVirtualFile)
  }
}

object MultiScalaModulesInsightFixtureTestCase {
  abstract class Scala2DependingOnScala3LTS extends MultiScalaModulesInsightFixtureTestCase(ScalaVersion.Latest.Scala_2, ScalaVersion.Latest.Scala_3_LTS)
  abstract class Scala3LTSDependingOnScala2 extends MultiScalaModulesInsightFixtureTestCase(ScalaVersion.Latest.Scala_3_LTS, ScalaVersion.Latest.Scala_2)
}
