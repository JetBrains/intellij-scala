package org.jetbrains.plugins.scala.worksheet.cell

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiElement

/**
  * User: Dmitry.Naydanov
  * Date: 27.08.18.
  */
abstract class WorksheetCellExternalIdProvider {
  def canHandle(startElement: PsiElement): Boolean
  def getId(startElement: PsiElement): Option[String]
}

object WorksheetCellExternalIdProvider {
  val EP_NAME: ExtensionPointName[WorksheetCellExternalIdProvider] = 
    ExtensionPointName.create[WorksheetCellExternalIdProvider]("org.intellij.scala.worksheetCellExternalIdProvider")
  
  def getSuitable(startElement: PsiElement): Option[String] = 
    EP_NAME.getExtensions.find(_.canHandle(startElement)).flatMap(_.getId(startElement))
}
