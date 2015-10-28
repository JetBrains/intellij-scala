package org.jetbrains.plugins.scala.annotator.intention


import java.awt.Point
import javax.swing.Icon

import com.intellij.codeInsight.{JavaProjectCodeInsightSettings, FileModificationService}
import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction
import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl, QuestionAction}
import com.intellij.codeInspection.HintAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.{Editor, LogicalPosition}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.{JBPopupFactory, PopupStep}
import com.intellij.openapi.util.TextRange
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ObjectUtils
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.TypeToImport
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScPostfixExpr, ScPrefixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.packaging.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiElementFactory, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.settings._
import org.jetbrains.plugins.scala.util.ScalaUtils

import scala.collection.mutable.ArrayBuffer

/**
 * User: Alexander Podkhalyuzin
 * Date: 15.07.2009
 */

class ScalaImportTypeFix(private var classes: Array[TypeToImport], ref: ScReferenceElement) extends {
    val project = ref.getProject
  } with  HintAction {
  val getText = ScalaBundle.message("import.with", classes(0).qualifiedName)

  def getFamilyName = ScalaBundle.message("import.class")

  def isAvailable(project: Project, editor: Editor, file: PsiFile) = file.isInstanceOf[ScalaFile]

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    CommandProcessor.getInstance().runUndoTransparentAction(new Runnable {
      def run() {
        if (!ref.isValid) return
        classes = ScalaImportTypeFix.getTypesToImport(ref, project)
        new ScalaAddImportAction(editor, classes, ref).execute()
      }
    })
  }

  def showHint(editor: Editor): Boolean = {
    if (!ref.isValid) return false
    if (ref.qualifier.isDefined) return false
    ref.getContext match {
      case postf: ScPostfixExpr if postf.operation == ref => false
      case pref: ScPrefixExpr if pref.operation == ref => false
      case inf: ScInfixExpr if inf.operation == ref => false
      case _ =>
        classes = ScalaImportTypeFix.getTypesToImport(ref, project)
        classes.length match {
          case 0 => false
          case 1 if ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY &&
                  !caretNear(editor) =>
            CommandProcessor.getInstance().runUndoTransparentAction(new Runnable {
              def run() {
                new ScalaAddImportAction(editor, classes, ref).execute()
              }
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
    val myEndOffset = editor.logicalPositionToOffset(new LogicalPosition(endPosition.line + 1, 0))
    new TextRange(myStartOffset, myEndOffset)
  }

  private def startOffset(editor: Editor) = range(editor).getStartOffset
  private def endOffset(editor: Editor) = range(editor).getEndOffset

  private def fixesAction(editor: Editor) {
    ApplicationManager.getApplication.invokeLater(new Runnable {
      def run() {
        if (!ref.isValid) return
        if (ref.resolve != null) return

        if (HintManagerImpl.getInstanceImpl.hasShownHintsThatWillHideByOtherHint(true)) return
        val action = new ScalaAddImportAction(editor, classes, ref: ScReferenceElement)

        val offset = ref.getTextRange.getStartOffset
        if (classes.nonEmpty && offset >= startOffset(editor) && offset <= endOffset(editor) && editor != null &&
                offset <= editor.getDocument.getTextLength) {
          HintManager.getInstance().showQuestionHint(editor,
          if (classes.length == 1) classes(0).qualifiedName + "? Alt+Enter"
          else classes(0).qualifiedName + "? (multiple choices...) Alt+Enter",
          offset,
          offset + ref.getTextLength,
          action)
          return
        }
      }
    })
  }

  def startInWriteAction(): Boolean = true

  class ScalaAddImportAction(editor: Editor, classes: Array[TypeToImport], ref: ScReferenceElement) extends QuestionAction {
    def addImportOrReference(clazz: TypeToImport) {
      ApplicationManager.getApplication.invokeLater(new Runnable() {
        def run() {
          if (!ref.isValid || !FileModificationService.getInstance.prepareFileForWrite(ref.getContainingFile)) return
          ScalaUtils.runWriteAction(new Runnable {
            def run() {
              PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
              if (!ref.isValid) return
              if (!ref.isInstanceOf[ScDocResolvableCodeReference]) ref.bindToElement(clazz.element)
              else ref.replace(ScalaPsiElementFactory.createDocLinkValue(clazz.qualifiedName, ref.getManager))
            }
          }, clazz.getProject, "Add import action")
        }
      })
    }

    def chooseClass() {
      val popup = new BaseListPopupStep[TypeToImport](QuickFixBundle.message("class.to.import.chooser.title"), classes : _*) {
        override def getIconFor(aValue: TypeToImport): Icon = {
          aValue.getIcon
        }

        override def getTextFor(value: TypeToImport): String = {
          ObjectUtils.assertNotNull(value.qualifiedName)
        }

        override def isAutoSelectionEnabled: Boolean = false

        import com.intellij.openapi.ui.popup.PopupStep.FINAL_CHOICE
        override def onChosen(selectedValue: TypeToImport, finalChoice: Boolean): PopupStep[_] = {
          if (selectedValue == null) {
            return FINAL_CHOICE
          }
          if (finalChoice) {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            addImportOrReference(selectedValue)
            return FINAL_CHOICE
          }
          val qname: String = selectedValue.qualifiedName
          if (qname == null) return FINAL_CHOICE
          val toExclude: java.util.List[String] = AddImportAction.getAllExcludableStrings(qname)
          new BaseListPopupStep[String](null, toExclude) {
            override def onChosen(selectedValue: String, finalChoice: Boolean): PopupStep[_] = {
              if (finalChoice) {
                AddImportAction.excludeFromImport(project, selectedValue)
              }
              super.onChosen(selectedValue, finalChoice)
            }

            override def getTextFor(value: String): String = {
              "Exclude '" + value + "' from auto-import"
            }
          }
        }

        override def hasSubstep(selectedValue: TypeToImport): Boolean = {
          true
        }
      }
      JBPopupFactory.getInstance.createListPopup(popup).showInBestPositionFor(editor)
    }

    def execute: Boolean = {
      for (clazz <- classes if !clazz.isValid) return false

      PsiDocumentManager.getInstance(project).commitAllDocuments()
      if (classes.length == 1) {
        addImportOrReference(classes(0))
      }
      else chooseClass()

      true
    }
  }
}

object ScalaImportTypeFix {
  sealed trait TypeToImport {
    def name: String
    def qualifiedName: String
    def element: PsiNamedElement
    def isAnnotationType: Boolean = false
    def isValid: Boolean = element.isValid
    def getIcon: Icon = element.getIcon(0)
    def getProject: Project = element.getProject
  }

  object TypeToImport {
    def unapply(elem: PsiElement): Option[TypeToImport] = {
      elem match {
        case clazz: PsiClass => Some(ClassTypeToImport(clazz))
        case ta: ScTypeAlias => Some(TypeAliasToImport(ta))
        case pack: ScPackage => Some(PrefixPackageToImport(pack))
        case _ => None
      }
    }
  }

  case class ClassTypeToImport(clazz: PsiClass) extends TypeToImport {
    def name: String = clazz.name
    def qualifiedName: String = clazz.qualifiedName
    def element: PsiNamedElement = clazz
    override def isAnnotationType: Boolean = clazz.isAnnotationType
  }

  case class TypeAliasToImport(ta: ScTypeAlias) extends TypeToImport {
    def name: String = ta.name
    def qualifiedName: String = {
      val clazz: ScTemplateDefinition = ta.containingClass
      if (clazz == null || clazz.qualifiedName == "") ta.name
      else clazz.qualifiedName + "." + ta.name
    }
    def element: PsiNamedElement = ta
  }

  case class PrefixPackageToImport(pack: ScPackage) extends TypeToImport {
    def name: String = pack.name
    def qualifiedName: String = pack.getQualifiedName
    def element: PsiNamedElement = pack
  }

  def getImportHolder(ref: PsiElement, project: Project): ScImportsHolder = {
    if (ScalaCodeStyleSettings.getInstance(project).isAddImportMostCloseToReference)
      PsiTreeUtil.getParentOfType(ref, classOf[ScImportsHolder])
    else {
      PsiTreeUtil.getParentOfType(ref, classOf[ScPackaging]) match {
        case null => ref.getContainingFile match {
          case holder: ScImportsHolder => holder
          case file =>
            throw new AssertionError(s"Holder is wrong, file text: ${file.getText}")
        }
        case packaging: ScPackaging => packaging
      }
    }
  }

  private def notInner(clazz: PsiClass, ref: PsiElement): Boolean = {
    def parent(t: ScTypeDefinition): PsiElement = {
      val stub = t.asInstanceOf[ScTypeDefinitionImpl].getStub
      if (stub != null) stub.getParentStub.getPsi
      else t.getParent
    }
    clazz match {
      case o: ScObject if o.isSyntheticObject =>
        ScalaPsiUtil.getCompanionModule(o) match {
          case Some(cl) => notInner(cl, ref)
          case _ => true
        }
      case t: ScTypeDefinition =>
        parent(t) match {
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

  def getTypesToImport(ref: ScReferenceElement, myProject: Project): Array[TypeToImport] = {
    if (!ref.isValid) return Array.empty
    if (ref.isInstanceOf[ScTypeProjection]) return Array.empty
    val kinds = ref.getKinds(incomplete = false)
    val cache = ScalaPsiManager.instance(myProject)
    val classes = cache.getClassesByName(ref.refName, ref.getResolveScope)
    val buffer = new ArrayBuffer[TypeToImport]
    for (clazz <- classes) {
      def addClazz(clazz: PsiClass) {
        if (clazz != null && clazz.qualifiedName != null && clazz.qualifiedName.indexOf(".") > 0 &&
          ResolveUtils.kindMatches(clazz, kinds) && notInner(clazz, ref) && ResolveUtils.isAccessible(clazz, ref) &&
          !JavaCompletionUtil.isInExcludedPackage(clazz, false)) {
          buffer += ClassTypeToImport(clazz)
        }
      }
      addClazz(clazz)
      clazz match {
        case c: ScTypeDefinition if c.fakeCompanionModule.isDefined =>
          if (ScalaPsiUtil.getBaseCompanionModule(c).isEmpty) {
            ScalaPsiUtil.getCompanionModule(c) match {
              case Some(companion) => addClazz(companion)
              case _ =>
            }
          }
        case _ =>
      }
    }

    val typeAliases = cache.getStableAliasesByName(ref.refName, ref.getResolveScope)
    for (alias <- typeAliases) {
      val containingClass = alias.containingClass
      if (containingClass != null && ScalaPsiUtil.hasStablePath(alias) &&
        ResolveUtils.kindMatches(alias, kinds) && ResolveUtils.isAccessible(alias, ref) &&
        !JavaCompletionUtil.isInExcludedPackage(containingClass, false)) {
        buffer += TypeAliasToImport(alias)
      }
    }

    val packagesList = ScalaCodeStyleSettings.getInstance(myProject).getImportsWithPrefix.filter {
      case exclude if exclude.startsWith(ScalaCodeStyleSettings.EXCLUDE_PREFIX) => false
      case include =>
        val parts = include.split('.')
        if (parts.length > 1) parts.takeRight(2).head == ref.refName
        else false
    }.map { case s => s.reverse.dropWhile(_ != '.').tail.reverse }

    for (packageQualifier <- packagesList) {
      val pack = ScPackageImpl.findPackage(myProject, packageQualifier)
      if (pack != null && pack.getQualifiedName.indexOf('.') != -1 && ResolveUtils.kindMatches(pack, kinds) &&
        !JavaProjectCodeInsightSettings.getSettings(myProject).isExcluded(pack.getQualifiedName)) {
        buffer += PrefixPackageToImport(pack)
      }
    }

    if (ref.getParent.isInstanceOf[ScMethodCall]) {
      buffer.filter {
        case ClassTypeToImport(clazz) =>
          clazz.isInstanceOf[ScObject] &&
            clazz.asInstanceOf[ScObject].functionsByName("apply").nonEmpty
        case _ => false
      }.sortBy(_.qualifiedName).toArray
    } else buffer.sortBy(_.qualifiedName).toArray
  }
}