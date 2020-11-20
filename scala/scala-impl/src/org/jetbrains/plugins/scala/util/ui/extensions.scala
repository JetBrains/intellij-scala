package org.jetbrains.plugins.scala.util.ui

import java.awt.Component
import java.awt.event.{HierarchyEvent, HierarchyListener}
import java.util.concurrent.Future

import javax.swing.JComponent
import org.jetbrains.plugins.scala.extensions.invokeLater

object extensions {

  implicit class JComponentExt(private val target: JComponent) extends AnyVal {

    def components: Iterator[Component] =
      Iterator.tabulate(target.getComponentCount)(target.getComponent)
  }

  implicit class ComponentExt(private val component: Component)
    extends AnyVal {

    /**
     * Executes `startOp` if the component becomes visible.
     * Cancels the created future when the component is hidden.
     */
    def bindExecutionToVisibility(startOp: () => Future[_]): Unit =
      component.addHierarchyListener(new HierarchyListener {

        private var refreshFuture = Option.empty[Future[_]]

        invokeLater {
          startOrCancelScheduling()
        }

        override def hierarchyChanged(e: HierarchyEvent): Unit = {
          if (HierarchyEvent.SHOWING_CHANGED == (e.getChangeFlags & HierarchyEvent.SHOWING_CHANGED))
            startOrCancelScheduling()
        }

        private def startOrCancelScheduling(): Unit =
          if (component.isShowing) {
            refreshFuture = refreshFuture.orElse(Option(startOp()))
          } else {
            refreshFuture.foreach(_.cancel(true))
            refreshFuture = None
          }
      })
  }
}
