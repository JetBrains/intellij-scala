package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package imports

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenType
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement

trait ScImportSelector extends ScalaPsiElement {

  def parentImportExpression: ScImportExpr

  /**
   * @example {{{
   *    import a.b.c           ---> Some(c)
   *    import a.b.{c => ccc}  ---> Some(ccc)
   * }}}
   */
  def importedName: Option[String]

  /**
   * @example {{{
   *    import a.b.c           ---> None
   *    import a.b.{c => ccc}  ---> Some(ccc)
   * }}}
   */
  def aliasName: Option[String]

  /**
   * Whether the import has alias (rename) e.g. {{{
   *   import a.b.{c => cRenamed} ---> true
   *   import a.b.{d => _}        ---> true
   *   import a.b.c               ---> false
   * }}}
   */
  def isAliasedImport: Boolean

  final def isScala2StyleAliasImport: Boolean = {
    isAliasedImport && {
      val isScala3AliasImport =
        this.features.`Scala 3 renaming imports` &&
          this.findFirstChildByType(ScalaTokenType.AsKeyword).isDefined
      !isScala3AliasImport
    }
  }

  def reference: Option[ScStableCodeReference]

  def isWildcardSelector: Boolean

  def wildcardElement: Option[PsiElement]

  /**
   * @param removeRedundantBraces whether to remove remaining redundant curly brace in the remaining single selector<br>
   *                              example: {{{
   *                                remove `d` in : import a.b.{c, d}`
   *                                result (removeRedunduntBraces = false) : `import a.b.{c}
   *                                result (removeRedunduntBraces = true)  : `import a.b.c
   *                              }}}
   */
  def deleteSelector(removeRedundantBraces: Boolean): Unit

  def isGivenSelector: Boolean

  def givenTypeElement: Option[ScTypeElement]
}