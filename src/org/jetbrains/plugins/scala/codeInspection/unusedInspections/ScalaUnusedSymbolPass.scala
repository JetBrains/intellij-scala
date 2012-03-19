package org.jetbrains.plugins.scala
package codeInspection
package unusedInspections

import com.intellij.codeInspection._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.Editor
import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.openapi.progress.ProgressIndicator
import collection.mutable.Buffer
import com.intellij.lang.annotation.{Annotation, AnnotationSession}
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.CodeInsightUtilBase
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import varCouldBeValInspection.VarCouldBeValInspection
import com.intellij.lang.findUsages.{LanguageFindUsages, FindUsagesProvider}
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackageLike, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScMember
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScVariableDefinition, ScDeclaredElementsHolder}
import com.intellij.psi._
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl._
import analysis.HighlightLevelUtil
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import annotator.importsTracker.ScalaRefCountHolder
import extensions.toPsiNamedElementExt

// TODO merge with UnusedImportPass (?)
class ScalaUnusedSymbolPass(file: PsiFile, editor: Editor) extends TextEditorHighlightingPass(file.getProject, editor.getDocument) {
  val findUsageProvider: FindUsagesProvider = LanguageFindUsages.INSTANCE.forLanguage(ScalaFileType.SCALA_LANGUAGE)
  val highlightInfos = Buffer[HighlightInfo]()

  case class UnusedConfig(checkLocalUnused: Boolean, checkLocalAssign: Boolean)
  case class UnusedPassState(annotationHolder: AnnotationHolderImpl, annotations: Buffer[Annotation], config: UnusedConfig)

  def doCollectInformation(progress: ProgressIndicator) {}

  def doApplyInformationToEditor() {
    file match {
      case sFile: ScalaFile if HighlightLevelUtil.shouldInspect(file) =>
        processScalaFile(sFile)
        import scala.collection.JavaConversions._
        UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, editor.getDocument, 0, file.getTextLength,
          highlightInfos, getColorsScheme, getId)
        highlightInfos.clear()
      case _ =>
    }
  }

  private def processScalaFile(sFile: ScalaFile) {
    val annotationHolder = new AnnotationHolderImpl(new AnnotationSession(file))
    val annotations = Buffer[Annotation]()
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
    UnusedConfig(checkLocalUnused = isEnabled(ScalaUnusedSymbolInspection.ShortName),
      checkLocalAssign = isEnabled(VarCouldBeValInspection.ShortName))
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
            val annotation = state.annotationHolder.createWarningAnnotation(decElem.nameId, "%s '%s' is never used".format(elementTypeDesc, decElem.name))
            annotation.setHighlightType(ProblemHighlightType.LIKE_UNUSED_SYMBOL)
            annotation.registerFix(new DeleteElementFix(elem))
            state.annotations += annotation
          }
          if (hasAssign) hasAtLeastOneAssign = true
        case _ =>
      }
    }
    if (isVar && !hasAtLeastOneAssign && !hasAtLeastOneUnusedHighlight) {
      val (messgae, nameOpt) = declElementHolder.declaredElements match {
        case Seq(n: ScNamedElement) =>
          ("var '%s' could be a val".format(n.name), Some(n.name))
        case _ =>
          ("var could be a val", None)
      }
      val annotation = state.annotationHolder.createWeakWarningAnnotation(declElementHolder, messgae)
      annotation.registerFix(new VarToValFix(declElementHolder.asInstanceOf[ScVariableDefinition], nameOpt))
      state.annotations += annotation
    }
  }

  private def createUnusedSymbolInfo(element: PsiElement, message: String, highlightInfoType: HighlightInfoType): HighlightInfo = {
    return HighlightInfo.createHighlightInfo(highlightInfoType, element, message)
  }
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
    return file.getManager.isInProject(file) && file.isInstanceOf[ScalaFile]
  }

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return
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
    return file.getManager.isInProject(file) && file.isInstanceOf[ScalaFile]
  }

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    if (!varDef.isValid) return
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return
    varDef.replace(ScalaPsiElementFactory.createValFromVarDefinition(varDef, varDef.getManager))
  }
}

