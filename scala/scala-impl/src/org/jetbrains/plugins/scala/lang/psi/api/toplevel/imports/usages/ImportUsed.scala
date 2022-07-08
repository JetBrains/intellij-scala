package org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages

import com.intellij.psi.{PsiElement, SmartPointerManager, SmartPsiElementPointer}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, ifReadAllowed}
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.lang.TokenTexts
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.{ScImportExpr, ScImportSelector}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

// TODO 1: choose better naming, import used sounds like the import is actually used in the file
//  but in practice it's just a pointer to a import psi element, it can be practically unused in the file
// TODO 2: it should have generic type [T <: PsiElement] instead of hardcoded type in pointer: SmartPsiElementPointer[PsiElement]
//  this requires a lot of changes at usage place, but will allow us to avoid untyped
/**
 * Base class to store import-provided reference elements
 */
sealed abstract class ImportUsed(private val pointer: SmartPsiElementPointer[PsiElement]) {

  def this(e: PsiElement) =
   this(SmartPointerManager.createPointer(e))

  def element: PsiElement = pointer.getElement

  def isValid: Boolean = element.toOption.exists(_.isValid)

  def importExpr: Option[ScImportExpr]

  override def toString: String = ifReadAllowed(element.getText)("")

  def qualName: Option[String]

  def isAlwaysUsed: Boolean = {
    val project = pointer.getProject
    val settings = ScalaCodeStyleSettings.getInstance(project)
    val isScala3 = Option(element).exists(_.isInScala3Module)
    qualName.exists(settings.isAlwaysUsedImport) ||
      isLanguageFeatureImport ||
      (ScalaHighlightingMode.showCompilerErrorsScala3(project) && isScala3)
  }

  private def isLanguageFeatureImport: Boolean = {
    importExpr.exists {
      case ScImportExpr.qualifier(qualifier) =>
        qualifier.resolve() match {
          case o: ScObject =>
            //scala.language & scala.languageFeature
            o.qualifiedName.startsWith("scala.language")
          case _ => false
        }
      case _ => false
    }
  }

  override final def hashCode(): Int = pointer.hashCode()

  override final def equals(obj: Any): Boolean = obj match {
    case iu: ImportUsed => iu.pointer == pointer
    case _ => false
  }
}

object ImportUsed {

  def buildAllFor(importExpr: ScImportExpr): Set[ImportUsed] = {
    val res = Set.newBuilder[ImportUsed]

    if (/*!imp.singleWildcard && */importExpr.selectorSet.isEmpty) {
      res += new ImportExprUsed(importExpr)
    }
    else if (importExpr.hasWildcardSelector) {
      res += new ImportWildcardSelectorUsed(importExpr)
    }
    for (selector <- importExpr.selectors if !selector.isWildcardSelector) {
      res += new ImportSelectorUsed(selector)
    }
    res.result()
  }

  def unapply(importUsed: ImportUsed): Option[PsiElement] = Option(importUsed.element)
}

/**
 * Class to mark whole import expression as used (qualified or ending with reference id)
 */
class ImportExprUsed(
  e: ScImportExpr,
) extends ImportUsed(e) {
  override def importExpr: Option[ScImportExpr] = element.asOptionOf[ScImportExpr]

  override def qualName: Option[String] = {
    val expr = importExpr.getOrElse(return None)

    expr.qualifier.flatMap(qualifier =>
      if (expr.hasWildcardSelector) Some(qualifier.qualName + "." + TokenTexts.importWildcardText(element))
      else expr.reference.map(ref => qualifier.qualName + "." + ref.refName)
    )
  }

  override def toString: String = "ImportExprUsed(" + super.toString + ")"
}

object ImportExprUsed {
  def unapply(arg: ImportExprUsed): Option[ScImportExpr] = arg.importExpr
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
 * Example: {{{
 *     package aaa.bbb {
 *       class C;
 *       class E;
 *       class F;
 *     }
 *
 *     import aaa.bbb.{C => D, E => _, _};
 *
 *     new F
 * }}}
 * In the example above after removing selector <code>E => _</code> cancels appropriate shadowing and
 * reference to E may clash with some other in that place.
 *
 */
class ImportSelectorUsed(
  sel: ScImportSelector,
) extends ImportUsed(sel) {

  override def importExpr: Option[ScImportExpr] =
    Option(element).map(ScalaPsiUtil.getParentImportExpression)

  override def qualName: Option[String] = {
    importExpr.flatMap(_.reference).zip(sel.reference).map {
      case (left, right) => s"${left.qualName}.${right.refName}"
    }
  }

  override def toString: String = "ImportSelectorUsed(" + super.toString + ")"

  //we can't reliable tell that shadowing is redundant, so it should never be marked as unused
  override def isAlwaysUsed: Boolean = sel.importedName.contains("_") || super.isAlwaysUsed
}

object ImportSelectorUsed {
  def unapply(arg: ImportSelectorUsed): Option[ScImportSelector] = arg.element.asOptionOf[ScImportSelector]
}

/**
 * Marks import expression with trailing wildcard selector as used
 * Example:
 *
 * import aaa.bbb.{A => B, C => _ , _}
 */
class ImportWildcardSelectorUsed(
  e: ScImportExpr,
) extends ImportUsed(e) {

  override def importExpr: Option[ScImportExpr] = element.asOptionOf[ScImportExpr]

  override def qualName: Option[String] = {
    importExpr.flatMap(_.reference).map(ref => ref.qualName + "._")
  }

  override def toString: String = "ImportWildcardSelectorUsed(" + super.toString + ")"

}

object ImportWildcardSelectorUsed {
  def unapply(arg: ImportWildcardSelectorUsed): Option[ScImportExpr] = arg.importExpr
}