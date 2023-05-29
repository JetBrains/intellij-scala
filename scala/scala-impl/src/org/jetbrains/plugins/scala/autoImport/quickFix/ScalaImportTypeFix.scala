package org.jetbrains.plugins.scala.autoImport.quickFix

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.UnresolvedReferenceFixProvider
import org.jetbrains.plugins.scala.autoImport.ImportOrderings.defaultImportOrdering
import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportElementFix.isExcluded
import org.jetbrains.plugins.scala.autoImport.quickFix.ScalaImportTypeFix.getTypesToImport
import org.jetbrains.plugins.scala.autoImport.{GlobalMember, GlobalTypeAlias}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.getCompanionModule
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.resolve.ResolveTargets
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.{isAccessible, kindMatches}
import org.jetbrains.plugins.scala.settings._

final class ScalaImportTypeFix private (ref: ScReference)
  extends ScalaImportElementFix[ElementToImport](ref) {

  override def getText: String = elements match {
    case Seq(head) =>
      ScalaBundle.message("import.with", head.qualifiedName)
    case _ =>
      ElementToImport.messageByType(elements)(
        ScalaBundle.message("import.class"),
        ScalaBundle.message("import.package"),
        ScalaBundle.message("import.something")
      )
  }

  override protected def findElementsToImport(): Seq[ElementToImport] = getTypesToImport(ref)

  override def shouldShowHint(): Boolean = {
    val settings = ScalaApplicationSettings.getInstance()
    val psiElements = elements.view.map(_.element: PsiElement)
    val showForClasses = psiElements.exists(_.is[PsiClass, ScTypeAlias]) && settings.SHOW_IMPORT_POPUP_CLASSES
    val showForMethods = psiElements.exists(_.is[PsiMethod, ScTypedDefinition]) && settings.SHOW_IMPORT_POPUP_STATIC_METHODS
    super.shouldShowHint() && (showForClasses || showForMethods)
  }

  override def getFamilyName: String = ScalaBundle.message("import.class")

  override def isAvailable: Boolean =
    super.isAvailable && ref.qualifier.isEmpty && !isSugarCallReference

  private def isSugarCallReference: Boolean = ref.getContext match {
    case ScSugarCallExpr(_, `ref`, _) => true
    case _ => false
  }

  override def createAddImportAction(editor: Editor): ScalaAddImportAction[_, _] =
    ScalaAddImportAction(editor, ref, elements)

  override def isAddUnambiguous: Boolean = ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY
}

object ScalaImportTypeFix {

  final class Provider extends UnresolvedReferenceFixProvider {
    override def fixesFor(reference: ScReference): Seq[IntentionAction] = Seq(ScalaImportTypeFix(reference))
  }

  def apply(reference: ScReference) = new ScalaImportTypeFix(reference)

  @annotation.tailrec
  private[this] def notInner(clazz: PsiClass, ref: PsiElement): Boolean = clazz match {
    case o: ScObject if o.isSyntheticObject =>
      val companion = getCompanionModule(o)
      companion match {
        case Some(cl) => notInner(cl, ref)
        case _ => true
      }
    case t: ScTypeDefinition =>
      t.getParent match {
        case _: ScalaFile |
             _: ScPackaging => true
        case _: ScTemplateBody =>
          t.containingClass match {
            case obj: ScObject if isAccessible(obj, ref) => notInner(obj, ref)
            case _ => false
          }
        case _ => false
      }
    case _ => true
  }

  def getTypesToImport(ref: ScReference): Seq[ElementToImport] = {
    if (!ref.isValid || ref.is[ScTypeProjection])
      return Seq.empty

    implicit val project: Project = ref.getProject

    val kinds = ref.getKinds(incomplete = false)
    val manager = ScalaPsiManager.instance(project)

    def enrichKinds(element: PsiNamedElement): Set[ResolveTargets.Value] =
      if (!kinds.contains(ResolveTargets.OBJECT)) kinds
      else element match {
        case cls: ScClass if isCaseOrInScala3File(cls) =>
          kinds + ResolveTargets.CLASS // SCL-19992, SCL-21187
        case _: ScEnum => kinds + ResolveTargets.CLASS // SCL-20846
        case _ => kinds
      }

    def kindMatchesAndIsAccessible(named: PsiNamedElement): Boolean = named match {
      case member: PsiMember => kindMatches(member, enrichKinds(named)) && isAccessible(member, ref)
      case _ => false
    }

    val predicate: PsiClass => Boolean = ref.getParent match {
      case _: ScMethodCall => hasApplyMethod
      case _ => Function.const(true)
    }

    val referenceName = ref.refName

    val classesWithName = manager.getClassesByName(referenceName, ref.resolveScope)
    val classesWithNameFiltered = classesWithName.filter { clazz =>
      clazz != null &&
        clazz.qualifiedName != null &&
        kindMatchesAndIsAccessible(clazz) &&
        notInner(clazz, ref) &&
        predicate(clazz)
    }
    val classesToImport = classesWithNameFiltered.map(ClassToImport.apply)

    val aliasesToImport: Iterable[MemberToImport] = for {
      alias  <- manager.getTypeAliasesByName(referenceName, ref.resolveScope)
      global <- GlobalMember.findGlobalMembers(alias, ref.resolveScope)(GlobalTypeAlias)

      if kindMatchesAndIsAccessible(alias)

    } yield MemberToImport(alias, global.owner, global.pathToOwner)

    //it's possible to have same qualified name with different owners in case of val overriding
    val distinctAliasesToImport: Iterator[MemberToImport] =
      aliasesToImport.iterator.distinctBy(_.qualifiedName)

    val packagesList = importsWithPrefix(referenceName).map { s =>
      s.reverse.dropWhile(_ != '.').tail.reverse
    }

    val packages = for {
      packageQualifier <- packagesList
      pack <- ScPackageImpl.findPackage(packageQualifier)(manager)
      if kindMatches(pack, kinds)
    } yield PrefixPackageToImport(pack)

    val elementsAll = classesToImport ++ distinctAliasesToImport ++ packages
    val elementsFiltered = elementsAll.filterNot(e => isExcluded(e.qualifiedName, project))
    val elementsSorted = elementsFiltered.sorted(defaultImportOrdering(ref))
    elementsSorted
  }

  private def hasApplyMethod(`class`: PsiClass): Boolean = `class` match {
    case `object`: ScObject => `object`.allFunctionsByName(ScFunction.CommonNames.Apply).nonEmpty
    case cls: ScClass => isCaseOrInScala3File(cls) // SCL-19992, SCL-21187
    case _ => false
  }

  private def isCaseOrInScala3File(cls: ScClass): Boolean =
    cls.isCase || cls.isInScala3File

  private def importsWithPrefix(prefix: String)
                               (implicit project: Project) =
    ScalaCodeStyleSettings.getInstance(project)
      .getImportsWithPrefix
      .filter {
        case exclude if exclude.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX) => false
        case include =>
          include.split('.') match {
            case parts if parts.length < 2 => false
            case parts => parts(parts.length - 2) == prefix
          }
      }
}
