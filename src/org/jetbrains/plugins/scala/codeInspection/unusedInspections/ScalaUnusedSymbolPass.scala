package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import java.util.Collections

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl._
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection._
import com.intellij.lang.annotation.{Annotation, AnnotationSession, HighlightSeverity}
import com.intellij.lang.findUsages.{FindUsagesProvider, LanguageFindUsages}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.annotator.importsTracker.ScalaRefCountHolder
import org.jetbrains.plugins.scala.codeInspection.varCouldBeValInspection.VarCouldBeValInspection
import org.jetbrains.plugins.scala.extensions.{toObjectExt, toPsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackageLike, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory

import scala.collection.mutable

// TODO merge with UnusedImportPass (?)
class ScalaUnusedSymbolPass(file: PsiFile, editor: Editor) extends TextEditorHighlightingPass(file.getProject, editor.getDocument) {
  val findUsageProvider: FindUsagesProvider = LanguageFindUsages.INSTANCE.forLanguage(ScalaFileType.SCALA_LANGUAGE)
  val highlightInfos = mutable.Buffer[HighlightInfo]()

  case class UnusedConfig(checkLocalUnused: Boolean, localUnusedSeverity: HighlightSeverity,
                          checkLocalAssign: Boolean, localAssignSeverity: HighlightSeverity)
  case class UnusedPassState(annotationHolder: AnnotationHolderImpl, annotations: mutable.Buffer[Annotation], config: UnusedConfig)

  def doCollectInformation(progress: ProgressIndicator) {}

  def doApplyInformationToEditor() {
    file match {
      case sFile: ScalaFile if HighlightingLevelManager.getInstance(file.getProject).shouldInspect(file) =>
        processScalaFile(sFile)
        import scala.collection.JavaConversions._
        UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, editor.getDocument, 0, file.getTextLength,
          highlightInfos, getColorsScheme, getId)
        highlightInfos.clear()
      case sFile: ScalaFile =>
        UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, editor.getDocument, 0, file.getTextLength,
          Collections.emptyList(), getColorsScheme, getId)
      case _ =>
    }
  }

  private def processScalaFile(sFile: ScalaFile) {
    val annotationHolder = new AnnotationHolderImpl(new AnnotationSession(file))
    val annotations = mutable.Buffer[Annotation]()
    val state = UnusedPassState(annotationHolder, annotations, readConfig(sFile))
    val config = state.config
    if (!config.checkLocalAssign && !config.checkLocalUnused) return

    sFile.depthFirst.foreach {
      case x: ScDeclaredElementsHolder => processDeclaredElementHolder(x, state)
      case _ =>
    }

    highlightInfos ++= annotations.map(HighlightInfo.fromAnnotation)
  }

  def readConfig(sFile: ScalaFile): UnusedConfig = {
    val profile: InspectionProfile = InspectionProjectProfileManager.getInstance(myProject).getInspectionProfile
    def isEnabled(shortName: String) = profile.isToolEnabled(HighlightDisplayKey.find(shortName), sFile)
    def severity(shortName: String) = {
      val key = HighlightDisplayKey.find(shortName)
      key.toOption.map(profile.getErrorLevel(_, sFile).getSeverity).orNull
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
          case _ => // ignore.
        }
      case _ if state.config.checkLocalAssign || state.config.checkLocalUnused =>
        processLocalDeclaredElementHolder(x, state)
      case _ =>
    }
  }

  /** Processes a ScDeclaredElementsHolder that is not accessible outside of the defining class/companion, ie locals or private or private[this] */
  private def processLocalDeclaredElementHolder(declElementHolder: ScDeclaredElementsHolder, state: UnusedPassState) {
    val isSpecialDef = declElementHolder match {
      case x: PsiMethod => ScFunction.isSpecial(x.name)
      case _ => false
    }
    val isImplicit = declElementHolder match {
      case x: ScMember => x.hasModifierProperty("implicit")
      case _ => false
    }
    if (!isSpecialDef && !isImplicit) {
      checkUnusedAndVarCouldBeVal(declElementHolder, state)
    }
  }

  /** Highlight unused local symbols, and vals that could be vars */
  private def checkUnusedAndVarCouldBeVal(declElementHolder: ScDeclaredElementsHolder, state: UnusedPassState) {
    val isVar = declElementHolder.isInstanceOf[ScVariableDefinition]

    var hasAssign = !state.config.checkLocalAssign || !isVar
    var hasAtLeastOneUnusedHighlight = false
    var hasAtLeastOneAssign = false
    val decElemIterator = declElementHolder.declaredElements.iterator
    while (decElemIterator.hasNext) {
      val elem = decElemIterator.next()
      elem match {
        case decElem: ScNamedElement =>
          val holder = ScalaRefCountHolder.getInstance(file)
          var used = false
          val runnable = new Runnable {
            def run() {
              if (holder.isValueWriteUsed(decElem)) {
                hasAssign = true
                used = true
              }
              if (holder.isValueReadUsed(decElem)) {
                used = true
              }
            }
          }
          holder.retrieveUnusedReferencesInfo(runnable)
          if (!used && state.config.checkLocalUnused) {
            hasAtLeastOneUnusedHighlight = true
            val elementTypeDesc = findUsageProvider.getType(declElementHolder)
            val severity = state.config.localUnusedSeverity
            val message = "%s '%s' is never used".format(elementTypeDesc, decElem.name)
            val annotation = state.annotationHolder.createAnnotation(severity, decElem.nameId.getTextRange, message)
            annotation.registerFix(new DeleteElementFix(elem))
            state.annotations += annotation
          }
          if (hasAssign) hasAtLeastOneAssign = true
        case _ =>
      }
    }
    if (isVar && !hasAtLeastOneAssign && !hasAtLeastOneUnusedHighlight) {
      val (message, nameOpt) = declElementHolder.declaredElements match {
        case Seq(n: ScNamedElement) =>
          ("var '%s' could be a val".format(n.name), Some(n.name))
        case _ =>
          ("var could be a val", None)
      }
      val severity = state.config.localAssignSeverity
      val annotation = state.annotationHolder.createAnnotation(severity, declElementHolder.getTextRange, message)
      annotation.registerFix(new VarToValFix(declElementHolder.asInstanceOf[ScVariableDefinition], nameOpt))
      state.annotations += annotation
    }
  }

  import scala.collection.JavaConversions._
  override def getInfos: java.util.List[HighlightInfo] = highlightInfos.toList
}

class DeleteElementFix(element: PsiElement) extends IntentionAction {
  def getText: String = {
    val provider: FindUsagesProvider = LanguageFindUsages.INSTANCE.forLanguage(ScalaFileType.SCALA_LANGUAGE)
    element match {
      case n: ScNamedElement =>
        val elementToClassify = ScalaPsiUtil.nameContext(n)
        val elementTypeDesc = provider.getType(elementToClassify)
        "Remove %s '%s'".format(elementTypeDesc, n.name)
      case x =>
        val elementTypeDesc = provider.getType(x)
        "Remove %s".format(elementTypeDesc)
    }
  }

  def getFamilyName: String = "Remove unused element"

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    file.getManager.isInProject(file) && file.isInstanceOf[ScalaFile]
  }

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return
    PsiDocumentManager.getInstance(project).commitAllDocuments()
    IdeDocumentHistory.getInstance(project).includeCurrentPlaceAsChangePlace()
    element.delete()
  }
}

class VarToValFix(varDef: ScVariableDefinition, name: Option[String]) extends IntentionAction {
  def getText: String = {
    name match {
      case Some(n) => "Convert var '%s' to val".format(n)
      case None => "Convert var to val"
    }
  }

  def getFamilyName: String = "Convert var to val"

  def startInWriteAction: Boolean = true

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    file.getManager.isInProject(file) && file.isInstanceOf[ScalaFile]
  }

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (!varDef.isValid) return
    if (!FileModificationService.getInstance.prepareFileForWrite(file)) return
    varDef.replace(ScalaPsiElementFactory.createValFromVarDefinition(varDef, varDef.getManager))
  }
}

