package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.{PsiFile, PsiNamedElement}
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor
import org.jetbrains.plugins.scala.SlowTests
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.findUsages.factory.{ScalaFindUsagesConfiguration, ScalaFindUsagesHandler, ScalaTypeDefinitionFindUsagesOptions}
import org.jetbrains.plugins.scala.util.TestUtils
import org.jetbrains.sbt.project.SbtExternalSystemImportingTestLike
import org.junit.Assert.{assertEquals, fail}
import org.junit.experimental.categories.Category

import scala.collection.mutable

@Category(Array(classOf[SlowTests]))
class FindUsagesInSbtBuildModuleTest extends SbtExternalSystemImportingTestLike {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/findUsages/sbt_projects/${getTestName(true)}"

  protected def defaultOptions = new ScalaTypeDefinitionFindUsagesOptions(getMyTestFixture.getProject)

  override def setUp(): Unit = {
    super.setUp()
    importProject(false)
  }

  //https://youtrack.jetbrains.com/issue/SCL-8698/Find-usages-doesnt-search-inside-build.sbt
  def testFindUsageOfDefinitionInBuildModuleScalaFileInBuildSbtFile(): Unit = {
    val psiFile = TestUtils.findFileInProject(getMyProject, "project/BuildCommons.scala")
    assertDefinitionFoundInFiles(psiFile, "myLibraryVersion1", Seq("build.sbt", "other.sbt", "project/BuildUtils.scala"))
    assertDefinitionFoundInFiles(psiFile, "myLibraryVersion2", Seq("build.sbt", "other.sbt", "project/BuildUtils.scala"))
  }

  private def assertDefinitionFoundInFiles(psiFile: PsiFile, definitionName: String, expectedFoundFileNames: Seq[String]): Unit = {
    val namedElement = findNamedElement(psiFile, definitionName)
    val usages = findUsages(namedElement)
    val foundFiles = usages.map(_.getVirtualFile)
    val actualFoundFileNames = foundFiles.map(relativePath(TestUtils.guessProjectDir(getMyProject), _)).sorted
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
