package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package imports
package usages


import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement

/**
 * Base class to store import-provided reference elements
 *
 * @author ilyas
 */
abstract sealed class ImportUsed(val e: PsiElement) {
  override def toString: String = e.getText
}

object ImportUsed {
  val key: Key[_root_.scala.collection.Set[ImportUsed]] = Key.create("scala.used.imports.key")

  def unapply(importUsed: ImportUsed): Option[PsiElement] = {
    Some(importUsed.e)
  }
}


/**
 * Class to mark whole import expression as used (qualified or ending with reference id)
 */
case class ImportExprUsed(expr: ScImportExpr) extends ImportUsed(expr) {
  override def toString: String = "ImportExprUsed(" + super.toString + ")"
}

/**
 * Marks import selector as used <p>
 *
 * Example:                       <p>
 *
 * <code>import aaa.bbb.{C => D, E => _}</code><p>
 *
 * CAUTION! Import Optimized shouldn't remove shadowing selectos of form E => _, otherwise
 * resulting code may be incorrect <p>
 *
 * Example<p>
 * <code lnaguage="java">
 * package aaa.bbb {
 *   class C;
 *   class E;
 *   class F;
 * }
 *
 *
 * import aaa.bbb.{C => D, E => _, _};
 *
 * new <ref>F<p>
 *
 * </code><p>
 * In the example above after removing selector <code>E => _</code> cancels appropriate shadowing and
 * reference to E may clash with some other in that place.
 *
 */
case class ImportSelectorUsed(sel: ScImportSelector) extends ImportUsed(sel) {
  override def toString: String = "ImportSelectorUsed(" + super.toString + ")"
}

/**
 * Marks import expression with trailing wildcard selector as used
 * Example:
 *
 * import aaa.bbb.{A => B, C => _ , _}
 */
case class ImportWildcardSelectorUsed(elem: ScImportExpr) extends ImportUsed(elem) {
  override def toString: String = "ImportWildcardSelectorUsed(" + super.toString + ")"
}