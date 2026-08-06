package org.jetbrains.sbt.project

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.{PsiFile, PsiManager}
import org.jetbrains.plugins.scala.{Scala3Language, SlowTests2}
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.sbt.SbtTestDataUtils
import org.jetbrains.sbt.SbtVersion
import org.jetbrains.sbt.language.SbtLanguageScala3
import org.jetbrains.sbt.project.ScalaExternalSystemImportingTestBase.TestProjectCopyOptions
import org.junit.Assert.{assertNotNull, assertTrue}
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

import java.nio.file.Path

@RunWith(classOf[JUnit4])
@Category(Array(classOf[SlowTests2]))
class SbtBuildFilesLanguageTest extends SbtExternalSystemImportingTestLike {
  override protected def getTestDataProjectPath: String =
    SbtTestDataUtils.resolveRelativePath(
      s"sbt-project-structure-tests/testdata/sbt/projects/${getTestName(true)}",
    )

  override protected def getTestProjectCopyOptions: TestProjectCopyOptions =
    super.getTestProjectCopyOptions.copy(copyToTemporaryDir = true)

  override protected def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_17

  @Test
  def buildFilesLanguage(): Unit = {
    injectVariable(
      getTestProjectPath / "project" / "build.properties",
      "$LATEST_SBT_2$",
      SbtVersion.Latest.Sbt_2.minor
    )

    importProject(false)

    val dayEnumPsiFile = findPsiFile(getTestProjectPath / "project" / "Day.scala")
    val buildSbtPsiFile = findPsiFile(getTestProjectPath / "build.sbt")

    assertTrue(s"Day.scala enum is not a Scala 3 source file", dayEnumPsiFile.getLanguage.isKindOf(Scala3Language.INSTANCE))
    assertTrue(s"build.sbt is not a Scala 3 sbt build file", buildSbtPsiFile.getLanguage.isKindOf(SbtLanguageScala3.INSTANCE))
  }

  private def findPsiFile(path: Path): PsiFile = {
    val virtualFile = VfsUtil.findFile(path, true)
    assertNotNull(s"Could not find a virtual file for: $path", virtualFile)
    val psiFile = PsiManager.getInstance(getMyProject).findFile(virtualFile)
    assertNotNull(s"Could not find a PSI file for: $path", psiFile)
    psiFile
  }
}
