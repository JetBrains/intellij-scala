package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package imports

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference

/**
  * @author Alexander Podkhalyuzin
  *         Date: 20.02.2008
  */
trait ScImportSelector extends ScalaPsiElement {
  def importedName: Option[String]

  def reference: Option[ScStableCodeReference]

  def isWildcardSelector: Boolean

  def wildcardElement: Option[PsiElement]

  def deleteSelector(): Unit

  def isAliasedImport: Boolean
}