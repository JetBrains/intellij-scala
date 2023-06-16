package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.imports

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi._
import com.intellij.psi.scope.{NameHint, PsiScopeProcessor}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.plugins.scala.JavaArrayFactoryUtil
import org.jetbrains.plugins.scala.caches.ScalaShortNamesCacheManager
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScGiven, ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.base.types.ScSimpleTypeElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.{ScExportStmtElementType, ScImportOrExportStmtElementType, ScImportStmtElementType}
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScExportStmtStub, ScImportOrExportStmtStub, ScImportStmtStub}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.clean
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor._
import org.jetbrains.plugins.scala.lang.resolve.{ResolveTargets, StdKinds}
import org.jetbrains.plugins.scala.util.ScEquivalenceUtil

import scala.annotation.tailrec
import scala.collection.mutable

class ScImportStmtImpl(
  stub: ScImportStmtStub,
  nodeType: ScImportStmtElementType,
  node: ASTNode,
  toString: String
) extends ScImportOrExportImpl[ScImportStmt, ScImportStmtStub](stub, nodeType, node, toString)
  with ScImportStmt

class ScExportStmtImpl(
  stub: ScExportStmtStub,
  nodeType: ScExportStmtElementType,
  node: ASTNode,
  toString: String
) extends ScImportOrExportImpl[ScExportStmt, ScExportStmtStub](stub, nodeType, node, toString)
  with ScExportStmt

abstract sealed class ScImportOrExportImpl[
  Psi <: ScImportOrExportStmt,
  Stub >: Null <: ScImportOrExportStmtStub[Psi]
](
  stub: Stub,
  nodeType: ScImportOrExportStmtElementType[Psi, Stub],
  node: ASTNode,
  override val toString: String
) extends ScalaStubBasedElementImpl(stub, nodeType, node)
  with ScImportOrExportStmt {

  import ScImportOrExportImpl._

  override def importExprs: Seq[ScImportExpr] =
    getStubOrPsiChildren(ScalaElementType.IMPORT_EXPR, JavaArrayFactoryUtil.ScImportExprFactory).toSeq

  override def processDeclarations(
    processor:  PsiScopeProcessor,
    state:      ResolveState,
    lastParent: PsiElement,
    place:      PsiElement
  ): Boolean = {
    val isScala3 = this.isInScala3File
    val importExpr1 = importExprs.takeWhile(_ != lastParent)
    val importsIterator = importExpr1.reverseIterator

    while (importsIterator.hasNext) {
      val importExpr = importsIterator.next()
      ProgressManager.checkCanceled()

      if (!processDeclarationForImportExpr(processor, state, place, getProject, this, importExpr, isScala3))
        return false
    }
    true
  }
}

object ScImportOrExportImpl {

  //Can/Should we move it to ScImportExprImpl ?
  def processDeclarationForImportExpr(
    processor: PsiScopeProcessor,
    state: ResolveState,
    place: PsiElement,
    project: Project,
    importOrExportStmt: ScImportOrExportStmt,
    importExpr: ScImportExpr,
    isScala3: Boolean,
  ): Boolean = {
    val ref = importExpr.reference match {
      case Some(element) => element
      case _             => return true
    }

    val nameHint = processor.getHint(NameHint.KEY).nullSafe
    val name     = nameHint.fold("")(_.getName(state))

    if (name != "" && !importExpr.hasWildcardSelector && !importExpr.hasGivenSelector) {
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

    val qualifier: ScStableCodeReference =
      importExpr.qualifier.getOrElse(return true)

    val resolve = processor match {
      case p: ResolveProcessor =>
        ref match {
          // do not process method refs when importing a type from a type
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
              .getInstance(project)
              //NOTE: note sure whether we need to take resolveScope from importsStmt and can't take it from importExpr
              .findPackageObjectByName(p.getQualifiedName, importOrExportStmt.resolveScope)
              .flatMap(_.`type`().toOption)
        case _ => ScSimpleTypeElementImpl.calculateReferenceType(qualifier).toOption
      }

    def wildcardProxyProcessor(bp: BaseProcessor,
                               hasWildcard: Boolean,
                               shadowed: mutable.HashSet[(ScImportSelector, PsiElement)] = mutable.HashSet.empty,
                               givenImports: GivenImports = GivenImports.empty): BaseProcessor = {
      assert(bp == processor)

      if (!isScala3 && shadowed.isEmpty && !givenImports.hasImports) bp
      else new BaseProcessor(bp.kinds)(project) {

        override def getHint[T](hintKey: Key[T]): T = bp.getHint(hintKey)

        override def isImplicitProcessor: Boolean = bp.isImplicitProcessor

        override def handleEvent(event: PsiScopeProcessor.Event, associated: Object): Unit =
          bp.handleEvent(event, associated)

        override def getClassKind: Boolean          = bp.getClassKind
        override def setClassKind(b: Boolean): Unit = bp.setClassKind(b)

        override protected def execute(namedElement: PsiNamedElement)
                                      (implicit state: ResolveState): Boolean = {
          if (shadowed.exists(p => ScEquivalenceUtil.smartEquivalence(namedElement, p._2))) {
            return true
          }

          var newState = state
          val importedByGiven = if (isScala3) {
            // given elements are handled in a special way by given imports
            namedElement match {
              case ScGiven.Original(givenElement) =>
                // check if there are any given imports
                if (!givenImports.hasImports) {
                  // no given selector at all, so don't import, because a normal wildcard doesn't import givens
                  return true
                }

                val adjustedGivenElementType = givenElement.`type`().map(state.substitutor)
                givenImports.conformingGivenSelector(adjustedGivenElementType) match {
                  case Some(conformingGivenSelector) =>
                    val additionalImportsUsed = new ImportSelectorUsed(conformingGivenSelector)
                    newState = newState.withImportsUsed(newState.importsUsed + additionalImportsUsed)
                    true
                  case None =>
                    // no selector conforms, so do not import
                    return true
                }
              case _ =>
                false
            }
          } else false

          if (!(importedByGiven || hasWildcard)) {
            return true
          }

          val refType = qualifierType(isInPackageObject(namedElement))
          newState = newState.withFromType(refType)
          bp.execute(namedElement, newState)
        }
      }
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

      (elem, processor) match {
        case (pack: PsiPackage, completionProcessor: CompletionProcessor) if completionProcessor.includePrefixImports =>
          val settings: ScalaCodeStyleSettings = ScalaCodeStyleSettings.getInstance(project)
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

          val newImportsUsed = importsUsed + new ImportExprUsed(importExpr)
          val newState       = state.withPrefixCompletion.withImportsUsed(newImportsUsed)

          val importsProcessor = new BaseProcessor(StdKinds.stableImportSelector)(project) {

            override protected def execute(namedElement: PsiNamedElement)
                                          (implicit state: ResolveState): Boolean =
              if (isOK(namedElement.name)) completionProcessor.execute(namedElement, state)
              else                         true

            override def getHint[T](hintKey: Key[T]): T = completionProcessor.getHint(hintKey)
          }

          elem.processDeclarations(importsProcessor, newState, importOrExportStmt, place)
        case _ =>
      }

      ProgressManager.checkCanceled()

      importExpr.selectorSet match {
        case None =>
          val importHasWildcardSelector = importExpr.hasWildcardSelector
          // Update the set of used imports
          val newImportsUsed = importsUsed + new ImportExprUsed(importExpr)
          val refType        = qualifierType(isInPackageObject(next.element))

          val newState: ResolveState = state
            .withImportsUsed(newImportsUsed)
            .withSubstitutor(subst)
            .withFromType(refType)

          if (importHasWildcardSelector) {
            if (!checkWildcardImports)
              return true

            val processed = (elem, refType, processor) match {
              case (cl: PsiClass, _, processor: BaseProcessor) if !cl.is[ScTemplateDefinition] =>
                processor.processType(ScDesignatorType.static(cl), place, newState)
              case (_, Some(value), processor: BaseProcessor) =>
                wildcardProxyProcessor(processor, hasWildcard = true)
                  .processType(value, place, newState)
              case _ =>
                elem.processDeclarations(processor, newState, importOrExportStmt, place)
            }

            if (!processed)
              return false
          }
          else if (!processor.execute(elem, newState))
            return false
        case Some(set) =>
          val shadowed  = mutable.HashSet.empty[(ScImportSelector, PsiElement)]
          val givenImports = GivenImports(set)
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
                        importsUsed + new ImportSelectorUsed(selector)

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
          val hasWildcard = set.hasWildcard
          if (hasWildcard || givenImports.hasImports) {
            if (!checkWildcardImports) return true
            processor match {
              case bp: BaseProcessor =>
                ProgressManager.checkCanceled()

                val wildcardExprUsed =
                  if (hasWildcard) Some(new ImportWildcardSelectorUsed(importExpr))
                  else None
                val newImportsUsed =
                  importsUsed ++ wildcardExprUsed

                val newState =
                  state
                    .withImportsUsed(newImportsUsed)
                    .withSubstitutor(subst)

                (elem, processor) match {
                  case (cl: PsiClass, processor: BaseProcessor) if hasWildcard && !cl.is[ScTemplateDefinition] =>
                    val qualType = qualifierType(isInPackageObject(next.element))

                    if (!processor.processType(ScDesignatorType.static(cl), place, newState.withFromType(qualType)))
                      return false
                  case _ =>
                    // In this case import optimizer should check for used selectors
                    if (!elem.processDeclarations(wildcardProxyProcessor(bp, hasWildcard = hasWildcard, shadowed, givenImports), newState, importOrExportStmt, place))
                      return false
                }
              case _ =>
            }
          }

          //wildcard import first, to show that this imports are unused if they really are
          for(selector <- set.selectors) {
            if (!selector.isAliasedImport || selector.importedName == selector.reference.map(_.refName)) {
              ProgressManager.checkCanceled()
              for {
                element <- selector.reference
                result <- element.multiResolveScala(false)
              } {
                val rSubst = result.substitutor

                val newImportsUsed =
                  importsUsed + new ImportSelectorUsed(selector)

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

  trait GivenImports {
    def conformingGivenSelector(ty: TypeResult): Option[ScImportSelector]
    def hasWildcard: Boolean
    def hasImports: Boolean
  }

  object GivenImports {
    def empty: GivenImports = new GivenImports {
      override def conformingGivenSelector(ty: TypeResult): Option[ScImportSelector] = None
      override def hasWildcard: Boolean = false
      override def hasImports: Boolean = false
    }

    def apply(selectors: ScImportSelectors): GivenImports = new GivenImports {
      private val givenSelectors = selectors.selectors.filter(_.isGivenSelector)

      private val wildcardSelector: Option[ScImportSelector] = givenSelectors.find(_.givenTypeElement.isEmpty)

      private val filterSelectors: Map[ScType, ScImportSelector] =
        givenSelectors.flatMap { sel =>
          val maybeType = sel.givenTypeElement.flatMap(_.`type`().toOption)
          maybeType.map(_ -> sel)
        }.toMap

      def conformingGivenSelector(ty: TypeResult): Option[ScImportSelector] = ty match {
        case Right(ty) =>
          val conformingSelectors = {
            val selectors = filterSelectors.filter { case (fTy, _) => ty conforms fTy }.values

            //todo: should we use another ordering/precedence?
            selectors.toSeq.sortBy(_.startOffset) ++ wildcardSelector
          }

          conformingSelectors.headOption
        case Left(_) => wildcardSelector
      }

      def hasWildcard: Boolean = wildcardSelector.isDefined
      def hasImports: Boolean = hasWildcard || filterSelectors.nonEmpty
    }
  }
}
