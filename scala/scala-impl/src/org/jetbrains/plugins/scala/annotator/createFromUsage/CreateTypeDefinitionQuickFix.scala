package org.jetbrains.plugins.scala
package annotator.createFromUsage

import com.intellij.codeInsight.navigation.NavigationUtil
import com.intellij.codeInsight.template.{TemplateBuilder, TemplateBuilderImpl, TemplateManager}
import com.intellij.codeInsight.{CodeInsightUtilCore, FileModificationService}
import com.intellij.ide.util.PsiElementListCellRenderer
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.search.PsiElementProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import javax.swing.Icon
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil._
import org.jetbrains.plugins.scala.console.ScalaLanguageConsoleView
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaDirectoryService

/**
 * Nikolay.Tropin
 * 2014-07-28
 */
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
        case Some(InstanceOfClass(typeDef: ScTypeDefinition)) => createInnerClassIn(typeDef)
        case Some(ResolvesTo(pack: PsiPackage)) => createClassInPackage(pack)
        case None =>
          val inThisFile = ref.withParentsInFile.collect {
            case inner childOf (_: ScTemplateBody) => inner
            case td: ScTypeDefinition if td.isTopLevel => td
          }
          val fileOption = if (file == null || file.getContainingDirectory == null) None else Some(file)
          val possibleSiblings = fileOption ++: inThisFile.toSeq.reverse
          createClassWithLevelChoosing(editor, possibleSiblings)
        case _ =>
      }
    }
  }

  private def createClassInPackage(psiPackage: PsiPackage): Unit = {
    val directory = psiPackage.getDirectories.filter(_.isWritable) match {
      case Array(dir) => dir
      case Array() => throw new IllegalStateException(s"Cannot find directory for the package `${psiPackage.name}`")
      case dirs => 
        val currentDir = dirs.find(PsiTreeUtil.isAncestor(_, ref, true))
                .orElse(dirs.find(ScalaPsiUtil.getModule(_) == ScalaPsiUtil.getModule(ref)))
        currentDir.getOrElse(dirs(0))
    }
    createClassInDirectory(directory)
  }

  private def createInnerClassIn(target: ScTemplateDefinition): Unit = {
    val extBlock = target.extendsBlock
    val targetBody = extBlock.templateBody.getOrElse(
      extBlock.add(createTemplateBody(target.getManager)))
    createClassIn(targetBody, Some(targetBody.getLastChild))
  }

  private def createClassIn(parent: PsiElement, anchorAfter: Option[PsiElement]): Unit = {
    try {
      if (!FileModificationService.getInstance.preparePsiElementForWrite(parent)) return

      val text = s"${kind.keyword} $name"
      val newTd = createTemplateDefinitionFromText(text, parent, parent.getFirstChild)
      val anchor = anchorAfter.orNull
      parent.addBefore(createNewLine()(parent.getManager), anchor)
      val result = parent.addBefore(newTd, anchor)
      afterCreationWork(result.asInstanceOf[ScTypeDefinition])
    }
    catch {
      case e: IncorrectOperationException =>
        LOG.error(e)
    }
  }

  private def createClassWithLevelChoosing(editor: Editor, siblings: Seq[PsiElement]): Unit = {
    val renderer: PsiElementListCellRenderer[PsiElement] = new PsiElementListCellRenderer[PsiElement] {
      override def getElementText(element: PsiElement): String = element match {
        case _: PsiFile => "New file"
        case td: ScTypeDefinition if td.isTopLevel => "Top level in this file"
        case _ childOf (tb: ScTemplateBody) =>
          val containingClass = PsiTreeUtil.getParentOfType(tb, classOf[ScTemplateDefinition])
          s"Inner in ${containingClass.name}"
        case _ => "Local scope"
      }

      override def getContainerText(element: PsiElement, name: String): String = null
      override def getIconFlags = 0
      override def getIcon(element: PsiElement): Icon = null
    }
    siblings match {
      case Seq() =>
      case Seq(elem) => createClassAtLevel(elem)
      case _ =>
        val selection = siblings.head
        val processor = new PsiElementProcessor[PsiElement] {
          override def execute(elem: PsiElement): Boolean = {
            inWriteCommandAction {
              createClassAtLevel(elem)
            }(elem.getProject)
            false
          }
        }
        NavigationUtil.getPsiElementPopup(siblings.toArray, renderer, ScalaBundle.message("choose.level.popup.title"), processor, selection)
                .showInBestPositionFor(editor)
    }
  }
  
  private def createClassAtLevel(sibling: PsiElement): Unit = {
    sibling match {
      case file: PsiFile => createClassInDirectory(file.getContainingDirectory)
      case td: ScTypeDefinition if td.isTopLevel => createClassIn(td.getParent, None)
      case _ childOf (tb: ScTemplateBody) =>
        createInnerClassIn(PsiTreeUtil.getParentOfType(tb, classOf[ScTemplateDefinition]))
      case _ =>
    }
  }
  
  private def createClassInDirectory(directory: PsiDirectory): Unit = {
    val clazz = ScalaDirectoryService.createClassFromTemplate(directory, name, kind.templateName, askToDefineVariables = false)
    afterCreationWork(clazz.asInstanceOf[ScTypeDefinition])
  }

  protected def afterCreationWork(clazz: ScTypeDefinition): Unit = {
    addGenericParams(clazz)
    addClassParams(clazz)
    ScalaPsiUtil.adjustTypes(clazz)
    runTemplate(clazz)
  }
  
  protected def addMoreElementsToTemplate(builder: TemplateBuilder, clazz: ScTypeDefinition): Unit = {}

  private def runTemplate(clazz: ScTypeDefinition): Unit = {
    val builder = new TemplateBuilderImpl(clazz)

    addTypeParametersToTemplate(clazz, builder)
    clazz match {
      case cl: ScClass if cl.constructor.exists(_.parameters.nonEmpty) =>
        addParametersToTemplate(cl.constructor.get, builder)
      case _ =>
    }
    addMoreElementsToTemplate(builder, clazz)

    CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(clazz)

    val template = builder.buildTemplate()
    val targetFile = clazz.getContainingFile
    val isScalaConsole = targetFile.name == ScalaLanguageConsoleView.ScalaConsole

    if (!isScalaConsole) {
      val newEditor = positionCursor(clazz.nameId)
      if (template.getSegmentsCount != 0) {
        val range = clazz.getTextRange
        newEditor.getDocument.deleteString(range.getStartOffset, range.getEndOffset)
        TemplateManager.getInstance(clazz.getProject).startTemplate(newEditor, template)
      }
    }
  }

  private def addGenericParams(clazz: ScTypeDefinition): Unit = ref.getParent.getParent match {
    case pt: ScParameterizedTypeElement => 
      val paramsText = pt.typeArgList.typeArgs match {
        case args if args.size == 1 => "[T]"
        case args => args.indices.map(i => s"T${i + 1}").mkString("[",", ", "]")
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
        val parameters = createParamClausesWithContext(text, constr, constr.getFirstChild)
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

  override protected def afterCreationWork(clazz: ScTypeDefinition): Unit = {
    clazz.setModifierProperty("case")
    super.afterCreationWork(clazz)
  }
}
