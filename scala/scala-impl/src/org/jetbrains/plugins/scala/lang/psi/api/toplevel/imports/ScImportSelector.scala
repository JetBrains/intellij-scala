package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package imports

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

/**
  * @author Alexander Podkhalyuzin
  *         Date: 20.02.2008
  */
trait ScImportSelector extends ScalaPsiElement {
  def importedName: Option[String]

  def reference: Option[ScStableCodeReference]

  def isWildcardSelector: Boolean

  def wildcardElement: Option[PsiElement]

  /**
   * @param removeRedunduntBraces whether to remove remaining redundant curly brace in the remaining single selector<br>
   *                              example: {{{
   *                                remove `d` in : import a.b.{c, d}`
   *                                result (removeRedunduntBraces = false) : `import a.b.{c}
   *                                result (removeRedunduntBraces = true)  : `import a.b.c
   *                              }}}
   */
  def deleteSelector(removeRedundantBraces: Boolean): Unit

  def isAliasedImport: Boolean

  def isGivenSelector: Boolean

  def givenTypeElement: Option[ScTypeElement]
}