package org.jetbrains.plugins.scala.editor.importOptimizer

import com.intellij.psi._
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer._root_prefix
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiMemberExt, PsiModifierListOwnerExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScStableCodeReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportExpr
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.usages.{ImportExprUsed, ImportSelectorUsed, ImportUsed, ImportWildcardSelectorUsed}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScObject}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScStableCodeReferenceElementImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.lang.resolve.processor.CompletionProcessor
import org.jetbrains.plugins.scala.lang.resolve.{ScalaResolveResult, StdKinds}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * @author Nikolay.Tropin
  */
case class ImportInfo(prefixQualifier: String,
                      relative: Option[String],
                      allNames: Set[String],
                      singleNames: Set[String],
                      renames: Map[String, String],
                      hiddenNames: Set[String],
                      hasWildcard: Boolean,
                      rootUsed: Boolean,
                      isStableImport: Boolean = true,
                      allNamesForWildcard: Set[String] = Set.empty,
                      wildcardHasUnusedImplicit: Boolean = false) {

  def withoutRelative: ImportInfo =
    if (relative.isDefined || rootUsed) copy(relative = None) else this

  def split: Seq[ImportInfo] = {
    val result = new ArrayBuffer[ImportInfo]()
    result ++= singleNames.toSeq.sorted.map { name =>
      template.copy(singleNames = Set(name))
    }
    result ++= renames.map { rename =>
      template.copy(renames = Map(rename))
    }
    result ++= hiddenNames.map { hidden =>
      this.toHiddenNameInfo(hidden)
    }
    if (hasWildcard) {
      result += this.toWildcardInfo
    }
    result
  }

  def merge(second: ImportInfo): ImportInfo = {
    val relative = this.relative.orElse(second.relative)
    val rootUsed = relative.isEmpty && (this.rootUsed || second.rootUsed)
    new ImportInfo(this.prefixQualifier, relative,
      this.allNames ++ second.allNames, this.singleNames ++ second.singleNames,
      this.renames ++ second.renames, this.hiddenNames ++ second.hiddenNames,
      this.hasWildcard || second.hasWildcard, rootUsed, this.isStableImport && second.isStableImport,
      this.allNamesForWildcard)
  }

  def isSimpleWildcard = hasWildcard && singleNames.isEmpty && renames.isEmpty && hiddenNames.isEmpty

  def namesFromWildcard: Set[String] = {
    if (hasWildcard) allNames -- singleNames -- renames.keySet
    else Set.empty[String]
  }

  private def template: ImportInfo =
    copy(singleNames = Set.empty, renames = Map.empty, hiddenNames = Set.empty, allNames = allNamesForWildcard, hasWildcard = false)

  def toWildcardInfo: ImportInfo = template.copy(hasWildcard = true)

  def toHiddenNameInfo(name: String): ImportInfo = template.copy(hiddenNames = Set(name))

  def withRootPrefix: ImportInfo = copy(rootUsed = true)
}

object ImportInfo {

  def apply(imp: ScImportExpr, isImportUsed: ImportUsed => Boolean): Option[ImportInfo] = {
    import imp.typeSystem
    def name(s: String) = ScalaNamesUtil.changeKeyword(s)

    val qualifier = imp.qualifier
    if (qualifier == null) return None //ignore invalid imports

    val importsUsed = ArrayBuffer[ImportUsed]()
    val allNames = mutable.HashSet[String]()
    val singleNames = mutable.HashSet[String]()
    val renames = mutable.HashMap[String, String]()
    val hiddenNames = mutable.HashSet[String]()
    var hasWildcard = false
    val namesForWildcard = mutable.HashSet[String]()
    val implicitNames = mutable.HashSet[String]()
    var hasNonUsedImplicits = false

    def shouldAddName(resolveResult: ResolveResult): Boolean = {
      resolveResult match {
        case ScalaResolveResult(p: PsiPackage, _) => true
        case ScalaResolveResult(m: PsiMethod, _) => m.containingClass != null
        case ScalaResolveResult(td: ScTypedDefinition, _) if td.isStable => true
        case ScalaResolveResult(_: ScTypeAlias, _) => true
        case ScalaResolveResult(_: PsiClass, _) => true
        case ScalaResolveResult(f: PsiField, _) => f.hasFinalModifier
        case _ => false
      }
    }

    def addAllNames(ref: ScStableCodeReferenceElement, nameToAdd: String): Unit = {
      if (ref.multiResolve(false).exists(shouldAddName)) allNames += nameToAdd
    }

    def collectAllNamesForWildcard(): Unit = {
      val refText = imp.qualifier.getText + ".someIdentifier"
      val reference = ScalaPsiElementFactory.createReferenceFromText(refText, imp.qualifier.getContext, imp.qualifier)
        .asInstanceOf[ScStableCodeReferenceElementImpl]
      val processor = new CompletionProcessor(StdKinds.stableImportSelector, reference, collectImplicits = true, includePrefixImports = false)

      reference.doResolve(reference, processor).foreach {
        case rr: ScalaResolveResult if shouldAddName(rr) =>
          val element = rr.element
          val nameToAdd = name(element.name)
          namesForWildcard += nameToAdd
          if (ScalaPsiUtil.isImplicit(element))
            implicitNames += nameToAdd
        case _ =>
      }
    }

    collectAllNamesForWildcard()

    if (!imp.singleWildcard && imp.selectorSet.isEmpty) {
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
    } else if (imp.singleWildcard) {
      val importUsed =
        if (imp.selectorSet.isEmpty) ImportExprUsed(imp)
        else ImportWildcardSelectorUsed(imp)
      if (isImportUsed(importUsed)) {
        importsUsed += importUsed
        hasWildcard = true
        allNames ++= namesForWildcard
      }
    }
    for (selector <- imp.selectors) {
      val importUsed: ImportSelectorUsed = ImportSelectorUsed(selector)
      if (isImportUsed(importUsed)) {
        importsUsed += importUsed
        val refName: String = selector.reference.refName
        if (selector.isAliasedImport) {
          val importedName: String = selector.importedName
          if (importedName == "_") {
            hiddenNames += refName
          } else if (importedName == refName) {
            singleNames += refName
            addAllNames(selector.reference, refName)
          } else {
            renames += ((refName, importedName))
            addAllNames(selector.reference, importedName)
          }
        } else {
          singleNames += refName
          addAllNames(selector.reference, refName)
        }
      }
    }
    if (importsUsed.isEmpty) return None //all imports are empty

    allNames --= hiddenNames
    hasNonUsedImplicits = (implicitNames -- singleNames).nonEmpty

    @tailrec
    def deepestQualifier(ref: ScStableCodeReferenceElement): ScStableCodeReferenceElement = {
      ref.qualifier match {
        case Some(q) => deepestQualifier(q)
        case None => ref
      }
    }

    def packageFqn(p: PsiPackage): String = {
      p.getParentPackage match {
        case null => name(p.getName)
        case parent if parent.getName == null => name(p.getName)
        case parent => packageFqn(parent) + "." + name(p.getName)
      }
    }

    @tailrec
    def explicitQualifierString(ref: ScStableCodeReferenceElement, withDeepest: Boolean, res: String = ""): String = {
      ref.qualifier match {
        case Some(q) => explicitQualifierString(q, withDeepest, ref.refName + withDot(res))
        case None if withDeepest && ref.refName != _root_prefix => ref.refName + withDot(res)
        case None => res
      }
    }

    def withDot(s: String): String = {
      if (s.isEmpty) "" else "." + s
    }

    @tailrec
    def isRelativeObject(o: ScObject, res: Boolean = false): Boolean = {
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

    def qualifiedRef(ref: ScStableCodeReferenceElement): String = {
      if (ref.getText == _root_prefix) return _root_prefix

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
          if (parts.length > 1) parts.map(name).mkString(".") else refName
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
            case null => throw new IllegalStateException() //somehting is wrong
            case clazz => clazz.qualifiedName.split('.').map(name).mkString(".")
          }
          clazzFqn + withDot(refName)
        case _ => throw new IllegalStateException() //do not process invalid import
      }
    }

    val deepRef = deepestQualifier(qualifier)
    val rootUsed = deepRef.getText == _root_prefix

    val (prefixQualifier, isRelative) =
      if (rootUsed) (explicitQualifierString(qualifier, withDeepest = false), false)
      else {
        val qualifiedDeepRef =
          try qualifiedRef(deepRef)
          catch {
            case _: IllegalStateException => return None
          }
        val prefixQual = qualifiedDeepRef + withDot(explicitQualifierString(qualifier, withDeepest = false))
        val relative = qualifiedDeepRef != deepRef.getText
        (prefixQual, relative)
      }

    val relativeQualifier =
      if (isRelative) Some(explicitQualifierString(qualifier, withDeepest = true))
      else None

    val isStableImport = {
      deepRef.resolve() match {
        case named: PsiNamedElement => ScalaPsiUtil.hasStablePath(named)
        case _ => false
      }
    }

    Some(new ImportInfo(prefixQualifier, relativeQualifier, allNames.toSet,
      singleNames.toSet, renames.toMap, hiddenNames.toSet, hasWildcard, rootUsed,
      isStableImport, namesForWildcard.toSet, hasNonUsedImplicits))
  }

  def merge(infos: Seq[ImportInfo]): Option[ImportInfo] = infos.reduceOption(_ merge _)
}