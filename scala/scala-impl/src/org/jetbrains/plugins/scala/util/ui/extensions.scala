package org.jetbrains.plugins.scala.util.ui

import org.jetbrains.plugins.scala.extensions.invokeLater

import java.awt.Component
import java.awt.event.{HierarchyEvent, HierarchyListener}
import java.util.concurrent.Future
import javax.swing.{JComboBox, JComponent, JList}

object extensions {

  implicit class JComponentExt(private val target: JComponent) extends AnyVal {

    def components: Iterator[Component] =
      Iterator.tabulate(target.getComponentCount)(target.getComponent)
  }

  implicit class JComboBoxOps[E](private val box: JComboBox[E]) extends AnyVal {
    def items: Seq[E] = Seq.tabulate(box.getItemCount)(box.getItemAt)

    def setSelectedItemEnsuring(item: E): Unit = {
      val items = box.items
      if (!items.contains(item)) {
        throw new AssertionError(s"Cannot find '$item' in combo-box items: $items")
      }
      box.setSelectedItem(item)
    }
  }

  implicit class JListOps[E](private val list: JList[E]) extends AnyVal {
    def items: Seq[E] = {
      val model = list.getModel
      Seq.tabulate(model.getSize)(model.getElementAt)
    }
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
