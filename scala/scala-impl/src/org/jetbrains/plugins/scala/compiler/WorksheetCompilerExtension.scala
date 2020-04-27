package org.jetbrains.plugins.scala.compiler

import java.io.File

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderEnumerator
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.worksheet.actions.topmenu.TopComponentAction

trait WorksheetCompilerExtension {
  def worksheetClasspath(module: Module): Option[Seq[File]]
  def extraWorksheetActions(): Seq[TopComponentAction]
}

object WorksheetCompilerExtension {
  val EP_NAME: ExtensionPointName[WorksheetCompilerExtension] =
    ExtensionPointName.create("org.intellij.scala.worksheetCompilerExtension")

  def worksheetClasspath(module: Module): Seq[File] =
    EP_NAME.getExtensions.iterator.map(_.worksheetClasspath(module)).collectFirst {
      case Some(cp) => cp
    }.getOrElse {
      OrderEnumerator.orderEntries(module).compileOnly().getClassesRoots.map(_.toFile).toSeq
    }

  def extraWorksheetActions(): Seq[TopComponentAction] = {
    EP_NAME.getExtensions.flatMap(_.extraWorksheetActions())
  }
}
