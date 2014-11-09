package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.openapi.util.TextRange
import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.psi.{PsiClass, PsiClassOwnerEx, PsiImportHolder}
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScToplevelElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging

/**
 * @author ilyas
 */

trait ScalaFile extends ScalaPsiElement with ScToplevelElement with PsiClassOwnerEx with ScDeclarationSequenceHolder
    with PsiImportHolder with ScImportsHolder with PsiFileWithStubSupport {

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

  def isCompiled: Boolean

  def sourceName: String

  def isScriptFile(withCashing: Boolean = true): Boolean

  def isWorksheetFile: Boolean
}