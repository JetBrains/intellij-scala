package org.jetbrains.bsp.protocol

import ch.epfl.scala.bsp4j
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.externalSystem.model.task.{ExternalSystemTaskId, ExternalSystemTaskNotificationListener, ExternalSystemTaskType}
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.testFramework.PlatformTestUtil
import org.jetbrains.bsp.{BSP, SbtOverBspExternalSystemImportingTestCase}
import org.jetbrains.plugins.scala.SlowTests2
import org.jetbrains.plugins.scala.util.TestUtils
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.experimental.categories.Category

import java.util.concurrent.{CompletableFuture, CountDownLatch, TimeUnit}

/**
 * Verifies that a `DidChangeBuildTarget` BSP notification triggers a project reload.
 *
 * The notification is not sent by a real BSP server: it is injected artificially through the test-only
 * [[BspCommunication.simulateServerNotificationForTest]] hook.
 */
@Category(Array(classOf[SlowTests2]))
class BspDidChangeNotificationTest extends SbtOverBspExternalSystemImportingTestCase {

  override protected def getTestDataProjectPath: String =
    s"${TestUtils.getTestDataPath}/sbt/projects/simple"

  def testDidChangeNotificationTriggersProjectReload(): Unit = {
    importProject(false)

    val comm = BspCommunication.forWorkspace(getTestProjectPath, getMyProject)
    assertTrue("Expected a live BSP session after import", comm.alive)

    // 1. Create a latch to monitor when the import triggered by DidChangeBuildTarget has completed successfully
    val reimportFinished = new CountDownLatch(1)
    val reimportListener = new ExternalSystemTaskNotificationListener {
      override def onTaskOutput(id: ExternalSystemTaskId, text: String, outputType: ProcessOutputType): Unit = ()

      override def onEnd(projectPath: String, id: ExternalSystemTaskId): Unit =
        if id.getProjectSystemId == BSP.ProjectSystemId && id.getType == ExternalSystemTaskType.RESOLVE_PROJECT then
          reimportFinished.countDown()
    }
    ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(reimportListener, getMyProject)

    // 2. On a pooled thread, send the DidChangeBuildTarget notification, the same way the BSP server would
    val result = new CompletableFuture[Void]()
    ApplicationManager.getApplication.executeOnPooledThread(() => {
      val didChange = new bsp4j.DidChangeBuildTarget(java.util.Collections.emptyList())
      comm.simulateServerNotificationForTest(BspNotifications.DidChangeBuildTarget(didChange))
      result.complete(null)
    })
    PlatformTestUtil.waitForFuture(result, 60000)

    // 3. Wait for the import to finish and verify that the project callback was run
    assertEquals("DidChangeBuildTarget should trigger exactly one project reload", 1, comm.projectCallbackInvocationCount.get())
    val reimportCompleted = reimportFinished.await(60, TimeUnit.SECONDS)
    assertTrue("Expected the project reload to finish within 60s", reimportCompleted)
  }
}
