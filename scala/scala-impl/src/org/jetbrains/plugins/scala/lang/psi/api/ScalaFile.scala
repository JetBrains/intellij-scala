package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScToplevelElement}

/**
 * @author ilyas
 */
trait ScalaFile extends ScalaPsiElement
  with ScFile
  with ScToplevelElement
  with ScDeclarationSequenceHolder
  with ScImportsHolder {

  @Deprecated
  def importClass(aClass: PsiClass): Boolean = {
    addImportForClass(aClass)
    true
  }

  def getPackagings: Array[ScPackaging]

  def getPackageName: String

  @Nullable
  def packageName: String

  def packagingRanges: Seq[TextRange]

  def sourceName: String

  def isScriptFile: Boolean = isScriptFileImpl

  def isScriptFileImpl: Boolean

  def isWorksheetFile: Boolean

  def allowsForwardReferences: Boolean
}