package org.jetbrains.plugins.scala.project

import com.intellij.ide.util.projectWizard.ModuleBuilder
import com.intellij.openapi.module.{JavaModuleType, Module, ModuleType}
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.base.libraryLoaders.ScalaSDKLoader
import org.junit.Assert._

/**
 * Checks that a module whose name ends with `-build` but that has a real Scala SDK
 * is treated as a normal Scala module (SCL-26279).
 *
 * This is the Mill use case where the project is NOT imported through BSP. The user runs the `mill.idea` tool
 * and Mill writes the `.idea` files itself: the build module is named `mill-build`, is a plain Java module and has a Scala SDK library.
 *
 * Real sbt build modules are covered by the sbt and BSP project structure tests.
 */
class ScalaModuleSettingsBuildModuleTest extends JavaCodeInsightFixtureTestCase {

  private val scalaSdkLoader = ScalaSDKLoader()

  /** A Java module named like the Mill build module, with a real Scala 3 SDK library. */
  private var millBuildModule: Module = _

  override protected def setUp(): Unit = {
    super.setUp()

    millBuildModule = PsiTestUtil.addModule(
      getProject,
      JavaModuleType.getModuleType.asInstanceOf[ModuleType[_ <: ModuleBuilder]],
      "mill-build",
      myFixture.getTempDirFixture.findOrCreateDir("mill-build")
    )
    scalaSdkLoader.init(millBuildModule, ScalaVersion.Latest.Scala_3)
  }

  override protected def tearDown(): Unit =
    try scalaSdkLoader.clean(millBuildModule)
    finally super.tearDown()

  def testMillBuildModuleWithScalaSdkIsAScalaModule(): Unit = {
    assertTrue("The module name must still look like a build module", millBuildModule.isBuildModule)
    assertFalse("Mill writes a plain Java module, not an sbt build module", millBuildModule.hasBuildModuleType)

    assertTrue("The module must have Scala 3", millBuildModule.hasScala3)
    assertTrue("The module must use its Scala SDK library", millBuildModule.scalaSdk.isDefined)
    assertFalse("The compiler classpath must come from the Scala SDK", millBuildModule.scalaCompilerClasspath.isEmpty)

    assertTrue("The project must have Scala 3", getProject.hasScala3)
    assertTrue("modulesWithScala must contain mill-build", getProject.modulesWithScala.contains(millBuildModule))
  }
}
