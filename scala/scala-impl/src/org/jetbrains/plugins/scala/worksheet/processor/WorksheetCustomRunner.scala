package org.jetbrains.plugins.scala.worksheet.processor

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.scala.worksheet.processor.WorksheetCompilerUtil.RunCustom

/**
  * User: Dmitry.Naydanov
  * Date: 16.07.18.
  */
abstract class WorksheetCustomRunner {
  def canHandle(request: RunCustom): Boolean
  def handle(request: RunCustom): Unit
}

object WorksheetCustomRunner {
  val EP_NAME: ExtensionPointName[WorksheetCustomRunner] = 
    ExtensionPointName.create[WorksheetCustomRunner]("org.intellij.scala.worksheetCustomRunner")
  
  def getAllCustomRunners: Array[WorksheetCustomRunner] = EP_NAME.getExtensions
  
  def findSuitableRunnerFor(request: RunCustom): Option[WorksheetCustomRunner] = getAllCustomRunners.find(_ canHandle request)
}
