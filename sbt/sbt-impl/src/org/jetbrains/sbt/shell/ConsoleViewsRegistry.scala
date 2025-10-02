package org.jetbrains.sbt.shell

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer

import scala.collection.mutable

/**
 * Keeps track of the last console view per project and ensures the previous one is disposed
 * when a new console is set. Works with any console implementation (SbtShellConsoleView/TerminalExecutionConsole).
 */
object ConsoleViewsRegistry {
  private val lastConsoleViews: mutable.Map[Project, Disposable] = mutable.HashMap.empty

  /**
   * Disposes and removes the last console view for the given project, if any.
   */
  def disposeLast(project: Project): Unit = synchronized {
    lastConsoleViews.get(project).foreach(Disposer.dispose)
    removeConsoleView(project)
  }

  /**
   * Registers the new console for the project.
   * If there was a previous console, it will be disposed first.
   */
  def set(project: Project, console: Disposable): Unit = synchronized {
    disposeLast(project)
    lastConsoleViews.put(project, console)
  }
  
  def removeConsoleView(project: Project): Unit = 
    lastConsoleViews.remove(project)
}
