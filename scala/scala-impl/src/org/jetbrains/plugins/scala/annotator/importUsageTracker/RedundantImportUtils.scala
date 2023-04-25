package org.jetbrains.plugins.scala.annotator.importUsageTracker

import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiNamedElement, PsiPackage, ResolveState}
import org.jetbrains.plugins.scala.extensions.{IteratorExt, PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScImportsHolder
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScObject
import org.jetbrains.plugins.scala.lang.psi.api.{FileDeclarationsHolder, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports.ScImportOrExportImpl
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets
import org.jetbrains.plugins.scala.lang.resolve.processor.ResolveProcessor
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceTypes
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.settings.ScalaProjectSettings

import scala.collection.mutable

/**
 * Redundant import - imports which might be used by the compiler during resolve,
 * but can be safely removed because it's already available in the scope<br>
 * There are two types of redundant imports:
 *
 *  1. import from root imports `java.lang._, scala._, scala.Predef._` or defined with `-Yimports` scalac option
 *  1. import from the same package, example: {{{
 *    package org.example
 *    import org.example.A
 *    ...
 * }}}
 *
 * Not that not all such imports are redundant (in 2.13).
 * If there are name clashes with some wildcard, the import can't be removed, because it has a lower precedence then wildcard.
 */
object RedundantImportUtils {

  def collectPotentiallyRedundantImports(file: ScalaFile): collection.Set[ImportUsed] = {
    val result: mutable.Set[ImportUsed] = mutable.HashSet.empty

    val importHolders: Iterator[ScImportsHolder] = file.depthFirst().filterByType[ScImportsHolder]
    val precedenceType = PrecedenceTypes.forElement(file)
    for {
      importHolder <- importHolders
      redundantImportHelper = new RedundantImportHelper(importHolder, precedenceType)

      importStmt <- importHolder.getImportStatements
      importExprs = importStmt.importExprs
      if importExprs.nonEmpty

      importExpr <- importExprs
    } {
      val importQualifierFqnResolved = resolvedImportQualifierFqn(importExpr)

      val isImportFromAvailableQualifier =
        importQualifierFqnResolved.exists(redundantImportHelper.isAvailableQualifier)

      def isAliasImport =
        ScalaProjectSettings.getInstance(file.getProject).aliasExportsEnabled &&
          importExpr.reference.exists(_.multiResolveScala(false).exists(_.element match {
            case c: PsiClass => FileDeclarationsHolder.isAliasImport(c.qualifiedName, c.scalaLanguageLevelOrDefault)
            case _ => false
          }))

      if (isImportFromAvailableQualifier || isAliasImport) {
        val importsUsed = ImportUsed.buildAllFor(importExpr).filter {
          case ImportSelectorUsed(sel) =>
            sel.aliasName.isEmpty
          case _ => true
        }
        result ++= importsUsed
      }
    }
    result
  }

  private def resolvedImportQualifierFqn(importExpr: ScImportExpr): Option[String] = {
    val qualifier = importExpr.qualifier
    val resolved = qualifier.flatMap(_.bind())
    resolved.map(_.element).collect {
      case p: PsiPackage => p.getQualifiedName
      case obj: ScObject => obj.qualifiedName
    }
  }

  /**
   * ATTENTION: heavy operation, which resolves in all wildcard imports in scope.
   * The assumption is that in reality no one uses imports from root/default packages or from the same package
   * and that such name collisions are very rare.
   */
  def isActuallyRedundant(importUsed: ImportUsed, project: Project, isScala3: Boolean): Boolean = {
    val singleName = importUsed match {
      case ImportExprUsed(expr) =>
        if (expr.selectors.isEmpty && !expr.hasWildcardSelector)
          expr.reference.map(_.refName)
        else
          None
      case ImportSelectorUsed(selector) =>
        selector.importedName
      case _ =>
        None
    }

    singleName match {
      case Some(name) =>
        val importExpr = importUsed.importExpr.get
        val parentImportHolder = importExpr.parentOfType[ScImportsHolder].get
        resolvedImportQualifierFqn(importExpr) match {
          case Some(qualifierFqn) =>
            val hasClashes = singleNameClashesWithSomeNameFromWildcardImport(parentImportHolder, qualifierFqn, name, project, isScala3)
            !hasClashes
          case None =>
            false // do not mark as redundant just in case, if we can't resolve qualifier
        }
      case _ =>
        true
    }
  }

  /**
   * ATTENTION: heavy operation, which resolves in all wildcard imports in scope
   */
  private def singleNameClashesWithSomeNameFromWildcardImport(
    importHolder: ScImportsHolder,
    qualifier: String,
    name: String,
    project: Project,
    isScala3: Boolean
  ): Boolean = {
    //noinspection TypeAnnotation
    class MyResolveProcessor extends ResolveProcessor(ResolveTargets.values, importHolder, name) {
      var nameClasFound: Boolean = false
      override protected def execute(namedElement: PsiNamedElement)(implicit state: ResolveState): Boolean = {
        if (nameMatches(namedElement)) {
          if (!isAccessible(namedElement, ref)) {
            true
          }
          else {
            nameClasFound = true
            false
          }
        }
        else true
      }
    }
    val resolveProcessor = new MyResolveProcessor()

    //for now this is just for debugging purposes
    var clashedImportExpr: ScImportExpr = null

    val found = importHolder.withParentsInFile.filterByType[ScImportsHolder].exists { holder =>
      holder.getImportStatements.iterator.exists { importStmt =>
        importStmt.importExprs.iterator.exists { importExpr =>
          //ignore imports with the same qualifier, cause then it's not actually a "clash"
          if (importExpr.hasWildcardSelector && !resolvedImportQualifierFqn(importExpr).contains(qualifier)) {
            ScImportOrExportImpl.processDeclarationForImportExpr(
              resolveProcessor,
              new ResolveState,
              importHolder,
              project,
              importStmt,
              importExpr,
              isScala3
            )
            val nameClashFound = resolveProcessor.nameClasFound
            if (nameClashFound) {
              clashedImportExpr = importExpr
            }
            nameClashFound
          }
          else
            false
        }
      }
    }

    found
  }

  private class RedundantImportHelper(
    place: ScImportsHolder,
    precedenceTypes: PrecedenceTypes
  ) {

    def isAvailableQualifier(qualifierFqn: String): Boolean =
      isQualifierFromDefaultImports(qualifierFqn) ||
        isQualifierFromSamePackage(qualifierFqn)

    private lazy val defaultImportsQualifiers: Seq[String] =
      precedenceTypes.defaultImports

    /**
     * Reminder: Scala code can have multiple package clauses: {{{
     *   pacakge aaa
     *   pacakge bbb
     *   pacakge ccc
     * }}}
     *
     * In principle we could process all parent package clauses to search redundant import candidates.
     * But we only handle the deepest package for a given scope (in the example above - `aaa.bbb.ccc`).<br>
     *
     * This is because we would need to search for collisions not only with wildcard imports but with all packages clauses in current scope.
     * Here is an example: {{{
     *   package scala
     *   package collection
     *   package mutable
     *
     *   object Main {
     *     import scala.collection.Traversable
     *
     *     def main(args: Array[String]): Unit = {
     *       println(classOf[Traversable[_]])
     *     }
     *   }
     * }}}
     *
     * `scala.collection.Traversable` is "kind of" already available via `package scala ; package collection`
     * and might be a candidate for a "redundant import". But it's actually hidden/overridden with
     * `scala.collection.mutable.Traversable` via `package scala ; package collection ; package mutable`.
     * So to understand whether `import scala.collection.Traversable` is actually redundant we need to try to resolve `Traversable` in `scala.collection.mutable`.
     *
     * While it's doable, I decided to skip such cases for the sake of simplicity.
     */
    private lazy val parentPackagesQualifier: Option[String] = {
      //RedundantImportHelper.collectPackagesFqns(place).headOption.toSet
      place.parentOfType(classOf[ScPackaging], strict = false).map(_.fullPackageName)
    }
//    private lazy val parentPackagesQualifiers: Set[String] = {
//      RedundantImportHelper.collectPackagesFqns(place)
//    }

    private def isQualifierFromDefaultImports(qualifierFqn: String): Boolean =
      defaultImportsQualifiers.contains(qualifierFqn)

    private def isQualifierFromSamePackage(qualifierFqn: String): Boolean = {
      parentPackagesQualifier.contains(qualifierFqn)
    }
  }

//  private object RedundantImportHelper {
//    private def collectPackagesFqns(element: PsiElement): Set[String] =
//      element.withParentsInFile.filterByType[ScPackaging].map(_.fullPackageName).toSet
//  }
}