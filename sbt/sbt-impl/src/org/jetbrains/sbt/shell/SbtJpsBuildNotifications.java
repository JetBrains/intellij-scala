package org.jetbrains.sbt.shell;

import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileStatusNotification;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.task.ProjectTaskRunner;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.plugins.scala.build.TaskRunnerResult;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;

/**
 * Collector/notification helpers copied from:<br>
 * {@code com.intellij.task.impl.JpsProjectTaskRunner.MyNotificationCollector} and<br>
 * {@code com.intellij.task.impl.JpsProjectTaskRunner.MyCompileStatusNotification}.
 * <p>
 * Differences and commented-out JPS parts:
 * <ul>
 *   <li>JPS {@code ProjectTaskContext} + {@code JPS_BUILD_DATA_KEY} updates are commented out because this sbt hybrid
 *   runner does not populate/use JPS internal compatibility data (see investigation around {@code JpsBuildData}).</li>
 *   <li>JPS tracer span lines are commented out because this utility is intentionally focused on behavior parity for
 *   completion/error aggregation and callback handling only.</li>
 * </ul>
 */
final class SbtJpsBuildNotifications {

  private SbtJpsBuildNotifications() {
  }

  static final class MyNotificationCollector implements AutoCloseable {
    private static final ProjectTaskRunner.Result FAILED_AND_ABORTED = new ProjectTaskRunner.Result() {
      @Override
      public boolean isAborted() {
        return true;
      }

      @Override
      public boolean hasErrors() {
        return true;
      }
    };

    private final Logger myLog;
    private final AsyncPromise<ProjectTaskRunner.Result> myPromise;
    private boolean myCollectingStopped;

    private final Set<MyCompileStatusNotification> myNotifications = new ReferenceOpenHashSet<>();
    private int myErrors;
    private boolean myAborted;

    MyNotificationCollector(Logger log, AsyncPromise<ProjectTaskRunner.Result> promise) {
      myLog = log;
      myPromise = promise;
    }

    @Override
    public synchronized void close() {
      if (!myCollectingStopped) {
        myCollectingStopped = true;
        notifyFinished();
      }
    }

    private void notifyFinished() {
      if (myCollectingStopped && myNotifications.isEmpty()) {
        myPromise.setResult(myAborted && myErrors > 0 ? FAILED_AND_ABORTED :
          myAborted ? new TaskRunnerResult(true, false) :
            myErrors > 0 ? new TaskRunnerResult(false, true) :
              new TaskRunnerResult(false, false));
      }
    }

    private synchronized void appendJpsBuildResult(boolean aborted,
                                                   int errors,
                                                   CompileContext compileContext,
                                                   MyCompileStatusNotification notification) {
      final boolean notificationRemoved = myNotifications.remove(notification);
      if (!notificationRemoved) {
        myLog.error("Multiple invocation of the same callback");
      }
      myErrors += errors;
      if (aborted) myAborted = true;

      // JPS original code:
      // MyJpsBuildData jpsBuildData = (MyJpsBuildData)JPS_BUILD_DATA_KEY.get(myContext);
      // jpsBuildData.add(compileContext);
      //
      // Not applicable here:
      // this sbt-hybrid path does not aggregate JPS_BUILD_DATA / CompileContext compatibility data.

      if (notificationRemoved) {
        notifyFinished();
      }
    }

    synchronized void add(MyCompileStatusNotification notification) {
      assert !myCollectingStopped;
      if (!myNotifications.add(notification)) {
        myLog.error("Do not use the same callback for different JPS invocations");
      }
    }

    Logger logger() {
      return myLog;
    }
  }

  public static final class MyCompileStatusNotification implements CompileStatusNotification {
    private final Logger myLog;
    private final MyNotificationCollector myCollector;
    private final AtomicBoolean finished = new AtomicBoolean();
    // private final Tracer.Span mySpan = Tracer.start("jps task"); // which?

    public MyCompileStatusNotification(MyNotificationCollector collector) {
      myCollector = collector;
      myLog = collector.logger();
      myCollector.add(this);
    }

    @Override
    public void finished(boolean aborted, int errors, int warnings, CompileContext compileContext) {
      if (finished.compareAndSet(false, true)) {
        myLog.debug("Finished JPS artifact batch: aborted=" + aborted + ", errors=" + errors + ", warnings=" + warnings);
        myCollector.appendJpsBuildResult(aborted, errors, compileContext, this);
        // mySpan.complete();
      }
      else {
        // can be invoked by CompileDriver for rerun action
        myLog.debug("Multiple invocation of the same CompileStatusNotification.");
      }
    }
  }
}
