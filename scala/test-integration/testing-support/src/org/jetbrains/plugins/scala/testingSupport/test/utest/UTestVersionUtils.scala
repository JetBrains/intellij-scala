package org.jetbrains.plugins.scala.testingSupport.test.utest

import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiElement
import com.intellij.testIntegration.createTest.CreateTestAction
import org.jetbrains.plugins.scala.ScalaVersion
import org.jetbrains.plugins.scala.caches.cachedInUserData
import org.jetbrains.plugins.scala.project.settings.ScalaCompilerConfiguration
import org.jetbrains.plugins.scala.project.{ModuleExt, ProjectPsiElementExt, Version}

private[testingSupport]
object UTestVersionUtils {

  private val UTestMavenCoordinates = "com.lihaoyi:utest"

  val Version090 = Version("0.9.0")

  /**
   * @param originalPsiElement psi element in which the corresponding action was invoked (e.g. "Create Test" action)
   */
  def getUTestLibraryVersionForTestModuleOf(originalPsiElement: PsiElement): Option[Version] = {
    val originalActionModule = originalPsiElement.module
    val correspondingModuleForTests = originalActionModule.map(m => CreateTestAction.suggestModuleForTests(m.getProject, m))
    correspondingModuleForTests.flatMap(UTestVersionUtils.getUTestLibraryVersion)
  }

  /**
   * ATTENTION: Library version detection currently works only for SBT, JPS, Maven, Gradle projects.
   * It does NOT work for BSP
   *  - For an SBT/Gradle/Maven project it depends on the registered maven coordinates during the project import
   *  - For JPS the logic is based on the library name (com.intellij.java.library.JavaLibraryUtil#getJpsLibraryVersion
   *
   * @note If you have an instance of production module, you can extract the corresponding test module using
   *       [[com.intellij.testIntegration.createTest.CreateTestAction.suggestModuleForTests]]
   */
  def getUTestLibraryVersion(module: Module): Option[Version] = cachedInUserData("UTestUtils.getUTestLibraryVersion", module, ScalaCompilerConfiguration.modTracker(module.getProject)) {
    if (module.isDisposed || module.getProject.isDefault)
      return None

    val scalaVersion = module.scalaMinorVersion.orNull
    if (scalaVersion == null)
      return None

    val coordinatesWithSuffix = UTestMavenCoordinates + libraryCrossSuffix(scalaVersion)
    val version = JavaLibraryUtil.getLibraryVersion(module, coordinatesWithSuffix)
    Option(version).map(Version(_))
  }

  private def libraryCrossSuffix(scalaVersion: ScalaVersion): String =
    if (scalaVersion.isScala3)
      "_3"
    else
      "_" + scalaVersion.major
}
