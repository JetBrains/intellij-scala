package org.jetbrains.plugins.scala.annotator.intention


import java.awt.Point

import com.intellij.codeInsight.completion.JavaCompletionUtil
import com.intellij.codeInsight.daemon.impl.actions.AddImportAction
import com.intellij.codeInsight.hint.{HintManager, HintManagerImpl, QuestionAction}
import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.codeInsight.{FileModificationService, JavaProjectCodeInsightSettings}
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
import javax.swing.Icon
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.intention.ScalaImportTypeFix.TypeToImport
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.formatting.settings.ScalaCodeStyleSettings
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeProjection
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScInfixExpr, ScMethodCall, ScPostfixExpr, ScPrefixExpr}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScTypeAlias
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScPackaging
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.imports.ScImportStmt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{ScPackage, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createDocLinkValue
import org.jetbrains.plugins.scala.lang.psi.impl.{ScPackageImpl, ScalaPsiManager}
import org.jetbrains.plugins.scala.lang.psi.{ScImportsHolder, ScalaPsiUtil}
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocResolvableCodeReference
import org.jetbrains.plugins.scala.settings._
import org.jetbrains.plugins.scala.util.OrderingUtil

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.Sorting

/**
  * User: Alexander Podkhalyuzin
  * Date: 15.07.2009
  */

class ScalaImportTypeFix(private var classes: Array[TypeToImport], ref: ScReference)
  extends HintAction with HighPriorityAction {

  private val project = ref.getProject

  def getText: String = {
    if (classes.length == 1) ScalaBundle.message("import.with", classes(0).qualifiedName)
    else byType(classes)(
      ScalaBundle.message("import.class"),
      ScalaBundle.message("import.package"),
      ScalaBundle.message("import.something")
    )
  }

  def getFamilyName: String = ScalaBundle.message("import.class")

  def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = file.isInstanceOf[ScalaFile]

  def invoke(project: Project, editor: Editor, file: PsiFile) {
    CommandProcessor.getInstance().runUndoTransparentAction(() => {
      if (ref.isValid) {
        classes = ScalaImportTypeFix.getTypesToImport(ref)
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
        classes = ScalaImportTypeFix.getTypesToImport(ref)
        classes.length match {
          case 0 => false
          case 1 if ScalaApplicationSettings.getInstance().ADD_UNAMBIGUOUS_IMPORTS_ON_THE_FLY &&
            !caretNear(editor) =>
            CommandProcessor.getInstance().runUndoTransparentAction(() => {
              new ScalaAddImportAction(editor, classes, ref).execute()
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

  private def fixesAction(editor: Editor) {
    ApplicationManager.getApplication.invokeLater(() => {
      if (ref.isValid && ref.resolve() == null && !HintManagerImpl.getInstanceImpl.hasShownHintsThatWillHideByOtherHint(true)) {
        val action = new ScalaAddImportAction(editor, classes, ref)

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

  private def byType(toImport: Array[TypeToImport])(classes: String, packages: String, mixed: String) = {
    val toImportSeq = toImport.toSeq
    if (toImportSeq.forall(_.element.isInstanceOf[PsiClass])) classes
    else if (toImportSeq.forall(_.element.isInstanceOf[PsiPackage])) packages
    else mixed
  }

  override def startInWriteAction(): Boolean = true

  class ScalaAddImportAction(editor: Editor, classes: Array[TypeToImport], ref: ScReference) extends QuestionAction {
    def addImportOrReference(clazz: TypeToImport) {
      ApplicationManager.getApplication.invokeLater(() =>
        if (ref.isValid && FileModificationService.getInstance.prepareFileForWrite(ref.getContainingFile))
          executeWriteActionCommand("Add import action") {
            PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument)
            if (ref.isValid)
              ref match {
                case _: ScDocResolvableCodeReference => ref.replace(createDocLinkValue(clazz.qualifiedName)(ref.getManager))
                case _ =>
                  clazz match {
                    case ScalaImportTypeFix.PrefixPackageToImport(pack) => ref.bindToPackage(pack, addImport = true)
                    case _ => ref.bindToElement(clazz.element)
                  }
              }
          }(clazz.element.getProject)
      )
    }

    def chooseClass() {
      val title = byType(classes)(
        ScalaBundle.message("import.class.chooser.title"),
        ScalaBundle.message("import.package.chooser.title"),
        ScalaBundle.message("import.something.chooser.title")
      )
      val popup: BaseListPopupStep[TypeToImport] = new BaseListPopupStep[TypeToImport](title, classes: _*) {
        override def getIconFor(aValue: TypeToImport): Icon =
          aValue.element.getIcon(0)

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
    protected type E <: PsiNamedElement

    def element: E

    def name: String = element.name

    def qualifiedName: String

    def isAnnotationType: Boolean = false

    def isValid: Boolean = element.isValid
  }

  object TypeToImport {

    def unapply(`type`: TypeToImport): Some[(PsiNamedElement, String)] =
      Some(`type`.element, `type`.name)
  }

  case class ClassTypeToImport(element: PsiClass) extends TypeToImport {

    override protected type E = PsiClass

    def qualifiedName: String = element.qualifiedName

    override def isAnnotationType: Boolean = element.isAnnotationType
  }

  case class TypeAliasToImport(element: ScTypeAlias) extends TypeToImport {

    override protected type E = ScTypeAlias

    def qualifiedName: String = {
      val name = element.name

      val clazz = element.containingClass
      if (clazz == null || clazz.qualifiedName == "") name
      else clazz.qualifiedName + "." + name
    }
  }

  case class PrefixPackageToImport(element: ScPackage) extends TypeToImport {

    override protected type E = ScPackage

    def qualifiedName: String = element.getQualifiedName
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

  @tailrec
  def notInner(clazz: PsiClass, ref: PsiElement): Boolean = {
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

  type Sorter = (Seq[TypeToImport], ScReference) => Array[TypeToImport]

  private val sorter: Sorter = sortImportsByPackageDistance

  def getTypesToImport(ref: ScReference): Array[TypeToImport] = {
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

    val buffer = new ArrayBuffer[TypeToImport]

    classes.flatMap {
      case df: ScTypeDefinition => df.fakeCompanionModule ++: Seq(df)
      case c => Seq(c)
    }.filter(shouldAddClass).foreach(buffer += ClassTypeToImport(_))

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
        case ClassTypeToImport(clazz) =>
          clazz.isInstanceOf[ScObject] &&
            clazz.asInstanceOf[ScObject].allFunctionsByName("apply").nonEmpty
        case _ => false
      }
    } else buffer

    sorter(finalImports, ref)
  }

  def sortImportsByName(imports: Seq[TypeToImport], originalRef: ScReference): Array[TypeToImport] = {
    import OrderingUtil.implicits.PackageNameOrdering
    imports.toArray.sortBy(_.qualifiedName)
  }

  /**
   * Sorts a list of possible imports for an unresolved reference.
   *
   * 1. To sort packages, we first get all package qualifier that appear
   *    in import statements that are relevant for `originalRef`.
   *    Additionally we use the qualifier of the current package.
   *    (lets call them context packages)
   *
   * 2. For each import candidate we calculate the minimal distance to all context packages.
   *    For example qulifier `com.libA.blub` has distance of 2 to qualifier `com.libA.blabla`.
   *    Note that two packages qualifier are not related if they do not share at least the first two package names.
   *
   * 3. We sort the candidates according to that distance.
   *    If two candidates have the same distance we sort them according to their names.
   *    Further, we prefer inner packages to outer packages
   *    (i.e com.a.org.inner.Target should be higher up the list than com.a.Target
   *     iff com.a.org.SomethingElse appears in one of the context imports)
   *    Also we give a little preference to candidates that are near the curren package.
   *
   * @param importCandidates the possible imports
   * @param originalRef the reference for which the import should be added
   * @return the sorted list of possible imports
   */
  def sortImportsByPackageDistance(importCandidates: Seq[TypeToImport],
                                   originalRef: ScReference): Array[TypeToImport] = {

    if (importCandidates.size <= 1)
      return importCandidates.toArray

    val packaging = originalRef.containingScalaFile.flatMap(_.firstPackaging)
    val packageQualifier = packaging.map(_.fullPackageName)
    val ctxImports = getRelevantImports(originalRef)

    val ctxImportRawQualifiers = packageQualifier.toSeq ++
      ctxImports
        .flatMap(_.importExprs)
        .flatMap(e => Option(e.qualifier))
        .map(_.qualName)
    val ctxImportQualifiers = ctxImportRawQualifiers.distinct.map(_.split('.')).toArray


    val weightedCandidate =
      for (candidate <- importCandidates.toArray) yield {
        val candidateQualifier = candidate.qualifiedName.split('.').init
        assert(candidateQualifier.nonEmpty)

        val (dist, prefixLen, bestIdx) = minPackageDistance(candidateQualifier, ctxImportQualifiers)

        val weight =
          if (prefixLen >= 2) {
            var weightMod = 0

            // We want inner packages before outer packages
            // base.whereOrgRefWas.inner.Ref
            // base.Ref
            if (bestIdx >= 0 && prefixLen == ctxImportQualifiers(bestIdx).length) {
              weightMod -= 1
            }

            // if the candidate is nearest to the current package move it further up the import list
            if (packageQualifier.isDefined && bestIdx == 0 && prefixLen >= 2) {
              weightMod -= 6
            }

            dist * 2 + weightMod
          } else {
            specialPackageWeight.getOrElse(candidateQualifier.head, Int.MaxValue)
          }

        weight -> candidate
      }

    import OrderingUtil.implicits.PackageNameOrdering
    Sorting.quickSort(weightedCandidate)(Ordering.by { case (w, impt) => (w, impt.qualifiedName) })

    weightedCandidate.map(_._2)
  }

  val specialPackageWeight: Map[String, Int] = Map(
    "scala" -> 10000,
    "java"  -> 100000
  )

  // calculates the distance between two package qualifiers
  // two qualifiers that don't share the first two package names are not related at all!
  private def minPackageDistance(qulifier: Seq[String], qualifiers: Seq[Array[String]]): (Int, Int, Int) =
    if (qualifiers.isEmpty) (Int.MaxValue, 0, -1)
    else (for ((t, idx) <- qualifiers.iterator.zipWithIndex) yield {
      val prefixLen = seqCommonPrefixSize(qulifier, t)
      val dist =
        if (prefixLen >= 2) qulifier.length + t.length - 2 * prefixLen
        else Int.MaxValue

      (dist, prefixLen, idx)
    }).minBy(_._1)

  private def seqCommonPrefixSize(fst: Seq[String], snd: Seq[String]): Int =
    fst.zip(snd).iterator.takeWhile(Function.tupled(_ == _)).length

  @tailrec
  private def getRelevantImports(e: PsiElement, foundImports: Seq[ScImportStmt] = Seq.empty): Seq[ScImportStmt] = {
    val found = e match {
      case null => return foundImports
      case holder: ScImportsHolder => foundImports ++ holder.getImportStatements
      case _ => foundImports
    }

    getRelevantImports(e.getParent, found)
  }
}