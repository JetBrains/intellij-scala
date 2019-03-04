package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass

/**
 * @author ilyas
 */
trait ScalaFile extends ScalaPsiElement
  with ScFile
  with ScImportsHolder {

  @Deprecated
  def importClass(aClass: PsiClass): Boolean = {
    addImportForClass(aClass)
    true
  }

  def firstPackaging: Option[toplevel.ScPackaging]

  def typeDefinitions: Seq[toplevel.typedef.ScTypeDefinition]

  def packagingRanges: Seq[TextRange]

  def isScriptFile: Boolean = isScriptFileImpl

  def isScriptFileImpl: Boolean

  def isWorksheetFile: Boolean

  def allowsForwardReferences: Boolean
}