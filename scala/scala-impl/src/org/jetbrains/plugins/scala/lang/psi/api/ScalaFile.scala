package org.jetbrains.plugins.scala.lang.psi.api

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.{ScExportsHolder, ScImportsHolder}

trait ScalaFile extends ScalaPsiElement
  with ScFile
  with ScImportsHolder
  with ScExportsHolder {

  def firstPackaging: Option[ScPackaging]

  def typeDefinitions: Seq[ScTypeDefinition]

  def members: Seq[ScMember]

  def packagingRanges: Seq[TextRange]

  /** @return true if in this file it's allowed to have multiple declarations with the same name (for example in REPL worksheets) */
  def isMultipleDeclarationsAllowed: Boolean

  def isWorksheetFile: Boolean

  def allowsForwardReferences: Boolean

  /**
   * A synthetic object, containing top level members defined in this source file,
   * used for the purpose of resolving said members from Java code.
   */
  def topLevelWrapperObject: Option[PsiClass]

  /**
   * Context is any kind of information required for the file analyzing, different from the file contents itself.<br>
   * It allows us to force-invalidate psi elements caches without changing file contents<br>
   * @see [[org.jetbrains.plugins.scala.caches.BlockModificationTracker.apply]]<br>
   * @see [[org.jetbrains.plugins.scala.caches.CachesUtil.fileContextModTracker]]
   */
  def getContextModificationStamp: Long
  def incContextModificationStamp(): Unit
}