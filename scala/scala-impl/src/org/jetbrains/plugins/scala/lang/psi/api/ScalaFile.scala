package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.openapi.util.TextRange

/**
 * @author ilyas
 */
trait ScalaFile extends ScalaPsiElement
  with ScFile
  with ScImportsHolder {

  def firstPackaging: Option[toplevel.ScPackaging]

  def typeDefinitions: Seq[toplevel.typedef.ScTypeDefinition]

  def packagingRanges: Seq[TextRange]

  def isScriptFile: Boolean

  def isMultipleDeclarationsAllowed: Boolean

  def isWorksheetFile: Boolean

  def allowsForwardReferences: Boolean
}