package org.jetbrains.plugins.scala.project.migration.api

/**
  * User: Dmitry.Naydanov
  * Date: 07.09.16.
  */
case class SettingsDescriptor(checkBoxes: Iterable[(String, Boolean)], 
                              comboBoxes: Iterable[(String, Iterable[String])], textFields: Iterable[(String, String)])

object SettingsDescriptor {
  val EMPTY_SETTINGS = SettingsDescriptor(Iterable.empty, Iterable.empty, Iterable.empty)
}