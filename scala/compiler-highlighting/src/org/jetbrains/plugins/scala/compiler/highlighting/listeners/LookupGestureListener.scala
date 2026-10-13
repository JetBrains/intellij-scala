package org.jetbrains.plugins.scala.compiler.highlighting.listeners

import com.intellij.codeInsight.lookup.{Lookup, LookupManagerListener}
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.compiler.highlighting.services.TriggerService

/** Fires when the active lookup disappears without being replaced by another one. */
final class LookupGestureListener(project: Project) extends LookupManagerListener:
  override def activeLookupChanged(oldLookup: Lookup, newLookup: Lookup): Unit =
    if newLookup == null && oldLookup != null then
      TriggerService(project).schedule(oldLookup.getEditor.getVirtualFile, "Lookup changed")
