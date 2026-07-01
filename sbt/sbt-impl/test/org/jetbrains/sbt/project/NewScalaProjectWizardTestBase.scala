package org.jetbrains.sbt.project

import com.intellij.ide.projectWizard.NewProjectWizardTestCase
import com.intellij.ide.wizard.NewProjectWizardBaseData.getBaseData
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.{JavaSdk, ProjectJdkTable}
import com.intellij.openapi.roots.{LanguageLevelProjectExtension, ProjectRootManager}
import com.intellij.testFramework.FixtureRuleKt.useProject
import com.intellij.testFramework.IndexingTestUtil
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.extensions.inWriteAction
import org.jetbrains.plugins.scala.ScalaVersion
import org.junit.Assert
import org.junit.Assert.assertNotNull
import org.junit.experimental.categories.Category

@Category(Array(classOf[SlowTests2]))
abstract class NewScalaProjectWizardTestBase extends NewProjectWizardTestCase {

  override def tearDown(): Unit = {
    inWriteAction {
      val projectJdkTable = ProjectJdkTable.getInstance()
      projectJdkTable.getAllJdks.foreach(projectJdkTable.removeJdk)
    }
    super.tearDown()
  }

  protected def createScalaProject(
    templateGroup: String,
    projectName: String,
    checkJDK: Boolean = true
  )(configureStep: NewProjectWizardStep => Unit): Project = {
    val project = createProjectFromTemplate(
      templateGroup,
      step => {
        getBaseData(step).setName(projectName)
        configureStep(step)
      }
    )

    assertNotNull(project)

    if (checkJDK) {
      val projectJdk = ProjectRootManager.getInstance(project).getProjectSdk
      assertNotNull(projectJdk)

      val jdkVersion = JavaSdk.getInstance.getVersion(projectJdk)
      assertNotNull(jdkVersion)
      Assert.assertEquals(jdkVersion.getMaxLanguageLevel, LanguageLevelProjectExtension.getInstance(project).getLanguageLevel)
    }

    new ProjectStructureAssertionsFixture(project).assertNoNotificationsShown()

    IndexingTestUtil.waitUntilIndexesAreReady(project)

    project
  }

  protected final def availableScalaVersionsFromWizard(
    templateGroup: String,
    projectName: String,
    checkJDK: Boolean = false,
  )(configureAndGetVersions: NewProjectWizardStep => Seq[String]): Seq[String] = {
    var availableScalaVersions = Seq.empty[String]
    val project = createScalaProject(templateGroup, projectName, checkJDK) { step =>
      availableScalaVersions = configureAndGetVersions(step)
    }

    useProject(project, false, (_: Project) => ())
    availableScalaVersions
  }

  protected final def assertUnsupportedScala3VersionsAreHidden(
    versions: Seq[String],
    minSupportedScala3Version: ScalaVersion,
  ): Unit = {
    val parsedVersions = versions.flatMap(version => ScalaVersion.fromString(version).map(version -> _))

    Assert.assertTrue(
      "Scala 2 versions should remain available",
      versions.exists(_.startsWith("2."))
    )
    Assert.assertTrue(
      s"${minSupportedScala3Version.minor} should remain available",
      versions.contains(minSupportedScala3Version.minor)
    )
    Assert.assertTrue(
      "Latest Scala 3.3.x should remain available",
      versions.contains(ScalaVersion.Latest.Scala_3_3.minor)
    )
    Assert.assertTrue(
      "Newer Scala 3 versions should remain available",
      versions.exists(_.startsWith("3.4."))
    )

    val unsupportedScala3Versions = parsedVersions.collect {
      case (versionString, version) if version.isScala3 && version < minSupportedScala3Version =>
        versionString
    }
    Assert.assertTrue(s"Unsupported Scala 3 versions should be hidden: ${unsupportedScala3Versions.mkString(", ")}", unsupportedScala3Versions.isEmpty)
  }
}
