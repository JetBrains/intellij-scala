package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions.{inReadAction, invokeLater, withProgressSynchronously}

final class RegisterSyntheticClassesStartupActivity extends StartupActivity.DumbAware {

  private val Log = Logger.getInstance(this.getClass)

  override def runActivity(project: Project): Unit = {
    // StartupActivity.DumbAware runs on a pooled thread.
    // However, `SyntheticClasses.registerClasses` creates/parses PSI and therefore needs read access in tons of the places under the hood.
    // We have to use different strategies for aquiring read access for tests and production.
    //
    // Unit tests:
    // Running this initialization in a background read action can drastically slow down light test startup.
    // The slowdown is caused by frequent `ProgressManager.checkCanceled()` calls during parsing.
    // On a pooled thread this can trigger `CoreProgressManager.sleepIfNeededToGivePriorityToAnotherThread`,
    // i.e., repeated priority sleeps while another thread is prioritized.
    // Historically, this inflated light test startup from ~0.1s to ~12s on Windows.
    // (Disclaimer: I haven't rechecked how actual this is in 2026)
    // So, instead of running it in a background read action, we run it on EDT - we can afford that in tests.
    //
    // Production:
    // Running this work on EDT can freeze the UI (`SCL-25281`), so we keep it off EDT and wrap it into synchronous progress.
    // This shows a modal progress dialog. This is not ideal, but it's better than having a freeze.
    // In optimistic cases (most?) this should be pretty fast, and users shouldn't notice the modal dialog.
    // However, the freeze reports from SCL-25281 contain 5, 11, 17-second freezes.
    // IDK how the heck it's possible that on some machenes it takes so long - the logic in `registerClasses` is not that heavy weight.
    //
    // An ideal fix for the issue would require major refactoring:
    // Remove heavy PSI parsing from startup initialization. Build lightweight precomputed descriptors for synthetic classes
    // and instantiate PSI lazily or in small chunks only when specific elements are actually requested.
    // This would avoid the current trade-off between background-read-action test slowdowns and EDT freezes.
    if (ApplicationManager.getApplication.isUnitTestMode) {
      invokeLater {
        registerClasses(project)
      }
    } else {
      withProgressSynchronously(ScalaBundle.message("registering.synthetic.scala.library.classes")) {
        inReadAction {
          registerClasses(project)
        }
      }
    }
  }

  private def registerClasses(project: Project): Unit = {
    logActivityDuration("synthetic classes registration") {
      SyntheticClasses.get(project).registerClasses()
    }
  }

  private def logActivityDuration(activityName: String)(body: => Unit): Unit = {
    val timeBefore = System.currentTimeMillis()

    body

    val timeAfter = System.currentTimeMillis()
    val timeSpent = timeAfter - timeBefore

    // This is supposed to be logged once per project. I guess it's not too spammy
    val message = s"$activityName took in ${timeSpent}ms"
    val tookTooLong = timeSpent > 1000
    if (tookTooLong)
      Log.warn(message)
    else
      Log.info(message)
  }
}
