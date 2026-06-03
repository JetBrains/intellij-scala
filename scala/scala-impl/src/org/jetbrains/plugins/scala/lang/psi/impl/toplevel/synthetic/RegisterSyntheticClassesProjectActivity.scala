package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.{DumbModeTask, DumbService, Project}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.startup.ProjectActivity

/**
 * Implementation note:<br>
 * Under the hood this activity uses `DumbService.queueTask(DumbModeTask)` to run the synthetic class registration.
 * The IDE can't be in smart mode until we register those classes as highlighting depends on it.
 *
 * As an alternatie I tried using [[com.intellij.openapi.startup.StartupActivity.RequiredForSmartMode]] locally.
 * During local experiments `RequiredForSmartMode` approach was ~ x2 slower during project opening on IDE startup.
 * (ProjectActivity ~ 400ms, RequiredForSmartMode ~900ms)<br>
 * My guess is that `RequiredForSmartMode` instances are run earlier than `ProjectActivity` and not all classes are loaded at that time.
 * So some time is spent on class loading
 */
final class RegisterSyntheticClassesProjectActivity extends ProjectActivity {

  override def execute(project: Project): Unit = {
    // See current class ScalaDoc for details
    DumbService.getInstance(project).queueTask(new DumbModeTask() {
      override def performInDumbMode(indicator: ProgressIndicator): Unit = {
        registerClassesBlocking(project)
      }
    })
  }

  // We run the registration on EDT to speed up the light tests initialisation.
  //
  // Details:
  // Under the hood `SyntheticClasses.registerClasses` requires a read lock in many places.
  // It's expected by the platform when creating synthetic files and when traversing AST (which is done during synthetic files registration).
  // If we run the method as is from the background thread in tests, this can drastically slow down light-test startup.
  // The slowdown is caused by frequent `ProgressManager.checkCanceled()` calls during parsing.
  // On a pooled thread, this can trigger `CoreProgressManager.sleepIfNeededToGivePriorityToAnotherThread`.
  // So if the current thread is not prioritized, the background task can be postponed for some time.
  // Historically, this inflated light test startup from ~0.1s to ~12s on Windows.
  // As a workaround we run this on EDT
  // !!! DISCLAIMER !!! I haven't rechecked how actual this is in 2026.
  // TODO: verify how actual it is in 2026
  private def registerClassesBlocking(project: Project): Unit = {
    if (ApplicationManager.getApplication.isUnitTestMode) {
      invokeAndWait {
        registerClasses(project)
      }
    } else {
      registerClasses(project)
    }
  }

  private def registerClasses(project: Project): Unit = {
    SyntheticClasses.get(project).registerClasses()
  }
}
