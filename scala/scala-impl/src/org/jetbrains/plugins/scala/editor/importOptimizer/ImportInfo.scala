package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.psi._
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer._root_prefix
import org.jetbrains.plugins.scala.extensions.implementation.iterator.ContextsIterator
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiMemberExt, PsiModifierListOwnerExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReference
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceImpl
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

import scala.annotation.tailrec
import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * @author Nikolay.Tropin
  */
case class ImportInfo(prefixQualifier: String,
                      relative: Option[String] = None,
                      allNames: Set[String] = Set.empty,
                      singleNames: Set[String] = Set.empty,
                      renames: Map[String, String] = Map.empty,
                      hiddenNames: Set[String] = Set.empty,
                      hasWildcard: Boolean = false,
                      rootUsed: Boolean = false,
                      isStableImport: Boolean = true,
                      allNamesForWildcard: Set[String] = Set.empty,
                      wildcardHasUnusedImplicit: Boolean = false) {

  def withoutRelative: ImportInfo =
    if (relative.isDefined || rootUsed) copy(relative = None) else this

  def split: Seq[ImportInfo] = {
    val builder = ArraySeq.newBuilder[ImportInfo]
    builder ++= singleNames.toSeq.sorted.map { name =>
      template.copy(singleNames = Set(name))
    }
    builder ++= renames.map { rename =>
      template.copy(renames = Map(rename))
    }
    builder ++= hiddenNames.map { hidden =>
      this.toHiddenNameInfo(hidden)
    }
    if (hasWildcard) {
      builder += this.toWildcardInfo
    }
    builder.result()
  }

  def merge(second: ImportInfo): ImportInfo = {
    val relative = this.relative.orElse(second.relative)
    val rootUsed = relative.isEmpty && (this.rootUsed || second.rootUsed)
    new ImportInfo(
      this.prefixQualifier,
      relative,
      this.allNames ++ second.allNames,
      this.singleNames ++ second.singleNames,
      this.renames ++ second.renames,
      this.hiddenNames ++ second.hiddenNames,
      this.hasWildcard || second.hasWildcard,
      rootUsed,
      this.isStableImport && second.isStableImport,
      this.allNamesForWildcard
    )
  }

  def isSimpleWildcard: Boolean = hasWildcard && singleNames.isEmpty && renames.isEmpty && hiddenNames.isEmpty

  def namesFromWildcard: Set[String] = {
    if (hasWildcard) allNames -- singleNames -- renames.keySet
    else Set.empty[String]
  }

  private def template: ImportInfo =
    copy(singleNames = Set.empty, renames = Map.empty, hiddenNames = Set.empty, allNames = Set.empty, hasWildcard = false)

  def toWildcardInfo: ImportInfo = template.copy(hasWildcard = true, allNames = allNamesForWildcard)

  def toHiddenNameInfo(name: String): ImportInfo = template.copy(hiddenNames = Set(name))

  def withRootPrefix: ImportInfo = copy(rootUsed = true)

  def canAddRoot: Boolean = relative.isEmpty && !rootUsed && isStableImport && prefixQualifier.nonEmpty

  def withAllNamesForWildcard(place: PsiElement): ImportInfo = {
    if (!hasWildcard || allNamesForWildcard.nonEmpty) this
    else {
      val (namesForWildcard, implicitNames) = ImportInfo.collectAllNamesAndImplicitsFromWildcard(prefixQualifier, place)
      val hasWildcardImplicits = (implicitNames -- singleNames).nonEmpty
      copy(
        allNames = namesForWildcard -- hiddenNames -- renames.keys,
        allNamesForWildcard = namesForWildcard,
        wildcardHasUnusedImplicit = hasWildcardImplicits
      )
    }
  }
}

object ImportInfo {

  def apply(imp: ScImportExpr, isImportUsed: ImportUsed => Boolean): Option[ImportInfo] = {
    val qualifier = imp.qualifier.orNull
    if (qualifier == null) return None //ignore invalid imports

    val importsUsed = ArrayBuffer[ImportUsed]()
    val allNames = mutable.HashSet[String]()
    val singleNames = mutable.HashSet[String]()
    val renames = mutable.HashMap[String, String]()
    val hiddenNames = mutable.HashSet[String]()
    var hasWildcard = false
    var allNamesForWildcard = Set.empty[String]
    var hasNonUsedImplicits = false

    def addAllNames(ref: ScStableCodeReference, nameToAdd: String): Unit = {
      if (ref.multiResolveScala(false).exists(shouldAddName)) allNames += nameToAdd
    }

    val deepRef = deepestQualifier(qualifier)
    val rootUsed = deepRef.textMatches(_root_prefix)

    val (prefixQualifier, isRelative) =
      if (rootUsed) (explicitQualifierString(qualifier, withDeepest = false), false)
      else {
        val qualifiedDeepRef =
          try qualifiedRef(deepRef)
          catch {
            case _: IllegalStateException => return None
          }
        val prefixQual = qualifiedDeepRef + withDot(explicitQualifierString(qualifier, withDeepest = false))
        val relative = !deepRef.textMatches(qualifiedDeepRef)
        (prefixQual, relative)
      }

    for (selector <- imp.selectors if !selector.isWildcardSelector) {
      val importUsed: ImportSelectorUsed = ImportSelectorUsed(selector)
      if (isImportUsed(importUsed)) {
        importsUsed += importUsed
        for (reference <- selector.reference;
             refName = reference.refName) {
          if (selector.isAliasedImport) {
            for (importedName <- selector.importedName) {
              if (importedName == "_") {
                hiddenNames += refName
              } else if (importedName == refName) {
                singleNames += refName
                addAllNames(reference, refName)
              } else {
                renames += ((refName, importedName))
                addAllNames(reference, importedName)
              }
            }
          } else {
            singleNames += refName
            addAllNames(reference, refName)
          }
        }
      }
    }

    if (imp.selectorSet.isEmpty && !imp.hasWildcardSelector) {
      val importUsed: ImportExprUsed = ImportExprUsed(imp)
      if (isImportUsed(importUsed)) {
        importsUsed += importUsed
        imp.reference match {
          case Some(ref) =>
            singleNames += ref.refName
            addAllNames(ref, ref.refName)
          case None => //something is not valid
        }
      }
    } else if (imp.hasWildcardSelector) {
      val importUsed =
        if (imp.selectorSet.isEmpty) ImportExprUsed(imp)
        else ImportWildcardSelectorUsed(imp)
      if (isImportUsed(importUsed)) {
        importsUsed += importUsed
        hasWildcard = true
        val (namesForWildcard, implicitNames) = collectAllNamesAndImplicitsFromWildcard(prefixQualifier, imp)
        allNames ++= namesForWildcard
        allNamesForWildcard = namesForWildcard
        hasNonUsedImplicits = (implicitNames -- singleNames).nonEmpty
      }
    }

    if (ImportInfoProvider.providers.exists(_.isImportUsedWithFileCheck(imp)))
      importsUsed += ImportExprUsed(imp)

    if (importsUsed.isEmpty)
      return None //all imports are empty

    allNames --= hiddenNames

    val relativeQualifier =
      if (isRelative) Some(explicitQualifierString(qualifier, withDeepest = true))
      else None

    val isStableImport = {
      deepRef.resolve() match {
        case named: PsiNamedElement => ScalaPsiUtil.hasStablePath(named)
        case _ => false
      }
    }

    Some(
      new ImportInfo(
        prefixQualifier,
        relativeQualifier,
        allNames.toSet,
        singleNames.toSet,
        renames.toMap,
        hiddenNames.toSet,
        hasWildcard,
        rootUsed,
        isStableImport,
        allNamesForWildcard,
        hasNonUsedImplicits
      )
    )
  }

  def merge(infos: IterableOnce[ImportInfo]): Option[ImportInfo] = infos.iterator.reduceOption(_ merge _)

  private def withDot(s: String): String = {
    if (s.isEmpty) "" else "." + s
  }

  @tailrec
  private def isRelativeObject(o: ScObject, res: Boolean = false): Boolean = {
    o.getContext match {
      case _: ScTemplateBody =>
        o.containingClass match {
          case containingObject: ScObject => isRelativeObject(containingObject, res = true)
          case _ => false //inner of some class/trait
        }
      case _: ScPackaging | _: ScalaFile => true
      case _ => res //something in default package or in local object
    }
  }

  @tailrec
  private def deepestQualifier(ref: ScStableCodeReference): ScStableCodeReference = {
    ref.qualifier match {
      case Some(q) => deepestQualifier(q)
      case None => ref
    }
  }

  private def packageFqn(p: PsiPackage): String = {
    p.getParentPackage match {
      case null => fixName(p.getName)
      case parent if parent.getName == null => fixName(p.getName)
      case parent => packageFqn(parent) + "." + fixName(p.getName)
    }
  }

  private def fixName(s: String) = ScalaNamesUtil.escapeKeyword(s)

  @tailrec
  private def explicitQualifierString(ref: ScStableCodeReference, withDeepest: Boolean, res: String = ""): String = {
    ref.qualifier match {
      case Some(q) => explicitQualifierString(q, withDeepest, ref.refName + withDot(res))
      case None if withDeepest && ref.refName != _root_prefix => ref.refName + withDot(res)
      case None => res
    }
  }

  private def qualifiedRef(ref: ScStableCodeReference): String = {
    if (ref.textMatches(_root_prefix))
      return _root_prefix

    val refName = ref.refName
    ref.bind() match {
      case Some(ScalaResolveResult(p: PsiPackage, _)) =>
        if (p.getParentPackage != null && p.getParentPackage.getName != null) packageFqn(p)
        else refName
      case Some(ScalaResolveResult(o: ScObject, _)) =>
        if (isRelativeObject(o)) o.qualifiedName
        else refName
      case Some(ScalaResolveResult(c: PsiClass, _)) =>
        val parts = c.qualifiedName.split('.')
        if (parts.length > 1) parts.map(fixName).mkString(".") else refName
      case Some(ScalaResolveResult(td: ScTypedDefinition, _)) =>
        ScalaPsiUtil.nameContext(td) match {
          case m: ScMember =>
            m.containingClass match {
              case o: ScObject if isRelativeObject(o, res = true) =>
                o.qualifiedName + withDot(refName)
              case _ => refName
            }
          case _ => refName
        }
      case Some(ScalaResolveResult(f: PsiField, _)) =>
        val clazzFqn = f.containingClass match {
          case null => throw new IllegalStateException() //something is wrong
          case clazz => clazz.qualifiedName.split('.').map(fixName).mkString(".")
        }
        clazzFqn + withDot(refName)
      case _ =>
        if (isScriptRef(ref))
          refName
        else
          throw new IllegalStateException() //do not process invalid import
    }
  }

  private def isScriptRef(ref: ScStableCodeReference): Boolean = {
    // TODO: maybe we should create a separate extension point with a dedicated purpose?
    //  Currently ImportInfoProvider is reused just because it's only implementation (for ammonite) was equal to
    //  internal implementation isScriptRef
    val importExpr = new ContextsIterator(ref).findByType[ScImportExpr]
    importExpr.exists(imp => ImportInfoProvider.providers.exists(_.isImportUsedWithFileCheck(imp)))
  }

  private def collectAllNamesAndImplicitsFromWildcard(qualifier: String, place: PsiElement): (Set[String], Set[String]) = {
    val namesForWildcard = mutable.HashSet[String]()
    val implicitNames = mutable.HashSet[String]()
    val refText = qualifier + ".someIdentifier"
    val reference = ScalaPsiElementFactory.createReferenceFromText(refText, place.getContext, place)
      .asInstanceOf[ScStableCodeReferenceImpl]
    val processor = new CompletionProcessor(StdKinds.stableImportSelector, reference, withImplicitConversions = true) {
      override val includePrefixImports = false
    }

    for {
      rr <- reference.doResolve(processor)
      if shouldAddName(rr)
    } {
      val named = rr.element
      val nameToAdd = fixName(named.name)
      namesForWildcard += nameToAdd
      if (ScalaPsiUtil.isImplicit(named))
        implicitNames += nameToAdd
    }
    (namesForWildcard.toSet, implicitNames.toSet)
  }

  private def shouldAddName(resolveResult: ScalaResolveResult): Boolean = {
    resolveResult.element match {
      case _: PsiPackage => true
      case m: PsiMethod => m.containingClass != null
      case td: ScTypedDefinition if td.isStable => true
      case _: ScTypeAlias => true
      case _: PsiClass => true
      case f: PsiField => f.hasFinalModifier
      case _ => false
    }
  }
}
