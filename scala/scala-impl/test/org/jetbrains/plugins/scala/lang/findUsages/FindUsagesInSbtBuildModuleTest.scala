package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.project.ProjectUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiManager, PsiNamedElement}
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.findUsages.factory.{ScalaFindUsagesConfiguration, ScalaFindUsagesHandler, ScalaTypeDefinitionFindUsagesOptions}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.{assertEquals, assertNotNull, fail}

import scala.collection.mutable

class FindUsagesInSbtBuildModuleTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestProjectPath: String =
    s"${TestUtils.getTestDataPath}/findUsages/sbt_projects/${getTestName(true)}"

  protected def defaultOptions = new ScalaTypeDefinitionFindUsagesOptions(myTestFixture.getProject)

  override def setUp(): Unit = {
    super.setUp()
    importProject(false)
  }

  //https://youtrack.jetbrains.com/issue/SCL-8698/Find-usages-doesnt-search-inside-build.sbt
  def testFindUsageOfDefinitionInBuildModuleScalaFileInBuildSbtFile(): Unit = {
    val psiFile = findFileInProject("project/BuildCommons.scala")
    assertDefinitionFoundInFiles(psiFile, "myLibraryVersion1", Seq("build.sbt", "other.sbt", "project/BuildUtils.scala"))
    assertDefinitionFoundInFiles(psiFile, "myLibraryVersion2", Seq("build.sbt", "other.sbt", "project/BuildUtils.scala"))
  }

  private def getProjectDir: VirtualFile = {
    val file = ProjectUtil.guessProjectDir(myTestFixture.getProject)
    assertNotNull(s"Can't guess project dir", file)
    file
  }

  private def findFileInProject(relativePath: String): PsiFile = {
    val projectDir = getProjectDir

    val vFile = projectDir.findFileByRelativePath(relativePath)
    assertNotNull(s"Can't find virtual file ${projectDir.getCanonicalPath}/$relativePath", vFile)

    val psiFile = PsiManager.getInstance(myTestFixture.getProject).findFile(vFile)
    assertNotNull(s"Can't psi file for $vFile", psiFile)
    psiFile
  }

  private def assertDefinitionFoundInFiles(psiFile: PsiFile, definitionName: String, expectedFoundFileNames: Seq[String]): Unit = {
    val namedElement = findNamedElement(psiFile, definitionName)
    val usages = findUsages(namedElement)
    val foundFiles = usages.map(_.getVirtualFile)
    val actualFoundFileNames = foundFiles.map(relativePath(getProjectDir, _)).sorted
    assertEquals(
      "File names with found usages",
      expectedFoundFileNames.sorted,
      actualFoundFileNames
    )
  }

  private def relativePath(baseFile: VirtualFile, childFile: VirtualFile): String =
    childFile.getPath.stripPrefix(baseFile.getPath).replace("\\", "/").stripPrefix("/")

  private def findNamedElement(file: PsiFile, name: String): PsiNamedElement =
    file
      .depthFirst()
      .collectFirst { case el: PsiNamedElement if el.getName == name => el }
      .getOrElse {
        fail("Can't find named element myLibraryVersion1").asInstanceOf[Nothing]
      }

  private def findUsages(
    namedElement: PsiNamedElement,
    options: FindUsagesOptions = defaultOptions
  ): Seq[UsageInfo] = {
    val result = new mutable.ArrayBuffer[UsageInfo]

    val usagesProcessor: Processor[UsageInfo] = usage => {
      result += usage
      true
    }
    val handler = new ScalaFindUsagesHandler(namedElement, ScalaFindUsagesConfiguration.getInstance(namedElement.getProject))
    handler.processElementUsages(namedElement, usagesProcessor, options)

    result.toSeq
  }
}
