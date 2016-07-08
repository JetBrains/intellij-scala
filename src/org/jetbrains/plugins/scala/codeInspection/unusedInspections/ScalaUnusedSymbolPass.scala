package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import java.util.Collections

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl._
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInspection._
import com.intellij.lang.annotation.{Annotation, AnnotationSession, HighlightSeverity}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.importsTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.suppression.ScalaInspectionSuppressor
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.VarCouldBeValInspection
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.completion.ScalaKeyword
import org.jetbrains.plugins.scala.lang.findUsages.ScalaFindUsagesUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackageLike, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.mutable

class ScalaUnusedSymbolPass(file: ScalaFile, editor: Editor) extends TextEditorHighlightingPass(file.getProject, editor.getDocument) {
  private val highlightInfos = mutable.Buffer[HighlightInfo]()

  private val inspectionSuppressor = new ScalaInspectionSuppressor

  def doCollectInformation(progress: ProgressIndicator): Unit = {
    if (shouldHighlightFile) {
      highlightInfos.clear()
      processFile()
    }
  }

  private def shouldHighlightFile: Boolean = HighlightingLevelManager.getInstance(file.getProject).shouldInspect(file)

  def doApplyInformationToEditor() {
    import scala.collection.JavaConversions._
    if (shouldHighlightFile) {
      UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, editor.getDocument, 0, file.getTextLength,
        highlightInfos, getColorsScheme, getId)
    } else {
      UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, editor.getDocument, 0, file.getTextLength,
        Collections.emptyList(), getColorsScheme, getId)
    }
  }

  private def processFile(): Unit = {
    val annotationHolder = new AnnotationHolderImpl(new AnnotationSession(file))
    val annotations = mutable.Buffer[Annotation]()
    val config: UnusedConfig = readConfig
    val state = UnusedPassState(annotationHolder, annotations, config)

    file.depthFirst.foreach {
      case x: ScDeclaredElementsHolder => processDeclaredElementHolder(x, state)
      case _ =>
    }

    highlightInfos ++= annotations.map(HighlightInfo.fromAnnotation)
  }

  def readConfig: UnusedConfig = {
    val profile: InspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile
    def isEnabled(shortName: String) = profile.isToolEnabled(HighlightDisplayKey.find(shortName), file)
    def severity(shortName: String) = {
      val key = HighlightDisplayKey.find(shortName)
      key.toOption.map(profile.getErrorLevel(_, file).getSeverity).orNull
    }
    val localUnusedShortName = ScalaUnusedSymbolInspection.ShortName
    val localAssignShortName = VarCouldBeValInspection.ShortName
    UnusedConfig(isEnabled(localUnusedShortName), severity(localUnusedShortName),
      isEnabled(localAssignShortName), severity(localAssignShortName))
  }

  private def processDeclaredElementHolder(x: ScDeclaredElementsHolder, state: UnusedPassState) {
    x.getContext match {
      case _: ScPackageLike | _: ScalaFile | _: ScEarlyDefinitions => // ignore, too expensive to check for references.
      case _: ScTemplateBody =>
        x match {
          case mem: ScMember if mem.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis) =>
            processLocalDeclaredElementHolder(x, state)
          case _ =>
        }
      case _ => processLocalDeclaredElementHolder(x, state)
    }
  }

  /** Processes a ScDeclaredElementsHolder that is not accessible outside of the defining class/companion, ie locals or private or private[this] */
  private def processLocalDeclaredElementHolder(declElementHolder: ScDeclaredElementsHolder, state: UnusedPassState) {
    val isSpecialDef = declElementHolder match {
      case x: PsiMethod => ScFunction.isSpecial(x.name)
      case _ => false
    }
    val isImplicit = declElementHolder match {
      case x: ScMember => x.hasModifierProperty(ScalaKeyword.IMPLICIT)
      case _ => false
    }
    if (!isSpecialDef && !isImplicit) {
      if (!isUnusedSymbolSuppressed(declElementHolder) && state.config.checkLocalUnused) {
        checkUnused(declElementHolder, state)
      }
      declElementHolder match {
        case varDef: ScVariableDefinition if !isVarCouldBeValSuppressed(varDef) && state.config.checkLocalAssign =>
          checkVarCouldBeVal(varDef, state)
        case _ =>
      }
    }
  }

  private def checkUnused(declaredElementsHolder: ScDeclaredElementsHolder, state: UnusedPassState): Unit = {
    val elements = declaredElementsHolder.declaredElements
    elements.collect {
      case scalaElement: ScNamedElement => scalaElement
    } foreach { named =>
      val holder = ScalaRefCountHolder.getInstance(file)
      var used = false
      val succeeded = holder.retrieveUnusedReferencesInfo { () =>
        if (holder.isValueUsed(named)) {
          used = true
        }
      }

      if (succeeded && !used) {
        val elementType = ScalaFindUsagesUtil.getType(declaredElementsHolder)
        val severity = state.config.localUnusedSeverity
        val message = s"$elementType '${named.name}' is never used"
        val key = HighlightDisplayKey.find(ScalaUnusedSymbolInspection.ShortName)
        val range = named.nameId.getTextRange
        val annotation = state.annotationHolder.createAnnotation(severity, range, message)
        annotation.registerFix(new DeleteElementFix(named), range, key)
        state.annotations += annotation
      }
    }
  }

  private def checkVarCouldBeVal(varDef: ScVariableDefinition, state: UnusedPassState): Unit = {
    val elements = varDef.declaredElements
    var couldBeVal = true
    var used = false
    var succeeded = false
    elements.foreach { elem =>
      val holder = ScalaRefCountHolder.getInstance(file)
      succeeded = holder.retrieveUnusedReferencesInfo { () =>
        if (holder.isValueWriteUsed(elem)) {
          couldBeVal = false
        }
        if (holder.isValueUsed(elem)) {
          used = true
        }
      }
    }
    if (succeeded && couldBeVal && used) {
      val (annotationHint, fixHint) = varDef.declaredElements match {
        case Seq(n: ScNamedElement) => (s"var '${n.name}' could be a val", s"Convert var `${n.name}` to val")
        case _ => ("var could be a val", "Convert var to val")
      }
      val severity = state.config.localAssignSeverity
      val start = varDef.varKeyword.getTextRange.getStartOffset
      val end = varDef.getTextRange.getEndOffset
      val range = TextRange.create(start, end)
      val annotation = state.annotationHolder.createAnnotation(severity, range, annotationHint)
      val key = HighlightDisplayKey.find(VarCouldBeValInspection.ShortName)
      val fix = new VarToValFix(varDef, fixHint)
      annotation.registerFix(fix, range, key)
      state.annotations += annotation
    }
  }

  import scala.collection.JavaConversions._
  override def getInfos: java.util.List[HighlightInfo] = highlightInfos.toList

  private def isUnusedSymbolSuppressed(element: PsiElement) = {
    inspectionSuppressor.isSuppressedFor(element, ScalaUnusedSymbolInspection.ShortName)
  }

  private def isVarCouldBeValSuppressed(element: PsiElement) = {
    inspectionSuppressor.isSuppressedFor(element, VarCouldBeValInspection.ShortName)
  }
}

class DeleteElementFix(e: ScNamedElement) extends LocalQuickFixAndIntentionActionOnPsiElement(e) {
  override def getText: String = {
    Option(getStartElement).filter(_.isValid).collect {
      case named: PsiNamedElement =>
        val elementToClassify = ScalaPsiUtil.nameContext(named)
        val elementTypeDesc = ScalaFindUsagesUtil.getType(elementToClassify)
        s"Remove $elementTypeDesc '${named.name}'"
    }.getOrElse(getFamilyName)
  }

  def getFamilyName: String = "Remove unused element"

  override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit = {
    if (FileModificationService.getInstance.prepareFileForWrite(startElement.getContainingFile)) {
      PsiDocumentManager.getInstance(project).commitAllDocuments()
      startElement.delete()
    }
  }
}

class VarToValFix(elem: ScVariableDefinition, text: String) extends LocalQuickFixAndIntentionActionOnPsiElement(elem) {
  override def getText: String = text

  override def getFamilyName: String = "Convert var to val"

  override def invoke(project: Project, file: PsiFile, editor: Editor, startElement: PsiElement, endElement: PsiElement): Unit = {
    startElement match {
      case varDef: ScVariableDefinition =>
        if (FileModificationService.getInstance.prepareFileForWrite(varDef.getContainingFile)) {
          varDef.replace(ScalaPsiElementFactory.createValFromVarDefinition(varDef, varDef.getManager))
        }
      case _ =>
    }
  }
}

case class UnusedConfig(checkLocalUnused: Boolean, localUnusedSeverity: HighlightSeverity,
                        checkLocalAssign: Boolean, localAssignSeverity: HighlightSeverity)
case class UnusedPassState(annotationHolder: AnnotationHolderImpl, annotations: mutable.Buffer[Annotation], config: UnusedConfig)
