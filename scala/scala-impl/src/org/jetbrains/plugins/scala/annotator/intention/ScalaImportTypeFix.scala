package org.jetbrains.plugins.scala
package annotator
package intention

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.completion.JavaCompletionUtil.isInExcludedPackage
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{getCompanionModule, hasStablePath}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScTypeAlias, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils.{isAccessible, kindMatches}
import org.jetbrains.plugins.scala.settings._
import org.jetbrains.plugins.scala.util.OrderingUtil.orderingByRelevantImports

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.07.2009
 */
final class ScalaImportTypeFix private (override val elements: Seq[ElementToImport],
                                        ref: ScReference)
  extends ScalaImportElementFix(ref) {

  override def getTextInner: String = elements match {
    case Seq(head) =>
      ScalaBundle.message("import.with", head.presentationBody)
    case _ =>
      ElementToImport.messageByType(elements)(
        ScalaBundle.message("import.class"),
        ScalaBundle.message("import.package"),
        ScalaBundle.message("import.something")
      )
  }

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

  def apply(reference: ScReference) = new ScalaImportTypeFix(
    getTypesToImport(reference),
    reference
  )

  @annotation.tailrec
  private[this] def notInner(clazz: PsiClass, ref: PsiElement): Boolean = clazz match {
    case o: ScObject if o.isSyntheticObject =>
      getCompanionModule(o) match {
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

  def getTypesToImport(ref: ScReference): Array[ElementToImport] = {
    if (!ref.isValid || ref.isInstanceOf[ScTypeProjection])
      return Array.empty

    implicit val project: Project = ref.getProject

    val kinds = ref.getKinds(incomplete = false)
    val manager = ScalaPsiManager.instance(project)

    def kindMatchesAndIsAccessible(named: PsiNamedElement) = named match {
      case member: PsiMember => kindMatches(member, kinds) && isAccessible(member, ref)
      case _ => false
    }

    val predicate: PsiClass => Boolean = ref.getParent match {
      case _: ScMethodCall => hasApplyMethod
      case _ => Function.const(true)
    }

    val referenceName = ref.refName
    val classes = for {
      clazz <- manager.getClassesByName(referenceName, ref.resolveScope)
      classOrCompanion <- clazz match {
        case clazz: ScTypeDefinition => clazz.fakeCompanionModule match {
          case Some(companion) => companion :: clazz :: Nil
          case _ => clazz :: Nil
        }
        case _ => clazz :: Nil
      }

      if classOrCompanion != null &&
        classOrCompanion.qualifiedName != null &&
        isQualified(classOrCompanion.qualifiedName) &&
        kindMatchesAndIsAccessible(classOrCompanion) &&
        notInner(classOrCompanion, ref) &&
        !isInExcludedPackage(classOrCompanion, false) &&
        predicate(classOrCompanion)

    } yield ClassToImport(classOrCompanion)

    val aliases = for {
      alias <- manager.getStableAliasesByName(referenceName, ref.resolveScope)

      containingClass = alias.containingClass

      if containingClass != null &&
        hasStablePath(alias) &&
        kindMatchesAndIsAccessible(alias) &&
        !isInExcludedPackage(containingClass, false)

    } yield MemberToImport(alias, containingClass)

    val packagesList = importsWithPrefix(referenceName).map { s =>
      s.reverse.dropWhile(_ != '.').tail.reverse
    }

    val packages = for {
      packageQualifier <- packagesList
      pack = ScPackageImpl.findPackage(packageQualifier)(manager)

      if pack != null &&
        kindMatches(pack, kinds) &&
        !isExcluded(pack.getQualifiedName)

    } yield PrefixPackageToImport(pack)

    (classes ++
      aliases ++
      packages)
      .sortBy(_.qualifiedName)(orderingByRelevantImports(ref))
      .toArray
  }

  private def hasApplyMethod(`class`: PsiClass) = `class` match {
    case `object`: ScObject => `object`.allFunctionsByName(ScFunction.CommonNames.Apply).nonEmpty
    case _ => false
  }

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

  private def isExcluded(qualifiedName: String)
                        (implicit project: Project) =
    !isQualified(qualifiedName) ||
      JavaProjectCodeInsightSettings.getSettings(project).isExcluded(qualifiedName)

  private def isQualified(name: String) =
    name.indexOf('.') != -1
}