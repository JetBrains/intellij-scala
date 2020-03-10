package org.jetbrains.plugins.scala.annotator.intention


import java.awt.Point

import com.intellij.codeInsight.JavaProjectCodeInsightSettings
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl}
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInspection.HintAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaHighlightingMode
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScPostfixExpr, ScPrefixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.settings._
import org.jetbrains.plugins.scala.util.OrderingUtil

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

/**
  * User: Alexander Podkhalyuzin
  * Date: 15.07.2009
  */

class ScalaImportTypeFix(private var classes: Array[ElementToImport], ref: ScReference)
  extends HintAction with HighPriorityAction {

  private val project = ref.getProject

  override def getText: String = {
    if (classes.length == 1) ScalaBundle.message("import.with", classes(0).qualifiedName)
    else ElementToImport.messageByType(classes)(
      ScalaBundle.message("import.class"),
      ScalaBundle.message("import.package"),
      ScalaBundle.message("import.something")
    )
  }

  override def getFamilyName: String = ScalaBundle.message("import.class")

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = file.isInstanceOf[ScalaFile] || file.findAnyScalaFile.isDefined

  override def invoke(project: Project, editor: Editor, file: PsiFile): Unit = {
    CommandProcessor.getInstance().runUndoTransparentAction(() => {
      if (ref.isValid) {
        classes = ScalaImportTypeFix.getTypesToImport(ref)
        ScalaAddImportAction(editor, classes, ref).execute()
      }
    })
  }

  override def showHint(editor: Editor): Boolean = {
    val compilerErrorsEnabled = Option(ref.getContainingFile)
      .exists(ScalaHighlightingMode.isShowErrorsFromCompilerEnabled)
    if (compilerErrorsEnabled) return false
    if (!ref.isValid) return false
    if (ref.qualifier.isDefined) return false
    ref.getContext match {
      case postf: ScPostfixExpr if postf.operation == ref => false
      case pref: ScPrefixExpr if pref.operation == ref => false
      case inf: ScInfixExpr if inf.operation == ref => false
      case _ =>
        classes = ScalaImportTypeFix.getTypesToImport(ref)
        classes.length match {
          case 0 => false
          case 1 if ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY &&
            !caretNear(editor) =>
            CommandProcessor.getInstance().runUndoTransparentAction(() => {
              ScalaAddImportAction(editor, classes, ref).execute()
            })
            false
          case _ =>
            fixesAction(editor)
            true
        }
    }
  }

  private def caretNear(editor: Editor): Boolean = ref.getTextRange.grown(1).contains(editor.getCaretModel.getOffset)

  private def range(editor: Editor) = {
    val visibleRectangle = editor.getScrollingModel.getVisibleArea
    val startPosition = editor.xyToLogicalPosition(new Point(visibleRectangle.x, visibleRectangle.y))
    val myStartOffset = editor.logicalPositionToOffset(startPosition)
    val endPosition = editor.xyToLogicalPosition(new Point(visibleRectangle.x + visibleRectangle.width, visibleRectangle.y + visibleRectangle.height))
    val myEndOffset = myStartOffset max editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0))
    new TextRange(myStartOffset, myEndOffset)
  }

  private def startOffset(editor: Editor) = range(editor).getStartOffset

  private def endOffset(editor: Editor) = range(editor).getEndOffset

  private def fixesAction(editor: Editor): Unit = {
    ApplicationManager.getApplication.invokeLater(() => {
      if (ref.isValid && ref.resolve() == null && !HintManagerImpl.getInstanceImpl.hasShownHintsThatWillHideByOtherHint(true)) {
        val action = ScalaAddImportAction(editor, classes, ref)

        val refStart = ref.getTextRange.getStartOffset
        val refEnd = ref.getTextRange.getEndOffset
        if (classes.nonEmpty &&
            refStart >= startOffset(editor) &&
            refStart <= endOffset(editor) &&
            editor != null &&
            refEnd < editor.getDocument.getTextLength) {
          HintManager.getInstance().showQuestionHint(editor,
            if (classes.length == 1) classes(0).qualifiedName + "? Alt+Enter"
            else classes(0).qualifiedName + "? (multiple choices...) Alt+Enter",
            refStart,
            refEnd,
            action)
        }
      }
    })
  }

  override def startInWriteAction(): Boolean = true
}

object ScalaImportTypeFix {

  @tailrec
  private def notInner(clazz: PsiClass, ref: PsiElement): Boolean = {
    clazz match {
      case o: ScObject if o.isSyntheticObject =>
        ScalaPsiUtil.getCompanionModule(o) match {
          case Some(cl) => notInner(cl, ref)
          case _ => true
        }
      case t: ScTypeDefinition =>
        t.getParent match {
          case _: ScalaFile => true
          case _: ScPackaging => true
          case _: ScTemplateBody =>
            Option(t.containingClass) match {
              case Some(obj: ScObject) => ResolveUtils.isAccessible(obj, ref) && notInner(obj, ref)
              case _ => false
            }
          case _ => false
        }
      case _ => true
    }
  }

  def getTypesToImport(ref: ScReference): Array[ElementToImport] = {
    if (!ref.isValid || ref.isInstanceOf[ScTypeProjection])
      return Array.empty

    val project = ref.getProject

    val kinds = ref.getKinds(incomplete = false)
    val cache = ScalaPsiManager.instance(project)
    val classes = cache.getClassesByName(ref.refName, ref.resolveScope)

    def shouldAddClass(clazz: PsiClass) = {
      clazz != null &&
        clazz.qualifiedName != null &&
        clazz.qualifiedName.indexOf(".") > 0 &&
        ResolveUtils.kindMatches(clazz, kinds) &&
        notInner(clazz, ref) &&
        ResolveUtils.isAccessible(clazz, ref) &&
        !JavaCompletionUtil.isInExcludedPackage(clazz, false)
    }

    val buffer = new ArrayBuffer[ElementToImport]

    classes.flatMap {
      case df: ScTypeDefinition => df.fakeCompanionModule ++: Seq(df)
      case c => Seq(c)
    }.filter(shouldAddClass).foreach(buffer += ClassToImport(_))

    val typeAliases = cache.getStableAliasesByName(ref.refName, ref.resolveScope)
    for (alias <- typeAliases) {
      val containingClass = alias.containingClass
      if (containingClass != null && ScalaPsiUtil.hasStablePath(alias) &&
        ResolveUtils.kindMatches(alias, kinds) && ResolveUtils.isAccessible(alias, ref) &&
        !JavaCompletionUtil.isInExcludedPackage(containingClass, false)) {
        buffer += TypeAliasToImport(alias)
      }
    }

    val packagesList = ScalaCodeStyleSettings.getInstance(project).getImportsWithPrefix.filter {
      case exclude if exclude.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX) => false
      case include =>
        val parts = include.split('.')
        if (parts.length > 1) parts.takeRight(2).head == ref.refName
        else false
    }.map(s => s.reverse.dropWhile(_ != '.').tail.reverse)

    for (packageQualifier <- packagesList) {
      val pack = ScPackageImpl.findPackage(project, packageQualifier)
      if (pack != null && pack.getQualifiedName.indexOf('.') != -1 && ResolveUtils.kindMatches(pack, kinds) &&
        !JavaProjectCodeInsightSettings.getSettings(project).isExcluded(pack.getQualifiedName)) {
        buffer += PrefixPackageToImport(pack)
      }
    }

    val finalImports = if (ref.getParent.isInstanceOf[ScMethodCall]) {
      buffer.filter {
        case ClassToImport(clazz) =>
          clazz.isInstanceOf[ScObject] &&
            clazz.asInstanceOf[ScObject].allFunctionsByName("apply").nonEmpty
        case _ => false
      }
    } else buffer

    implicit val byRelevance: Ordering[String] = OrderingUtil.orderingByRelevantImports(ref)
    finalImports.sortBy(_.qualifiedName).toArray
  }
}