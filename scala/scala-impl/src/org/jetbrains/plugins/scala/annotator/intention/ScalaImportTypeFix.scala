package org.jetbrains.plugins.scala
package annotator
package intention

import java.awt.Point

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.completion.JavaCompletionUtil.isInExcludedPackage
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.HintAction
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.externalHighlighters.ScalaHighlightingMode
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil.{getCompanionModule, hasStablePath}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScMethodCall, ScSugarCallExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValueOrVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
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
final class ScalaImportTypeFix private(private var classes_ : Array[ElementToImport], ref: ScReference)
  extends HintAction with HighPriorityAction {

  import ScalaImportTypeFix._

  def classes: Array[ElementToImport] = classes_

  def isValid: Boolean = classes.nonEmpty

  override def getText: String = classes match {
    case Array(head) =>
      ScalaBundle.message("import.with", head.qualifiedName)
    case _ =>
      ElementToImport.messageByType(classes)(
        ScalaBundle.message("import.class"),
        ScalaBundle.message("import.package"),
        ScalaBundle.message("import.something")
      )
  }

  override def getFamilyName: String = ScalaBundle.message("import.class")

  override def isAvailable(project: Project,
                           editor: Editor,
                           file: PsiFile): Boolean = file.hasScalaPsi

  override def invoke(project: Project,
                      editor: Editor,
                      file: PsiFile): Unit = executeUndoTransparentAction {
    if (ref.isValid) {
      classes_ = getTypesToImport(ref)
      addImportAction(editor).execute()
    }
  }

  override def showHint(editor: Editor): Boolean = {
    if (!ref.isValid ||
      ref.qualifier.isDefined ||
      isShowErrorsFromCompilerEnabled) return false

    ref.getContext match {
      case ScSugarCallExpr(_, `ref`, _) => false
      case _ =>
        classes_ = getTypesToImport(ref)
        classes.length match {
          case 0 => false
          case 1 if ScalaApplicationSettings.getInstance.ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY &&
            !caretNear(editor) =>
            executeUndoTransparentAction {
              addImportAction(editor).execute()
            }
            false
          case _ =>
            invokeLater {
              fixesAction(editor)
            }
            true
        }
    }
  }

  private def caretNear(editor: Editor): Boolean = ref.getTextRange.grown(1).contains(editor.getCaretModel.getOffset)

  private def fixesAction(implicit editor: Editor): Unit = {
    val hintManager = HintManagerImpl.getInstanceImpl
    val TextRangeExt(refStart, refEnd) = ref.getTextRange

    if (ref.isValid &&
      ref.resolve() == null &&
      classes.nonEmpty &&
      !hintManager.hasShownHintsThatWillHideByOtherHint(true) &&
      editorRange.containsOffset(refStart) &&
      refEnd < editor.getDocument.getTextLength) {
      val hintText = ScalaBundle.message(
        "import.hint.text",
        classes.head.qualifiedName,
        if (classes.length == 1) "" else ScalaBundle.message("import.multiple.choices")
      )

      hintManager.showQuestionHint(
        editor,
        hintText,
        refStart,
        refEnd,
        addImportAction
      )
    }
  }

  override def startInWriteAction: Boolean = true

  private def addImportAction(implicit editor: Editor) =
    ScalaAddImportAction(ref, classes)

  private def isShowErrorsFromCompilerEnabled =
    ScalaHighlightingMode.isShowErrorsFromCompilerEnabled(ref.getContainingFile)
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

  private def getTypesToImport(ref: ScReference): Array[ElementToImport] = {
    if (!ref.isValid || ref.isInstanceOf[ScTypeProjection])
      return Array.empty

    implicit val project: Project = ref.getProject

    val kinds = ref.getKinds(incomplete = false)
    val manager = ScalaPsiManager.instance(project)

    def kindMatchesAndIsAccessible(member: PsiMember) =
      kindMatches(member, kinds) &&
        isAccessible(member, ref)

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

    val functions = for {
      CompanionObject(companion) <- ref.withContexts.toIterable

      function <- companion.allFunctionsByName(referenceName)
      if kindMatchesAndIsAccessible(function)
    } yield MethodToImport(function)

    val members = for {
      CompanionObject(companion) <- ref.withContexts.toIterable

      ValueOrVariable(member) <- companion.members
      if isAccessible(member, ref)

      definition <- member.declaredElements
      if kindMatches(definition, kinds)
    } yield DefinitionToImport(definition)

    val aliases = for {
      alias <- manager.getStableAliasesByName(referenceName, ref.resolveScope)

      containingClass = alias.containingClass

      if containingClass != null &&
        hasStablePath(alias) &&
        kindMatchesAndIsAccessible(alias) &&
        !isInExcludedPackage(containingClass, false)

    } yield TypeAliasToImport(alias)

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
      functions ++
      members ++
      aliases ++
      packages)
      .sortBy(_.qualifiedName)(orderingByRelevantImports(ref))
      .toArray
  }

  private def editorRange(implicit editor: Editor) = {
    val visibleRectangle = editor.getScrollingModel.getVisibleArea

    val startPosition = editor.xyToLogicalPosition(visibleRectangle.getLocation)
    val startOffset = editor.logicalPositionToOffset(startPosition)

    val endPosition = editor.xyToLogicalPosition(new Point(visibleRectangle.x + visibleRectangle.width, visibleRectangle.y + visibleRectangle.height))
    val endOffset = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0))

    TextRange.create(
      startOffset,
      startOffset max endOffset
    )
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

  // todo to be unified!
  /**
   * @see [[lang.completion.global.CompanionObjectMembersFinder]]
   */
  private object CompanionObject {

    def unapply(constructorOwner: ScConstructorOwner): Option[ScObject] =
      constructorOwner match {
        case _: ScClass |
             _: ScTrait =>
          constructorOwner.baseCompanionModule.filterByType[ScObject]
        case _ => None
      }
  }

  private object ValueOrVariable {

    def unapply(member: ScMember): Option[ScValueOrVariable] = member match {
      case member: ScValueOrVariable => Some(member)
      case _ => None
    }
  }
}