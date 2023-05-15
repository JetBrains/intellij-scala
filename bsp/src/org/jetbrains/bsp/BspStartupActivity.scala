package org.jetbrains.bsp

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.util.JavaCoroutines
import kotlin.coroutines.Continuation
import org.jetbrains.plugins.scala.extensions.executeOnPooledThread

final class BspStartupActivity extends ProjectActivity {
  override def execute(project: Project, continuation: Continuation[_ >: kotlin.Unit]): AnyRef = {
    JavaCoroutines.suspendJava[kotlin.Unit](jc => {
      // initialize build loop only for bsp projects
      if (BspUtil.isBspProject(project)) {
        BspBuildLoopService.getInstance(project)
      }
      jc.resume(kotlin.Unit.INSTANCE)
    }, continuation)
  }
}
