package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.synthetic

import com.intellij.openapi.project.{Project, ProjectManagerListener}

class DeregisterSyntheticClassesListener extends ProjectManagerListener {
  override def projectClosing(project: Project): Unit = {
    SyntheticClasses.get(project).clear()
  }
}
