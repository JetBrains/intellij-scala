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
   *    import a.b._           ---> None
   * }}}
   */
  def importedName: Option[String]

  /**
   * @return Some name of identifier which goes after alias symbol `=>` or `as`<br>
   *         This includes wildcards `_` and `*`
   *         None in case there is no alias
   * @example {{{
   *    import a.b.{c => ccc}  ---> Some(ccc)
   *    import a.b.c           ---> None
   *    import a.b.{c => _}    ---> Some(_)
   *    import a.b.{c => *}    ---> Some(*)
   * }}}
   */
  def aliasName: Option[String]

  /**
   * PSI identifier after an alias symbol (`=>` or `as`), when present.
   *
   * Unlike [[aliasName]], this is only available from the parsed PSI tree, not from the stub.
   */
  def aliasNameElement: Option[PsiElement]

  /**
   * Same as [[aliasName]] but returns None for hiding import (like `import a.b.{c => _}`)
   *
   * @example {{{
   *    import a.b.{c => ccc}  ---> Some(ccc)
   *    import a.b.c           ---> None
   *    import a.b.{c => _}    ---> None
   *    import a.b.{c => *}    ---> None (in Scala 3 or in Scala 2 with -Xsource:3
   * }}}
   */
  def aliasNameWithIgnoredHidingImport: Option[String]

  /**
   * Whether the import has alias (rename) e.g. {{{
   *   import a.b.{c => cRenamed} ---> true
   *   import a.b.{d => _}        ---> true
   *   import a.b.c               ---> false
   * }}}
   */
  def isAliasedImport: Boolean

  final def isScala2StyleAliasImport: Boolean =
    isAliasedImport &&
      !isScala3StyleAliasImport

  final def isScala3StyleAliasImport: Boolean =
    isAliasedImport &&
      this.features.`Scala 3 renaming imports` &&
      this.findFirstChildByType(ScalaTokenType.AsKeyword).isDefined

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

  /**
   * Explicitly named member introduced by this selector, if any.
   *
   * Wildcard, `given`, and hiding selectors do not introduce one named member and are excluded.
   *
   * Example:
   * {{{
   *   import a.{b => c}
   *   import a.{d}
   *   import a.{e => _}
   *   import a.{*}
   *
   *   // For the `b => c` selector:
   *   selector.explicitNamedMember
   *   // Some(ExplicitNamedMember("c", c, b))
   * }}}
   */
  def explicitNamedMember: Option[ScImportExpr.ExplicitNamedMember] =
    if (isWildcardSelector || isGivenSelector)
      None
    else {
      for {
        ref <- reference
        (visibleName, visibleNameElement) <-
          if (isAliasedImport)
            aliasNameWithIgnoredHidingImport.zip(aliasNameElement)
          else
            Some(ref.refName -> ref.nameId)
      } yield ScImportExpr.ExplicitNamedMember(visibleName, visibleNameElement, ref)
    }
}
