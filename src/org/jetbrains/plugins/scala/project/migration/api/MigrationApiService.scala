package org.jetbrains.plugins.scala.project.migration.api

import com.intellij.openapi.project.Project

/**
  * User: Dmitry.Naydanov
  * Date: 01.09.16.
  */
trait MigrationApiService {
  def showPopup(txt: String, title: String, handler: (String) => (Unit))
  
  def showDialog(title: String, text: String, settingsDescriptor: SettingsDescriptor, 
                 onOk: (SettingsDescriptor) => Unit, onCancel: => Unit)
  
  def showReport(report: MigrationReport)
  
  def getProject: Project
  
  def inReadAction[T](action: => T): T
  
  def inWriteAction[T](action: => T): T
  
  def onEdt(action: => Unit): Unit
}
