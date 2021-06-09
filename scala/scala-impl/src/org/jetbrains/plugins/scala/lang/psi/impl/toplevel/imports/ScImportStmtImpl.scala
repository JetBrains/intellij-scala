package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package imports

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.scope.{NameHint, PsiScopeProcessor}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScImportStmtStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScImportStmtElementType
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.clean
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.lang.resolve.processor.precedence.PrecedenceHelper
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, StdKinds}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
import scala.collection.mutable

/**
 * @author Alexander Podkhalyuzin
 *         Date: 20.02.2008
 */
class ScImportStmtImpl(stub: ScImportStmtStub,
                       nodeType: ScImportStmtElementType,
                       node: ASTNode,
                       override val toString: String)
  extends ScalaStubBasedElementImpl(stub, nodeType, node)
    with ScImportStmt {

  import ScImportStmtImpl._

  override def importExprs: Seq[ScImportExpr] =
    getStubOrPsiChildren(ScalaElementType.IMPORT_EXPR, JavaArrayFactoryUtil.ScImportExprFactory).toSeq

  override def processDeclarations(
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean = {

    val importsIterator = importExprs.takeWhile(_ != lastParent).reverseIterator
    while (importsIterator.hasNext) {
      val importExpr = importsIterator.next()
      ProgressManager.checkCanceled()

      def workWithImportExpr: Boolean = {
        val ref = importExpr.reference match {
          case Some(element) => element
          case _             => return true
        }

        val nameHint = processor.getHint(NameHint.KEY).nullSafe
        val name     = nameHint.fold("")(_.getName(state))

        if (name != "" && !importExpr.hasWildcardSelector) {
          val decodedName   = clean(name)
          val importedNames = importExpr.importedNames.map(clean)
          if (!importedNames.contains(decodedName)) return true
        }

        val checkWildcardImports = processor match {
          case r: ResolveProcessor =>
            if (!r.checkImports()) return false
            r.checkWildcardImports()
          case _ => true
        }

        val qualifier =
          importExpr.qualifier.getOrElse(return true)

        val resolve = processor match {
          case p: ResolveProcessor =>
            ref match {
              // do not process methodrefs when importing a type from a type
              case ref: ScStableCodeReference
                if p.kinds.contains(ResolveTargets.CLASS) &&
                  ref.getKinds(incomplete = false).contains(ResolveTargets.CLASS) &&
                  ref.getKinds(incomplete = false).contains(ResolveTargets.METHOD) =>
                ref.resolveTypesOnly(false)
              case ref: ScStableCodeReference if p.kinds.contains(ResolveTargets.METHOD) =>
                ref.resolveMethodsOnly(false)
              case _ => ref.multiResolveScala(false)
            }
          case _ => ref.multiResolveScala(false)
        }

        def isInPackageObject(element: PsiNamedElement): Boolean =
          PsiTreeUtil.getContextOfType(element, true, classOf[ScTypeDefinition]) match {
            case obj: ScObject if obj.isPackageObject => true
            case _                                    => false
          }

        def resolvedQualifier(): Option[PsiElement] = qualifier.bind().map(_.element)

        def qualifierType(checkPackageObject: Boolean): Option[ScType] =
          resolvedQualifier().flatMap {
            case p: PsiPackage =>
              if (!checkPackageObject) None
              else
                ScalaShortNamesCacheManager
                  .getInstance(getProject)
                  .findPackageObjectByName(p.getQualifiedName, this.resolveScope)
                  .flatMap(_.`type`().toOption)
            case _ => ScSimpleTypeElementImpl.calculateReferenceType(qualifier).toOption
          }

        val resolveIterator = resolve.iterator
        while (resolveIterator.hasNext) {

          @tailrec
          def getFirstReference(ref: ScStableCodeReference): ScStableCodeReference =
            ref.qualifier match {
              case Some(qual) => getFirstReference(qual)
              case _          => ref
            }

          val next        = resolveIterator.next()
          val elem        = next.getElement
          val importsUsed = getFirstReference(qualifier).bind().fold(next.importsUsed)(r => r.importsUsed ++ next.importsUsed)
          val subst       = state.substitutor.followed(next.substitutor)

          val qualifierFqn = resolvedQualifier().collect {
            case p: PsiPackage => p.getQualifiedName
            case obj: ScObject => obj.qualifiedName
          }

          (elem, processor) match {
            case (pack: PsiPackage, completionProcessor: CompletionProcessor) if completionProcessor.includePrefixImports =>
              val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(getProject)
              val prefixImports = settings.getImportsWithPrefix.filter(s =>
                !s.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX) &&
                  s.substring(0, s.lastIndexOf(".")) == pack.getQualifiedName
              )
              val excludeImports = settings.getImportsWithPrefix.filter(s =>
                s.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX) &&
                  s.substring(ScalaCodeStyleSettings.EXCLUDE_PREFIX.length, s.lastIndexOf(".")) == pack.getQualifiedName
              )
              val names = new mutable.HashSet[String]()
              for (prefixImport <- prefixImports) {
                names += prefixImport.substring(prefixImport.lastIndexOf('.') + 1)
              }
              val excludeNames = new mutable.HashSet[String]()
              for (prefixImport <- excludeImports) {
                excludeNames += prefixImport.substring(prefixImport.lastIndexOf('.') + 1)
              }
              val wildcard = names.contains("_")

              def isOK(name: String): Boolean =
                if (wildcard) !excludeNames.contains(name)
                else          names.contains(name)

              val newImportsUsed = importsUsed ++ tryMarkImportExprUsed(processor, qualifierFqn, importExpr)
              val newState       = state.withPrefixCompletion.withImportsUsed(newImportsUsed)

              val importsProcessor = new BaseProcessor(StdKinds.stableImportSelector) {

                override protected def execute(namedElement: PsiNamedElement)
                                              (implicit state: ResolveState): Boolean =
                  if (isOK(namedElement.name)) completionProcessor.execute(namedElement, state)
                  else                         true

                override def getHint[T](hintKey: Key[T]): T = completionProcessor.getHint(hintKey)
              }

              elem.processDeclarations(importsProcessor, newState, this, place)
            case _ =>
          }

          ProgressManager.checkCanceled()

          importExpr.selectorSet match {
            case None =>
              // Update the set of used imports
              val newImportsUsed = importsUsed ++ tryMarkImportExprUsed(processor, qualifierFqn, importExpr)
              val refType        = qualifierType(isInPackageObject(next.element))

              val newState: ResolveState = state
                .withImportsUsed(newImportsUsed)
                .withSubstitutor(subst)
                .withFromType(refType)

              if (importExpr.hasWildcardSelector) {
                if (!checkWildcardImports)
                  return true

                val processed = (elem, refType, processor) match {
                  case (cl: PsiClass, _, processor: BaseProcessor) if !cl.is[ScTemplateDefinition] =>
                    processor.processType(ScDesignatorType.static(cl), place, newState)
                  case (_, Some(value), processor: BaseProcessor) =>
                    processor.processType(value, place, newState)
                  case _ =>
                    elem.processDeclarations(processor, newState, this, place)
                }

                if (!processed)
                  return false
              }
              else if (!processor.execute(elem, newState))
                return false
            case Some(set) =>
              val shadowed  = mutable.HashSet.empty[(ScImportSelector, PsiElement)]
              val selectors = set.selectors.iterator //for reducing stacktrace

              while (selectors.hasNext) {
                val selector = selectors.next()
                ProgressManager.checkCanceled()
                selector.reference match {
                  case Some(reference) =>
                    val isImportAlias = selector.isAliasedImport && !selector.importedName.contains(reference.refName)
                    if (isImportAlias) {
                      for (result <- reference.multiResolveScala(false)) {
                        //Resolve the name imported by selector
                        //Collect shadowed and aliased elements
                        shadowed += ((selector, result.getElement))
                        val importedName = selector.importedName.map(clean)

                        if (!importedName.contains("_")) {
                          val refType = qualifierType(isInPackageObject(result.element))
                          //processor should skip shadowed reference
                          val newImportsUsed =
                            importsUsed ++ tryMarkImportSelectorUsed(processor, qualifierFqn, selector)

                          val newState =
                            state
                              .withRename(importedName)
                              .withImportsUsed(newImportsUsed)
                              .withSubstitutor(subst.followed(result.substitutor))
                              .withFromType(refType)

                          if (!processor.execute(result.getElement, newState)) {
                            return false
                          }
                        }
                      }
                    }
                  case _ =>
                }
              }

              // There is total import from stable id
              // import a.b.c.{d=>e, f=>_, _}
              if (set.hasWildcard) {
                if (!checkWildcardImports) return true
                processor match {
                  case bp: BaseProcessor =>
                    ProgressManager.checkCanceled()

                    val p1 = new BaseProcessor(bp.kinds) {
                      override def getHint[T](hintKey: Key[T]): T = processor.getHint(hintKey)

                      override def isImplicitProcessor: Boolean = bp.isImplicitProcessor

                      override def handleEvent(event: PsiScopeProcessor.Event, associated: Object): Unit =
                        processor.handleEvent(event, associated)

                      override def getClassKind: Boolean          = bp.getClassKind
                      override def setClassKind(b: Boolean): Unit = bp.setClassKind(b)

                      override protected def execute(namedElement: PsiNamedElement)
                                                    (implicit state: ResolveState): Boolean = {
                        if (shadowed.exists(p => ScEquivalenceUtil.smartEquivalence(namedElement, p._2))) return true

                        val refType = qualifierType(isInPackageObject(namedElement))
                        val newState = state
                          .withSubstitutor(subst)
                          .withFromType(refType)

                        processor.execute(namedElement, newState)
                      }
                    }

                    val newImportsUsed =
                      importsUsed ++ tryMarkImportExprUsed(processor, qualifierFqn, importExpr, wildcard = true)

                    val newState =
                      state
                        .withImportsUsed(newImportsUsed)
                        .withSubstitutor(subst)

                    (elem, processor) match {
                      case (cl: PsiClass, processor: BaseProcessor) if !cl.isInstanceOf[ScTemplateDefinition] =>
                        val qualType = qualifierType(isInPackageObject(next.element))

                        if (!processor.processType(ScDesignatorType.static(cl), place, newState.withFromType(qualType)))
                          return false
                      case _ =>
                        // In this case import optimizer should check for used selectors
                        if (!elem.processDeclarations(p1, newState, this, place))
                          return false
                    }
                  case _ => true
                }
              }

              //wildcard import first, to show that this imports are unused if they really are
              set.selectors.foreach { selector =>
                ProgressManager.checkCanceled()
                for {
                  element <- selector.reference
                  result  <- element.multiResolveScala(false)
                } {
                  if (!selector.isAliasedImport || selector.importedName == selector.reference.map(_.refName)) {
                    val rSubst = result.substitutor

                    val newImportsUsed =
                      importsUsed ++ tryMarkImportSelectorUsed(processor, qualifierFqn, selector)

                    val newState =
                      state
                        .withImportsUsed(newImportsUsed)
                        .withSubstitutor(subst.followed(rSubst))
                        .withFromType(qualifierType(isInPackageObject(result.element)))

                    if (!processor.execute(result.getElement, newState))
                      return false
                  }
                }
              }
          }
        }
        true
      }

      if (!workWithImportExpr) return false
    }
    true
  }
}

object ScImportStmtImpl {
  /** Utility methods to conditionally mark imports as used
   * only if qualifier is not already available
   * (e.g. comes from the same package or is implicitly imported)
   */
  private def tryMarkImportSelectorUsed(
    processor:    PsiScopeProcessor,
    qualifierFqn: Option[String],
    selector:     ScImportSelector
  ): Option[ImportUsed] = {
    val importUsed = ImportSelectorUsed(selector)

    if (selector.isAliasedImport) importUsed.toOption
    else markImportAsUsed(processor, qualifierFqn, importUsed)
  }

  private def tryMarkImportExprUsed(
    processor:    PsiScopeProcessor,
    qualifierFqn: Option[String],
    expr:         ScImportExpr,
    wildcard: Boolean = false
  ): Option[ImportUsed] = {
    val importUsed =
      if (wildcard) ImportWildcardSelectorUsed(expr)
      else          ImportExprUsed(expr)

    markImportAsUsed(processor, qualifierFqn, importUsed)
  }

  private def markImportAsUsed(
    processor:    PsiScopeProcessor,
    qualifierFqn: Option[String],
    importUsed:   ImportUsed
  ): Option[ImportUsed] = processor match {
    case helper: PrecedenceHelper =>
      val isAlreadyAvailable = qualifierFqn.exists(helper.isAvailableQualifier)
      (!isAlreadyAvailable).option(importUsed)
    case _ => importUsed.toOption
  }
}