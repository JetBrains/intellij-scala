package org.jetbrains.plugins.scala.lang.psi.api.toplevel
package imports

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr.ExplicitNamedMember
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaElementVisitor, ScalaPsiElement}

trait ScImportExpr extends ScalaPsiElement {
  def reference: Option[ScStableCodeReference]

  def selectorSet: Option[ScImportSelectors]

  def selectors: Seq[ScImportSelector] =
    selectorSet.iterator.flatMap(_.selectors).toSeq

  //def isWildcardImportExpr: Boolean

  //TODO: rename the method.
  //  "selector" means it's enclosed in braces, like import a.b.{_}
  //  but this method can return true for `import a.b._`
  def hasWildcardSelector: Boolean

  def hasGivenSelector: Boolean

  def wildcardElement: Option[PsiElement]

  def qualifier: Option[ScStableCodeReference]

  def deleteExpr(): Unit

  def deleteRedundantSingleSelectorBraces(): Unit

  def importedNames: Seq[String] = selectorSet match {
    case Some(set) => set.selectors.flatMap(_.importedName)
    case _ => reference.toSeq.map(_.refName)
  }

  /**
   * Explicitly named members introduced by this import or export expression.
   *
   * `import` and `export` share this PSI. The result covers a direct member reference
   * (`import a.b`) and explicitly named selectors (`import a.{b, c => d}`), retaining
   * both the visible name and the reference used to resolve the original member.
   *
   * Wildcard and `given` selectors are excluded.
   * A named selector in an expression that also contains one is still included.
   */
  def explicitNamedMembers: Seq[ExplicitNamedMember] =
    selectorSet match {
      case Some(selectorsSet) =>
        // `import a.{b, c => d, *}`
        selectorsSet.selectors.flatMap(_.explicitNamedMember)
      case _ =>
        if (hasWildcardSelector || hasGivenSelector)
          // `import a.*` or `import a.given`
          Seq.empty
        else
          // `import a.b`
          reference.map(ExplicitNamedMember.fromRef).toSeq
    }

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitImportExpr(this)
}

object ScImportExpr {

  /**
   * A member explicitly introduced by a direct import/export reference or by an import selector.
   *
   * @param visibleName the member name available at the import/export site
   * @param visibleNameElement the PSI identifier that spells [[visibleName]]
   * @param reference the reference to the original member
   */
  case class ExplicitNamedMember(
    visibleName: String,
    visibleNameElement: PsiElement,
    reference: ScStableCodeReference
  )

  object ExplicitNamedMember {
    def fromRef(ref: ScStableCodeReference): ExplicitNamedMember =
      ExplicitNamedMember(ref.refName, ref.nameId, ref)
  }

  object qualifier {
    def unapply(expr: ScImportExpr): Option[ScStableCodeReference] = expr.qualifier
  }
}
