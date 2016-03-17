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
import org.jetbrains.plugins.scala.annotator.createFromUsage.CreateFromUsageUtil._
import org.jetbrains.plugins.scala.console.ScalaLanguageConsoleView
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReferenceElement
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScParameterizedTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.api.TypeSystem
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaDirectoryService
import org.jetbrains.plugins.scala.project.ProjectExt

/**
 * Nikolay.Tropin
 * 2014-07-28
 */
abstract class CreateTypeDefinitionQuickFix(ref: ScReferenceElement, description: String, kind: ClassKind)
        extends CreateFromUsageQuickFixBase(ref, description) {
  private final val LOG: Logger = Logger.getInstance("#org.jetbrains.plugins.scala.annotator.createFromUsage.CreateTemplateDefinitionQuickFix")
  private val name = ref.refName


  override def isAvailable(project: Project, editor: Editor, file: PsiFile) = {
    implicit val typeSystem = project.typeSystem
    def goodQualifier = ref.qualifier match {
      case Some(InstanceOfClass(typeDef: ScTypeDefinition)) => true
      case Some(ResolvesTo(pack: PsiPackage)) => true
      case None => true
      case _ => false
    }
    super.isAvailable(project, editor, file) && goodQualifier
  }

  override protected def invokeInner(project: Project, editor: Editor, file: PsiFile) = {
    inWriteAction {
      implicit val typeSystem = project.typeSystem
      ref.qualifier match {
        case Some(InstanceOfClass(typeDef: ScTypeDefinition)) => createInnerClassIn(typeDef)
        case Some(ResolvesTo(pack: PsiPackage)) => createClassInPackage(pack)
        case None =>
          val inThisFile = (Iterator(ref) ++ ref.parentsInFile).collect {
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
      case Array() => throw new IllegalStateException(s"Cannot find directory for the package `${psiPackage.getName}`")
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
      extBlock.add(ScalaPsiElementFactory.createTemplateBody(target.getManager)))
    createClassIn(targetBody, Some(targetBody.getLastChild))
  }

  private def createClassIn(parent: PsiElement, anchorAfter: Option[PsiElement]): Unit = {
    try {
      if (!FileModificationService.getInstance.preparePsiElementForWrite(parent)) return

      val text = s"${kind.keyword} $name"
      val newTd = ScalaPsiElementFactory.createTemplateDefinitionFromText(text, parent, parent.getFirstChild)
      val anchor = anchorAfter.orNull
      parent.addBefore(ScalaPsiElementFactory.createNewLine(parent.getManager), anchor)
      val result = parent.addBefore(newTd, anchor)
      afterCreationWork(result.asInstanceOf[ScTypeDefinition])
    }
    catch {
      case e: IncorrectOperationException =>
        LOG.error(e)
    }
  }

  private def createClassWithLevelChoosing(editor: Editor, siblings: Seq[PsiElement])(implicit typeSystem: TypeSystem) {
    val renderer = new PsiElementListCellRenderer[PsiElement] {
      override def getElementText(element: PsiElement) = element match {
        case f: PsiFile => "New file"
        case td: ScTypeDefinition if td.isTopLevel => "Top level in this file"
        case _ childOf (tb: ScTemplateBody) =>
          val containingClass = PsiTreeUtil.getParentOfType(tb, classOf[ScTemplateDefinition])
          s"Inner in ${containingClass.name}"
        case _ => "Local scope"
      }

      override def getContainerText(element: PsiElement, name: String) = null
      override def getIconFlags = 0
      override def getIcon(element: PsiElement) = null
    }
    siblings match {
      case Seq() =>
      case Seq(elem) => createClassAtLevel(elem)
      case _ =>
        val selection = siblings.head
        val processor = new PsiElementProcessor[PsiElement] {
          def execute(elem: PsiElement): Boolean = {
            inWriteCommandAction(elem.getProject){
              createClassAtLevel(elem)
            }
            false
          }
        }
        NavigationUtil.getPsiElementPopup(siblings.toArray, renderer, "Choose level", processor, selection)
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
  
  private def createClassInDirectory(directory: PsiDirectory) = {
    val clazz = ScalaDirectoryService.createClassFromTemplate(directory, name, kind.templateName, askToDefineVariables = false)
    afterCreationWork(clazz.asInstanceOf[ScTypeDefinition])
  }

  protected def afterCreationWork(clazz: ScTypeDefinition) {
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
    val isScalaConsole = targetFile.getName == ScalaLanguageConsoleView.SCALA_CONSOLE

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
      val clause = ScalaPsiElementFactory.createTypeParameterClauseFromTextWithContext(paramsText, clazz, nameId)
      clazz.addAfter(clause, nameId)
    case _ =>
  }

  private def addClassParams(clazz: ScTypeDefinition): Unit = {
    clazz match {
      case cl: ScClass =>
        val constr = cl.constructor.get
        val text = parametersText(ref)
        val parameters = ScalaPsiElementFactory.createParamClausesWithContext(text, constr, constr.getFirstChild)
        constr.parameterList.replace(parameters)
      case _ =>
    }
  }
}

class CreateObjectQuickFix(ref: ScReferenceElement)
        extends CreateTypeDefinitionQuickFix(ref, "object", Object)

class CreateTraitQuickFix(ref: ScReferenceElement)
        extends CreateTypeDefinitionQuickFix(ref, "trait", Trait) {
  
  override def isAvailable(project: Project, editor: Editor, file: PsiFile) = {
    super.isAvailable(project, editor, file) && parametersText(ref).isEmpty
  }
}

class CreateClassQuickFix(ref: ScReferenceElement)
        extends CreateTypeDefinitionQuickFix(ref, "class", Class)

class CreateCaseClassQuickFix(ref: ScReferenceElement)
        extends CreateTypeDefinitionQuickFix(ref, "case class", Class) {

  override protected def afterCreationWork(clazz: ScTypeDefinition) = {
    clazz.setModifierProperty("case", value = true)
    super.afterCreationWork(clazz)
  }
}
