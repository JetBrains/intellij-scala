package org.jetbrains.plugins.scala.compiler

import java.io.File

import com.intellij.openapi.module.Module
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.scala.ExtensionPointDeclaration
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.TopComponentAction

/**
 * NOTE: do not use in external plugins, due `extraWorksheetActions` results
 * are registered in Worksheet top panel UI, and effectively the EP is not dynamic
 */
@ApiStatus.Internal
trait WorksheetCompilerExtension {
  def worksheetClasspath(module: Module): Option[Seq[File]]
  def extraWorksheetActions(): Seq[TopComponentAction]
}

object WorksheetCompilerExtension
  extends ExtensionPointDeclaration[WorksheetCompilerExtension](
    "org.intellij.scala.worksheetCompilerExtension"
  ) {

  def worksheetClasspath(module: Module): Option[Seq[File]] =
    implementations.iterator.map(_.worksheetClasspath(module)).collectFirst { case Some(cp) => cp }

  def extraWorksheetActions(): collection.Seq[TopComponentAction] =
    implementations.flatMap(_.extraWorksheetActions())
}
