package org.jetbrains.plugins.scala.annotator.createFromUsage

import com.intellij.codeInsight.CodeInsightUtilCore
import com.intellij.codeInsight.intention.preview.{IntentionPreviewInfo, IntentionPreviewUtils}
import com.intellij.codeInsight.navigation.{PsiTargetNavigator, TargetPresentationProvider}
import com.intellij.codeInsight.template.{Template, TemplateBuilder, TemplateBuilderImpl}
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi._
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.annotator.TemplateUtils
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.{ScParameterizedTypeElement, ScSimpleTypeElement}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaDirectoryService
import org.jetbrains.plugins.scala.{ScalaBundle, ScalaFileType, isUnitTestMode}

abstract class CreateTypeDefinitionQuickFix(ref: ScReference, kind: ClassKind)
  extends CreateFromUsageQuickFixBase(ref) {

  private final val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.annotator.createFromUsage.CreateTemplateDefinitionQuickFix")
  private val name = ref.refName

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    def goodQualifier = ref.qualifier match {
      case Some(InstanceOfClass(_: ScTypeDefinition)) => true
      case Some(ResolvesTo(_: PsiPackage)) => true
      case None => true
      case _ => false
    }

    super.isAvailable(project, editor, file) && goodQualifier
  }

  override protected def invokeInner(project: Project, editor: Editor, file: PsiFile): Unit = {
    inWriteAction {
      ref.qualifier match {
        case Some(InstanceOfClass(typeDef: ScTypeDefinition)) =>
          createInnerClassIn(typeDef)(editor)
        case Some(ResolvesTo(pack: PsiPackage)) =>
          createClassInPackage(pack)(editor)
        case None =>
          val fileOption = if (file == null || file.getContainingDirectory == null) None else Some(file)
          val possibleSiblings = fileOption ++: getPossibleSiblingsInThisFile(ref)
          createClassWithLevelChoosing(possibleSiblings)(editor)
        case _ =>
      }
    }
  }

  override def generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo = {
    // TODO(SCL-21523, SCL-21524): Fix preview
    IntentionPreviewInfo.EMPTY

//    val refCopy = inCopy(ref, file)
//    refCopy.qualifier match {
//      case Some(InstanceOfClass(typeDef: ScTypeDefinition)) =>
//        createInnerClassIn(inCopy(typeDef, file))
//        IntentionPreviewInfo.DIFF
//      case Some(ResolvesTo(_: PsiPackage)) =>
//        createSyntheticDefinitionForPreview
//      case None =>
//        val possibleSiblings = getPossibleSiblingsInThisFile(refCopy)
//        if (possibleSiblings.exists(!_.is[PsiFile])) {
//          createClassWithLevelChoosing(editor, possibleSiblings)
//          IntentionPreviewInfo.DIFF
//        } else createSyntheticDefinitionForPreview
//      case _ => IntentionPreviewInfo.EMPTY
//    }
  }

  private def getPossibleSiblingsInThisFile(reference: ScReference): Seq[PsiElement] =
    reference.withParentsInFile.collect {
      case inner childOf (_: ScTemplateBody) => inner
      case td: ScTypeDefinition if td.isTopLevel => td
    }.toSeq.reverse

  private def createSyntheticDefinitionForPreview = {
    val text = s"${kind.keyword} $name"
    val file = createScalaFileFromText(text, ref)
    val definition = PsiTreeUtil.findChildOfType(file, classOf[ScTypeDefinition])
    //afterCreationWork(definition, editor)
    val fileType = ScalaFileType.INSTANCE
    new IntentionPreviewInfo.CustomDiff(fileType, name + fileType.getExtensionWithDot, "", file.getText)
  }

  @inline private def inCopy[E <: PsiElement](element: E, file: PsiFile): E =
    PsiTreeUtil.findSameElementInCopy(element, file)

  private def createClassInPackage(psiPackage: PsiPackage)(editor: Editor): Unit = {
    val directory = psiPackage.getDirectories.filter(_.isWritable) match {
      case Array(dir) => dir
      case Array() => throw new IllegalStateException(s"Cannot find directory for the package `${psiPackage.name}`")
      case dirs =>
        val currentDir = dirs.find(PsiTreeUtil.isAncestor(_, ref, true))
          .orElse(dirs.find(ScalaPsiUtil.getModule(_) == ScalaPsiUtil.getModule(ref)))
        currentDir.getOrElse(dirs(0))
    }
    createClassInDirectory(directory)(editor)
  }

  private def createInnerClassIn(target: ScTemplateDefinition)(editor: Editor): Unit = {
    val extBlock = target.extendsBlock
    val targetBody = extBlock.getOrCreateTemplateBody
    createClassIn(targetBody, Some(targetBody.getLastChild))(editor)
  }

  private def createClassIn(parent: PsiElement, anchorAfter: Option[PsiElement])(editor: Editor): Unit = {
    try {
      if (!IntentionPreviewUtils.prepareElementForWrite(parent)) return

      val text = s"${kind.keyword} $name"
      val newTd = createTemplateDefinitionFromText(text, parent, parent.getFirstChild)
      val anchor = anchorAfter.orNull
      parent.addBefore(createNewLine()(parent.getManager), anchor)
      val result = parent.addBefore(newTd, anchor)
      afterCreationWork(result.asInstanceOf[ScTypeDefinition])(editor)
    }
    catch {
      case e: IncorrectOperationException =>
        LOG.error(e)
    }
  }

  private def createClassWithLevelChoosing(siblings: Seq[PsiElement])(editor: Editor): Unit =
    siblings match {
      case Seq() =>
      case Seq(elem) => createClassAtLevel(elem)(editor)
      case _ =>
        val selection = siblings.head
        if (isUnitTestMode || IntentionPreviewUtils.isIntentionPreviewActive) {
          val sibling = siblings.find(!_.is[PsiFile])
            .getOrElse(siblings.head)
          createClassAtLevel(sibling)(editor)
        } else {
          val title = ScalaBundle.message("choose.level.popup.title")
          val processor: PsiElementProcessor[PsiElement] = { elem =>
            inWriteCommandAction(createClassAtLevel(elem)(editor))(elem.getProject)
            false
          }
          val renderer: TargetPresentationProvider[PsiElement] = { element =>
            val text = element match {
              case _: PsiFile => ScalaBundle.message("new.class.location.new.file")
              case td: ScTypeDefinition if td.isTopLevel => ScalaBundle.message("new.class.location.top.level.in.this.file")
              case _ childOf (tb: ScTemplateBody) =>
                val containingClass = PsiTreeUtil.getParentOfType(tb, classOf[ScTemplateDefinition])
                ScalaBundle.message("new.class.location.inner.in.class", containingClass.name)
              case _ => ScalaBundle.message("new.class.location.local.scope")
            }

            TargetPresentation.builder(text).presentation()
          }

          new PsiTargetNavigator(siblings.toArray)
            .selection(selection)
            .presentationProvider(renderer)
            .createPopup(projectContext.project, title, processor)
            .showInBestPositionFor(editor)
        }
    }

  private def createClassAtLevel(sibling: PsiElement)(editor: Editor): Unit = {
    sibling match {
      case file: PsiFile => createClassInDirectory(file.getContainingDirectory)(editor)
      case td: ScTypeDefinition if td.isTopLevel => createClassIn(td.getParent, None)(editor)
      case _ childOf (tb: ScTemplateBody) =>
        createInnerClassIn(PsiTreeUtil.getParentOfType(tb, classOf[ScTemplateDefinition]))(editor)
      case _ =>
    }
  }

  private def createClassInDirectory(directory: PsiDirectory)(editor: Editor): Unit = {
    val clazz = ScalaDirectoryService.createClassFromTemplate(directory, name, kind.templateName, askToDefineVariables = false)
    afterCreationWork(clazz.asInstanceOf[ScTypeDefinition])(editor)
  }

  protected def afterCreationWork(clazz: ScTypeDefinition)(editor: Editor): Unit = {
    addGenericParams(clazz)
    addClassParams(clazz)
    ScalaPsiUtil.adjustTypes(clazz)
    if (!IntentionPreviewUtils.isIntentionPreviewActive)
      runTemplate(clazz)(editor)
  }

  protected def addMoreElementsToTemplate(builder: TemplateBuilder, clazz: ScTypeDefinition): Unit = {}

  private def runTemplate(clazz: ScTypeDefinition)(editor: Editor): Unit = {
    val builder = new TemplateBuilderImpl(clazz)

    addTypeParametersToTemplate(clazz, builder)
    clazz match {
      case cl: ScClass if cl.constructor.exists(_.parameters.nonEmpty) =>
        addParametersToTemplate(cl.constructor.get, builder)
      case _ =>
    }
    addMoreElementsToTemplate(builder, clazz)

    CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(clazz)

    val template: Template = builder.buildTemplate()
    TemplateUtils.positionCursorAndStartTemplate(clazz, template, editor)
  }

  private def addGenericParams(clazz: ScTypeDefinition): Unit = ref.getParent.getParent match {
    case pt: ScParameterizedTypeElement =>
      val paramsText = pt.typeArgList.typeArgs match {
        case args if args.size == 1 => "[T]"
        case args => args.indices.map(i => s"T${i + 1}").mkString("[", ", ", "]")
      }
      val nameId = clazz.nameId
      val clause = createTypeParameterClauseFromTextWithContext(paramsText, clazz, nameId)
      clazz.addAfter(clause, nameId)
    case _ =>
  }

  private def addClassParams(clazz: ScTypeDefinition): Unit = {
    clazz match {
      case cl: ScClass =>
        val constr = cl.constructor.get
        val text = parametersText(ref)
        val parameters = createClassParamClausesWithContext(text, constr)
        constr.parameterList.replace(parameters)
      case _ =>
    }
  }
}

class CreateObjectQuickFix(ref: ScReference)
  extends CreateTypeDefinitionQuickFix(ref, Object) {

  override val getText: String = ScalaBundle.message("create.object.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.object")
}


class CreateTraitQuickFix(ref: ScReference)
  extends CreateTypeDefinitionQuickFix(ref, Trait) {

  override val getText: String = ScalaBundle.message("create.trait.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.trait")

  override def isAvailable(project: Project, editor: Editor, file: PsiFile): Boolean = {
    super.isAvailable(project, editor, file) && parametersText(ref).isEmpty
  }
}

class CreateClassQuickFix(ref: ScReference)
  extends CreateTypeDefinitionQuickFix(ref, Class) {

  override val getText: String = ScalaBundle.message("create.class.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.class")
}

class CreateCaseClassQuickFix(ref: ScReference)
  extends CreateTypeDefinitionQuickFix(ref, Class) {

  override val getText: String = ScalaBundle.message("create.case.class.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.case.class")

  override protected def afterCreationWork(clazz: ScTypeDefinition)(editor: Editor): Unit = {
    clazz.setModifierProperty("case")
    super.afterCreationWork(clazz)(editor)
  }
}

final class CreateAnnotationClassQuickFix(ref: ScReference)
  extends CreateTypeDefinitionQuickFix(ref, Class) {

  override val getText: String = ScalaBundle.message("create.annotation.class.named", ref.nameId.getText)
  override val getFamilyName: String = ScalaBundle.message("family.name.create.annotation.class")

  override protected def afterCreationWork(td: ScTypeDefinition)(editor: Editor): Unit = {
    createTypeElementFromText("scala.annotation.StaticAnnotation", td) match {
      case ScSimpleTypeElement(ScReference(staticAnnotation: ScTypeDefinition)) =>
        org.jetbrains.plugins.scala.lang.refactoring.extractTrait.ExtractSuperUtil.addExtendsTo(td, staticAnnotation)
      case _ =>
    }

    super.afterCreationWork(td)(editor)
  }
}
