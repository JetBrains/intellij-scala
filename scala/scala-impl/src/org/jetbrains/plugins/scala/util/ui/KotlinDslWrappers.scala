package org.jetbrains.plugins.scala.util.ui

import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder._
import kotlin.Unit.{INSTANCE => KUnit}
import org.jetbrains.annotations.Nls

import java.lang
import javax.swing.{AbstractButton, JLabel}

object KotlinDslWrappers {

  def mutableProperty[T](getter: => T)(setter: T => Unit): MutableProperty[T] = new MutableProperty[T] {
    override def get(): T = getter

    override def set(t: T): Unit = setter(t)
  }

  implicit class CellExt[T <: AbstractButton](private val cell: Cell[T]) extends AnyVal {
    def bindSelected(getter: => Boolean, setter: Boolean => Unit): Cell[T] = {
      ButtonKt.bindSelected(
        cell,
        () => getter,
        (value: lang.Boolean) => {
          setter(value)
          KUnit
        }
      )
    }
  }

  implicit class PanelOps(private val panel: Panel) extends AnyVal {
    def checkBoxCell(@Nls text: String, getter: => Boolean, setter: Boolean => Unit): Row =
      panel.row(null: JLabel, (row: Row) => {
        row.checkBox(text).bindSelected(getter, setter)
        KUnit
      })

    def checkBoxCellWithTooltip(@Nls text: String, @NlsContexts.Tooltip tooltip: String,
                                getter: => Boolean, setter: Boolean => Unit): Row = {
      panel.row(null: JLabel, (row: Row) => {
        row.checkBox(text)
          .bindSelected(getter, setter)
          .gap(RightGap.SMALL)
        row.contextHelp(tooltip, null)
        KUnit
      })
    }

    def buttonsGroup(@NlsContexts.Label title: String, indent: Boolean = true)(init: Panel => Unit): ButtonsGroup =
      panel.buttonsGroup(title, indent, (groupPanel: Panel) => {
        init(groupPanel)
        KUnit
      })

    def groupRowsRange(@NlsContexts.BorderTitle title: String, indent: Boolean = true)(init: Panel => Unit): RowsRange =
      panel.groupRowsRange(title, indent, false, false, (groupPanel: Panel) => {
        init(groupPanel)
        KUnit
      })
  }
}
