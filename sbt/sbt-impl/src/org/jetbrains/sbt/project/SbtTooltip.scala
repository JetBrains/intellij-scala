package org.jetbrains.sbt.project

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.impl.ActionButtonUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.impl.{SquareStripeButton, ToolWindowManagerImpl}
import com.intellij.openapi.wm.{ToolWindowAnchor, ToolWindowManager, WindowManager}
import com.intellij.ui.GotItTooltip

import java.awt.{Component, Point}

private object SbtTooltip {
  def findSbtToolWindowButton(project: Project): Option[SquareStripeButton] = {
    val frame = WindowManager.getInstance().getIdeFrame(project)
    if (frame == null) return None
    val component = frame.getComponent
    val maybeActionButton = ActionButtonUtil.findActionButton(component, {
      case button: SquareStripeButton => button.getToolWindow.getStripeTitle == "sbt"
      case _ => false
    })

    Option(maybeActionButton).collect { case button: SquareStripeButton => button }
  }

  //noinspection ApiStatus,UnstableApiUsage
  def findToolWindowManagerDisposable(project: Project): Option[Disposable] =
    Option(ToolWindowManager.getInstance(project))
      .collect { case x: ToolWindowManagerImpl => x }

  type PointProvider = kotlin.jvm.functions.Function2[Component, AnyRef, Point]

  def tooltipPointOfOrigin(button: SquareStripeButton): PointProvider =
    button.getToolWindow.getAnchor match {
      case ToolWindowAnchor.RIGHT => GotItTooltip.LEFT_MIDDLE
      case ToolWindowAnchor.LEFT => GotItTooltip.RIGHT_MIDDLE
      case ToolWindowAnchor.TOP => GotItTooltip.BOTTOM_MIDDLE
      case ToolWindowAnchor.BOTTOM =>
        // If `isSplitMode = true `, then the sbt icon is on the right side of the "bottom" toolbar.
        val isRight = button.getToolWindow.isSplitMode
        if (isRight) GotItTooltip.LEFT_MIDDLE else GotItTooltip.RIGHT_MIDDLE
    }
}
