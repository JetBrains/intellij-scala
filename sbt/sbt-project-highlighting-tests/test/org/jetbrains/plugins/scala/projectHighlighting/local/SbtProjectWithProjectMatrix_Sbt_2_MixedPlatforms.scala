package org.jetbrains.plugins.scala.projectHighlighting.local

import org.jetbrains.plugins.scala.notifications.CollectingNotificationsListener
import org.jetbrains.sbt.project.SbtProjectMatrixTestUtils
import org.jetbrains.sbt.project.SbtProjectMatrixTestUtils.Fixture

abstract class SbtProjectWithProjectMatrix_Sbt_2_MixedPlatforms
  extends SbtProjectWithProjectMatrixAndSourceGenerators_Sbt_2_TestBase {

  protected def fixture: Fixture

  override def projectName: String = fixture.projectName

  def testProjectStructure(): Unit = {
    val notifications = CollectingNotificationsListener.subscribeOnWarningsAndErrors(getProject)
    importProject(false)
    assertProjectStructure(SbtProjectMatrixTestUtils.expectedProject(fixture), notifications)
  }
}

class SbtProjectWithProjectMatrix_Sbt_2_JvmAndJs
  extends SbtProjectWithProjectMatrix_Sbt_2_MixedPlatforms {
  override protected def fixture: Fixture = SbtProjectMatrixTestUtils.JvmAndJs
}

class SbtProjectWithProjectMatrix_Sbt_2_JsAndNative
  extends SbtProjectWithProjectMatrix_Sbt_2_MixedPlatforms {
  override protected def fixture: Fixture = SbtProjectMatrixTestUtils.JsAndNative
}

class SbtProjectWithProjectMatrix_Sbt_2_JvmAndJsAndNative
  extends SbtProjectWithProjectMatrix_Sbt_2_MixedPlatforms {
  override protected def fixture: Fixture = SbtProjectMatrixTestUtils.JvmAndJsAndNative
}

class SbtProjectWithProjectMatrix_Sbt_2_CrossVersionFull
  extends SbtProjectWithProjectMatrix_Sbt_2_MixedPlatforms {
  override protected def fixture: Fixture = SbtProjectMatrixTestUtils.CrossVersionFull
}
