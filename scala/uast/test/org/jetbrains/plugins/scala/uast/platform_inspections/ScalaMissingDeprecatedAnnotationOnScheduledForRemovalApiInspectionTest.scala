package org.jetbrains.plugins.scala.uast.platform_inspections

import com.intellij.codeInspection.MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection
import com.intellij.openapi.roots.{ModifiableRootModel, ModuleRootModificationUtil}
import com.intellij.testFramework.PsiTestUtil
import com.intellij.util.PathUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

import java.util.Arrays.asList

class ScalaMissingDeprecatedAnnotationOnScheduledForRemovalApiInspectionTest extends ScalaInspectionTestBase {

  override protected val classOfInspection = classOf[MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection]
  override protected val description = "Scheduled for removal API must also be marked with '@Deprecated' annotation"

  override def setUpLibraries(implicit module: com.intellij.openapi.module.Module): Unit = {
    super.setUpLibraries

    ModuleRootModificationUtil.updateModel(module, (model: ModifiableRootModel) => {
      val rootPaths = asList(PathUtil.getJarPathForClass(classOf[ApiStatus.ScheduledForRemoval]))
      PsiTestUtil.addProjectLibrary(model, "annotations", rootPaths)
    })
  }

  def testHighlighting(): Unit = checkTextHasError(
    s"""
       |import org.jetbrains.annotations.ApiStatus
       |
       |@ApiStatus.ScheduledForRemoval
       |class ${START}Warnings$END {
       |
       |  @ApiStatus.ScheduledForRemoval
       |  var ${START}field1$END: String = _
       |
       |  @ApiStatus.ScheduledForRemoval
       |  val ${START}field2$END: Int = 0
       |
       |  @ApiStatus.ScheduledForRemoval
       |  def ${START}method$END(): Unit = {
       |  }
       |
       |  @ApiStatus.ScheduledForRemoval
       |  trait ${START}Trait$END {
       |  }
       |
       |  @ApiStatus.ScheduledForRemoval
       |  case class ${START}CaseClass$END()
       |
       |  @ApiStatus.ScheduledForRemoval
       |  object ${START}Object$END {
       |  }
       |
       |  class Foo(@ApiStatus.ScheduledForRemoval val ${START}f$END: String)
       |}
       |""".stripMargin
  )

  def testNoHighlighting(): Unit = checkTextHasNoErrors(
    """
      |import org.jetbrains.annotations.ApiStatus
      |
      |@ApiStatus.ScheduledForRemoval
      |@Deprecated
      |class Warnings {
      |
      |  @ApiStatus.ScheduledForRemoval
      |  @Deprecated
      |  var field1: String = _
      |
      |  @ApiStatus.ScheduledForRemoval
      |  @Deprecated
      |  val field2: Int = 0
      |
      |  @ApiStatus.ScheduledForRemoval
      |  @Deprecated
      |  def method(): Unit = {
      |  }
      |
      |  @ApiStatus.ScheduledForRemoval
      |  @Deprecated
      |  trait Trait {
      |  }
      |
      |  @ApiStatus.ScheduledForRemoval
      |  @Deprecated
      |  case class CaseClass()
      |
      |  @ApiStatus.ScheduledForRemoval
      |  @Deprecated
      |  object Object {
      |  }
      |
      |  class Foo(@ApiStatus.ScheduledForRemoval @Deprecated val f: String)
      |}
      |""".stripMargin
  )

  // TODO: Fix highlighting and add tests for case with scala @deprecated annotation
}
