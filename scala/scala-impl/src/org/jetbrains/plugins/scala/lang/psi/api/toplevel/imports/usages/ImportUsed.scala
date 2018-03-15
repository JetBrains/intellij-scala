package org.jetbrains.plugins.scala
package lang
package psi
package api
package toplevel
package imports
package usages


import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.extensions.ifReadAllowed
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject

/**
 * Base class to store import-provided reference elements
 *
 * @author ilyas
 */
abstract sealed class ImportUsed(val e: PsiElement) {
  def importExpr: ScImportExpr

  override def toString: String = ifReadAllowed(e.getText)("")

  def qualName: Option[String]

  def isAlwaysUsed: Boolean = {
    val settings = ScalaCodeStyleSettings.getInstance(e.getProject)
    qualName.exists(settings.isAlwaysUsedImport) || isLanguageFeatureImport
  }

  private def isLanguageFeatureImport: Boolean = {
    val expr = importExpr

    if (expr == null) return false
    if (expr.qualifier == null) return false
    expr.qualifier.resolve() match {
      case o: ScObject =>
        o.qualifiedName.startsWith("scala.language")
      case _ => false
    }
  }

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
case class ImportExprUsed(importExpr: ScImportExpr) extends ImportUsed(importExpr) {
  override def qualName: Option[String] = {
    if (importExpr.qualifier == null) None
    else if (importExpr.isSingleWildcard) Some(importExpr.qualifier.qualName + "._")
    else importExpr.reference.map(ref => importExpr.qualifier.qualName + "." + ref.refName)
  }

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

  override def importExpr: ScImportExpr = PsiTreeUtil.getParentOfType(sel, classOf[ScImportExpr])

  override def qualName: Option[String] = {
    importExpr.reference.zip(sel.reference).map {
      case (left, right) => s"${left.qualName}.${right.refName}"
    }.headOption
  }

  override def toString: String = "ImportSelectorUsed(" + super.toString + ")"

  //we can't reliable tell that shadowing is redundant, so it should never be marked as unused
  override def isAlwaysUsed: Boolean = sel.importedName.contains("_") || super.isAlwaysUsed
}

/**
 * Marks import expression with trailing wildcard selector as used
 * Example:
 *
 * import aaa.bbb.{A => B, C => _ , _}
 */
case class ImportWildcardSelectorUsed(importExpr: ScImportExpr) extends ImportUsed(importExpr) {
  override def qualName: Option[String] = {
    importExpr.reference.map(ref => ref.qualName + "._")
  }

  override def toString: String = "ImportWildcardSelectorUsed(" + super.toString + ")"
}