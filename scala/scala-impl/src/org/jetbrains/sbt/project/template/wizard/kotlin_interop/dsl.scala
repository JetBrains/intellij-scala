package org.jetbrains.sbt.project.template.wizard.kotlin_interop

import com.intellij.ui.dsl.builder.{Cell, Row}

import javax.swing.JComponent

//noinspection ApiStatus,UnstableApiUsage
object dsl {
  implicit class RowOps(val row: Row) extends AnyVal {
    def cell[T <: JComponent](component: T): Cell[T] = row.cell(component, component)
  }
}
