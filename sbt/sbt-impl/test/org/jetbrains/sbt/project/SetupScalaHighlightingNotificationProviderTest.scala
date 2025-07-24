package org.jetbrains.sbt.project

import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.openapi.vfs.{VfsUtil, VirtualFile}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.search.ProjectScope
import com.intellij.psi.{JavaPsiFacade, PsiFile, PsiManager}
import com.intellij.ui.EditorNotificationProvider
import junit.framework.TestCase.{assertFalse, assertNotNull, assertNull, assertTrue}
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.extensions.PathExt
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.experimental.categories.Category

import java.nio.file.Path

@Category(Array(classOf[SlowTests2]))
class SetupScalaHighlightingNotificationProviderTest extends SbtExternalSystemImportingTestLike {
  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/setupScalaHighlightingNotificationProvider"

  override protected def projectJdkLanguageLevel: LanguageLevel = LanguageLevel.JDK_17

  override def setUp(): Unit = {
    super.setUp()
    SbtProjectResolver.processOutputOfLatestStructureDump = ""
    SbtCachesSetupUtil.setupCoursierAndIvyCache(getProject)
  }

  def testSetupScalaHighlighting(): Unit = {
    val notificationProvider = EditorNotificationProvider.EP_NAME.findExtensionOrFail(classOf[SetupScalaHighlightingNotificationProvider], getProject)

    val greeterPath = getTestProjectPath / "module1" / "src" / "main" / "java" / "Greeter.java"
    val abstractGreeterPath = getTestProjectPath / "module1" / "src" / "main" / "kotlin" / "AbstractGreeter.kt"
    val helloWorldGreeterPath = getTestProjectPath / "module2" / "src" / "main" / "scala" / "HelloWorldGreeter.scala"

    val greeterPsiFileBefore = findPsiFile(greeterPath)
    val abstractGreeterPsiFileBefore = findPsiFile(abstractGreeterPath)
    val helloWorldGreeterPsiFileBefore = findPsiFile(helloWorldGreeterPath)

    val shouldHighlightGreeterBefore = ProblemHighlightFilter.shouldHighlightFile(greeterPsiFileBefore)
    val shouldHighlightAbstractGreeterBefore = ProblemHighlightFilter.shouldHighlightFile(abstractGreeterPsiFileBefore)
    val shouldHighlightHelloWorldGreeterBefore = ProblemHighlightFilter.shouldHighlightFile(helloWorldGreeterPsiFileBefore)

    assertFalse("Greeter.java should not be highlighted before the project has been imported", shouldHighlightGreeterBefore)
    assertFalse("AbstractGreeter.kt should not be highlighted before the project has been imported", shouldHighlightAbstractGreeterBefore)
    assertFalse("HelloWorldGreeter.scala should not be highlighted before the project has been imported", shouldHighlightHelloWorldGreeterBefore)

    importProject(false)

    val greeterPsiFileAfter = findPsiFile(greeterPath)
    val abstractGreeterPsiFileAfter = findPsiFile(abstractGreeterPath)
    val helloWorldGreeterPsiFileAfter = findPsiFile(helloWorldGreeterPath)

    val shouldHighlightGreeterAfter = ProblemHighlightFilter.shouldHighlightFile(greeterPsiFileAfter)
    val shouldHighlightAbstractGreeterAfter = ProblemHighlightFilter.shouldHighlightFile(abstractGreeterPsiFileAfter)
    val shouldHighlightHelloWorldGreeterAfter = ProblemHighlightFilter.shouldHighlightFile(helloWorldGreeterPsiFileAfter)

    val notificationBannerGreeterAfter = notificationProvider.collectNotificationData(getProject, greeterPsiFileBefore.getVirtualFile)
    val notificationBannerAbstractGreeterAfter = notificationProvider.collectNotificationData(getProject, abstractGreeterPsiFileBefore.getVirtualFile)
    val notificationBannerHelloWorldGreeterAfter = notificationProvider.collectNotificationData(getProject, helloWorldGreeterPsiFileBefore.getVirtualFile)

    assertTrue("Greeter.java should be highlighted after the project has been imported", shouldHighlightGreeterAfter)
    assertTrue("AbstractGreeter.kt should be highlighted after the project has been imported", shouldHighlightAbstractGreeterAfter)
    assertTrue("HelloWorldGreeter.scala should be highlighted after the project has been imported", shouldHighlightHelloWorldGreeterAfter)
    assertTrue("The sbt project should have been imported (Greeter.java)", SbtProjectImportStateService.instance(getProject).isImported(greeterPsiFileAfter))
    assertTrue("The sbt project should have been imported (AbstractGreeter.kt)", SbtProjectImportStateService.instance(getProject).isImported(abstractGreeterPsiFileAfter))
    assertTrue("The sbt project should have been imported (HelloWorldGreeter.scala)", SbtProjectImportStateService.instance(getProject).isImported(helloWorldGreeterPsiFileAfter))
    assertNull("A notification banner should not be shown in Greeter.java after the project has been imported", notificationBannerGreeterAfter)
    assertNull("A notification banner should not be shown in AbstractGreeter.kt after the project has been imported", notificationBannerAbstractGreeterAfter)
    assertNull("A notification banner should not be shown in HelloWorldGreeter.scala after the project has been imported", notificationBannerHelloWorldGreeterAfter)

    val scalaListPsiFileAfter = findPsiFileForLibraryClass("scala.collection.immutable.List")
    val cNodeBasePsiFileAfter = findPsiFileForLibraryClass("scala.collection.concurrent.CNodeBase")

    val shouldHighlightScalaListAfter = ProblemHighlightFilter.shouldHighlightFile(scalaListPsiFileAfter)
    val shouldHighlightCNodeBaseAfter = ProblemHighlightFilter.shouldHighlightFile(cNodeBasePsiFileAfter)

    val notificationBannerScalaListAfter = notificationProvider.collectNotificationData(getProject, scalaListPsiFileAfter.getVirtualFile)
    val notificationBannerCNodeBaseAfter = notificationProvider.collectNotificationData(getProject, cNodeBasePsiFileAfter.getVirtualFile)

    assertTrue("List.scala should be highlighted after the project has been imported", shouldHighlightScalaListAfter)
    assertTrue("CNodeBase.java should be highlighted after the project has been imported", shouldHighlightCNodeBaseAfter)
    assertTrue("The sbt project should have been imported (List.scala)", SbtProjectImportStateService.instance(getProject).isImported(scalaListPsiFileAfter))
    assertTrue("The sbt project should have been imported (CNodeBase.java)", SbtProjectImportStateService.instance(getProject).isImported(cNodeBasePsiFileAfter))
    assertNull("A notification banner should not be shown in List.scala after the project has been imported", notificationBannerScalaListAfter)
    assertNull("A notification banner should not be shown in CNodeBase.java after the project has been imported", notificationBannerCNodeBaseAfter)
  }

  private def findPsiFile(path: Path): PsiFile = {
    val virtualFile = findVirtualFile(path)
    val manager = PsiManager.getInstance(getProject)
    val psiFile = manager.findFile(virtualFile)
    assertNotNull(s"Could not find psi file for virtual file: $virtualFile", psiFile)
    psiFile
  }

  private def findVirtualFile(path: Path): VirtualFile = {
    // It's necessary to refresh the virtual file system to get the up-to-date VirtualFile/PsiFile instances before
    // and after project import.
    val virtualFile = VfsUtil.findFile(path, true)
    assertNotNull(s"Could not find virtual file for path: $path", virtualFile)
    virtualFile
  }

  private def findPsiFileForLibraryClass(fqn: String): PsiFile = {
    val facade = JavaPsiFacade.getInstance(getProject)
    val cls = facade.findClass(fqn, ProjectScope.getLibrariesScope(getProject))
    assertNotNull(s"Could not find class: $fqn", cls)
    val file = cls.getContainingFile
    assertNotNull(s"Could not find containing file for class: $fqn", file)
    file
  }
}
