package org.jetbrains.sbt.project

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.{PsiFile, PsiManager}
import junit.framework.TestCase.{assertFalse, assertNotNull, assertTrue}
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.experimental.categories.Category

import java.nio.file.Path

sealed abstract class SbtProjectFileHighlightingTestBase(sbtVersion: String) extends SbtExternalSystemImportingTestLike {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/sbtProjectFileHighlighting_$sbtVersion"

  override protected def projectJdkLanguageLevel: LanguageLevel = sbtVersion match {
    case "0_13" => LanguageLevel.JDK_1_8
    case "1" | "2" => LanguageLevel.JDK_17
    case _ => throw new IllegalArgumentException(s"Unsupported sbt version: $sbtVersion")
  }

  override def setUp(): Unit = {
    super.setUp()
    SbtProjectResolver.processOutputOfLatestStructureDump = ""
    SbtCachesSetupUtil.setupCoursierAndIvyCache(getProject)
  }

  def testSbtProjectFileHighlighting(): Unit = {
    val pluginsSbtPath = getTestProjectPath / "project" / "plugins.sbt"
    val buildSbtPath = getTestProjectPath / "build.sbt"
    val scalaFilePath = getTestProjectPath / "project" / "Dependencies.scala"

    val shouldHighlightPluginsSbtBefore = ProblemHighlightFilter.shouldHighlightFile(findPsiFile(pluginsSbtPath))
    val shouldHighlightBuildSbtBefore = ProblemHighlightFilter.shouldHighlightFile(findPsiFile(buildSbtPath))
    val shouldHighlightScalaFileBefore = ProblemHighlightFilter.shouldHighlightFile(findPsiFile(scalaFilePath))

    assertFalse("plugins.sbt should not be highlighted before the project has been imported", shouldHighlightPluginsSbtBefore)
    assertFalse("build.sbt should not be highlighted before the project has been imported", shouldHighlightBuildSbtBefore)
    assertFalse("Dependencies.scala should not be highlighted before the project has been imported", shouldHighlightScalaFileBefore)

    importProject(false)

    val shouldHighlightPluginsSbtAfter = ProblemHighlightFilter.shouldHighlightFile(findPsiFile(pluginsSbtPath))
    val shouldHighlightBuildSbtAfter = ProblemHighlightFilter.shouldHighlightFile(findPsiFile(buildSbtPath))
    val shouldHighlightScalaFileAfter = ProblemHighlightFilter.shouldHighlightFile(findPsiFile(scalaFilePath))

    assertTrue("plugins.sbt should be highlighted after the project has been imported", shouldHighlightPluginsSbtAfter)
    assertTrue("build.sbt should be highlighted after the project has been imported", shouldHighlightBuildSbtAfter)
    assertTrue("The sbt project should have been imported", SbtProjectImportStateService.instance(getProject).isImported(findPsiFile(buildSbtPath)))
    assertTrue("Dependencies.scala should be highlighted after the project has been imported", shouldHighlightScalaFileAfter)
  }

  private def findPsiFile(path: Path): PsiFile = {
    // It's necessary to refresh the virtual file system to get the up-to-date VirtualFile/PsiFile instances before
    // and after project import.
    val virtualFile = VfsUtil.findFile(path, true)
    assertNotNull(s"Could not find virtual file for path: $path", virtualFile)

    val manager = PsiManager.getInstance(getProject)
    val psiFile = manager.findFile(virtualFile)
    assertNotNull(s"Could not find psi file for virtual file: $virtualFile", psiFile)
    psiFile
  }
}

@Category(Array(classOf[SlowTests2]))
class SbtProjectFileHighlightingTest_0_13 extends SbtProjectFileHighlightingTestBase("0_13")

@Category(Array(classOf[SlowTests2]))
class SbtProjectFileHighlightingTest_1 extends SbtProjectFileHighlightingTestBase("1")