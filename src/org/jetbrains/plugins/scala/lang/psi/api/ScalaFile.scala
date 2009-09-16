package org.jetbrains.plugins.scala
package lang
package psi
package api

import com.intellij.psi.impl.source.PsiFileWithStubSupport
import com.intellij.psi.{PsiClassOwner, PsiImportHolder, PsiClass}

import toplevel.packaging.ScPackaging
import toplevel.ScToplevelElement
import org.jetbrains.annotations.Nullable

/**
 * @author ilyas
 */

trait ScalaFile extends ScalaPsiElement with ScToplevelElement with PsiClassOwner with ScDeclarationSequenceHolder 
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

  def isCompiled: Boolean

  def sourceName: String

  def isScriptFile(withCashing: Boolean = true): Boolean
}