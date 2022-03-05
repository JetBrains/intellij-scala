package org.jetbrains.plugins.scala.editor.autoimport

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
    def checkBoxCell(@Nls text: String, getter: => Boolean, setter: Boolean => Unit): Row = {
      panel.row(null: JLabel, (row: Row) => {
        row.checkBox(text).bindSelected(getter, setter)
        KUnit
      })
    }
  }
}
