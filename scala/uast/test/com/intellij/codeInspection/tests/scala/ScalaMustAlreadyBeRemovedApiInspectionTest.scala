package com.intellij.codeInspection.tests.scala

import com.intellij.codeInspection.MustAlreadyBeRemovedApiInspection
import com.intellij.openapi.roots.{ModifiableRootModel, ModuleRootModificationUtil}
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.base.ScalaLightCodeInsightFixtureTestCase

import java.util.Arrays.asList

class ScalaMustAlreadyBeRemovedApiInspectionTest extends ScalaLightCodeInsightFixtureTestCase {
  private val pastVersion = "2.3"
  private val currentVersion = "3.0"
  private val futureVersion = "3.2"

  override protected def setUp(): Unit = {
    super.setUp()
    val inspection = new MustAlreadyBeRemovedApiInspection
    inspection.setCurrentVersion(currentVersion)
    myFixture.enableInspections(inspection)
  }

  override def setUpLibraries(implicit module: com.intellij.openapi.module.Module): Unit = {
    super.setUpLibraries

    ModuleRootModificationUtil.updateModel(module, (model: ModifiableRootModel) => {
      val rootPaths = asList(PathUtil.getJarPathForClass(classOf[ApiStatus.ScheduledForRemoval]))
      PsiTestUtil.addProjectLibrary(model, "annotations", rootPaths)
    })
  }

  def testHighlighting(): Unit = {
    val fileText =
      s"""
         |import org.jetbrains.annotations.ApiStatus
         |
         |@Deprecated
         |@ApiStatus.ScheduledForRemoval(inVersion = "$pastVersion")
         |class ${withRemoveInPastVersionError("Warnings")} {
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$pastVersion")
         |  var ${withRemoveInPastVersionError("field1")}: String = _
         |
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$currentVersion")
         |  val ${withRemoveInCurrentVersionError("field2")}: Int = 0
         |
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$pastVersion")
         |  def ${withRemoveInPastVersionError("method")}(): Unit = {
         |  }
         |
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$currentVersion")
         |  trait ${withRemoveInCurrentVersionError("Trait")} {
         |  }
         |
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$currentVersion")
         |  case class ${withRemoveInCurrentVersionError("CaseClass")}()
         |
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$pastVersion")
         |  object ${withRemoveInPastVersionError("Object")} {
         |  }
         |}
         |
         |//No warnings should be produced.
         |
         |@Deprecated
         |@ApiStatus.ScheduledForRemoval(inVersion = "$futureVersion")
         |class NoWarnings {
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$futureVersion")
         |  var field1: String = _
         |
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$futureVersion")
         |  val field2: Int = 0
         |
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$futureVersion")
         |  def method(): Unit = {
         |  }
         |
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$futureVersion")
         |  trait Trait {
         |  }
         |
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$futureVersion")
         |  case class CaseClass()
         |
         |  @Deprecated
         |  @ApiStatus.ScheduledForRemoval(inVersion = "$futureVersion")
         |  object Object {
         |  }
         |}
         |""".stripMargin

    configureFromFileText(fileText)
    myFixture.testHighlighting()
  }

  private def withError(text: String, description: String) =
    s"""<error descr="$description">$text</error>"""

  private def withRemoveInCurrentVersionError(text: String): String =
    withError(text, s"API must be removed in the current version $currentVersion")

  private def withRemoveInPastVersionError(text: String): String =
    withError(text, s"API must have been removed in version $pastVersion but the current version is $currentVersion")
}
