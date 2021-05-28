package org.jetbrains.plugins.scala
package lang.refactoring.extractTrait

import com.intellij.openapi.actionSystem.{CommonDataKeys, DataContext}
import com.intellij.openapi.editor.{Editor, ScrollType}
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractSuperclass.ExtractSuperClassUtil
import com.intellij.util.containers.MultiMap
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaRecursiveElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.ScReference
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScExpression, ScNewTemplateDefinition, ScSuperReference}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.{ScTypeExt, ScalaType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.ScalaRefactoringActionHandler
import org.jetbrains.plugins.scala.lang.refactoring.memberPullUp.ScalaPullUpProcessor
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaDirectoryService
import org.jetbrains.plugins.scala.statistics.{FeatureKey, Stats}

import scala.jdk.CollectionConverters._
import scala.collection.mutable

/**
 * Nikolay.Tropin
 * 2014-05-20
 */
class ScalaExtractTraitHandler extends ScalaRefactoringActionHandler {

  val REFACTORING_NAME: String = ScalaBundle.message("extract.trait.title")

  override def invoke(file: PsiFile)
                     (implicit project: Project, editor: Editor, dataContext: DataContext): Unit = {
    val offset: Int = editor.getCaretModel.getOffset
    editor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    val element: PsiElement = file.findScalaLikeFile.map(_.findElementAt(offset)).orNull
    if (element == null) return
    val clazz = PsiTreeUtil.getParentOfType(element, classOf[ScTemplateDefinition])
    invokeOnClass(clazz, project, editor)
  }

  override def invoke(elements: Array[PsiElement])
                     (implicit project: Project, dataContext: DataContext): Unit = {
    val clazz = elements match {
      case Array(clazz: ScTemplateDefinition) => clazz
      case _ =>
        val parent = PsiTreeUtil.findCommonParent(elements: _*)
        PsiTreeUtil.getParentOfType(parent, classOf[ScTemplateDefinition], false)
    }

    if (dataContext != null) {
      val editor: Editor = CommonDataKeys.EDITOR.getData(dataContext)
      if (editor != null && clazz != null) {
        invokeOnClass(clazz, project, editor)
      }
    }
  }

  @TestOnly
  def testInvoke(file: PsiFile, onlyDeclarations: Boolean, onlyFirstMember: Boolean)
                (implicit project: Project, editor: Editor): Unit = {
    val offset: Int = editor.getCaretModel.getOffset
    editor.getScrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)
    val element: PsiElement = file.findElementAt(offset)
    val clazz = PsiTreeUtil.getParentOfType(element, classOf[ScTemplateDefinition])
    val allMembers = ExtractSuperUtil.possibleMembersToExtract(clazz)
    val memberInfos = if (onlyFirstMember) Seq(allMembers.head) else allMembers
    if (onlyDeclarations) memberInfos.foreach(_.setToAbstract(true))
    val extractInfo = new ExtractInfo(clazz, memberInfos)
    extractInfo.collect()
    val messages = extractInfo.conflicts.values().asScala
    if (messages.nonEmpty) throw new RuntimeException(messages.mkString("\n"))

    inWriteCommandAction {
      val traitText = "trait ExtractedTrait {\n\n}"
      val newTrt = createTemplateDefinitionFromText(traitText, clazz.getContext, clazz)
      val newTrtAdded = clazz match {
        case anon: ScNewTemplateDefinition =>
          val tBody = PsiTreeUtil.getParentOfType(anon, classOf[ScTemplateBody], true)
          val added = tBody.addBefore(newTrt, tBody.getLastChild).asInstanceOf[ScTrait]
          added
        case _ => clazz.getParent.addAfter(newTrt, clazz).asInstanceOf[ScTrait]
      }
      finishExtractTrait(newTrtAdded, extractInfo)
    }
  }

  private def invokeOnClass(clazz: ScTemplateDefinition, project: Project, editor: Editor): Unit = {
    if (clazz == null) return

    Stats.trigger(FeatureKey.extractTrait)

    val dialog = new ScalaExtractTraitDialog(project, clazz)
    dialog.show()
    if (!dialog.isOK) return

    val memberInfos = dialog.getSelectedMembers.asScala.toSeq
    val extractInfo = new ExtractInfo(clazz, memberInfos)
    extractInfo.collect()

    val isOk = ExtractSuperClassUtil.showConflicts(dialog, extractInfo.conflicts, clazz.getProject)
    if (!isOk) return

    val name = dialog.getTraitName
    val packName = dialog.getPackageName

    inWriteCommandAction {
      val newTrait = createTraitFromTemplate(name, packName, clazz)
      finishExtractTrait(newTrait, extractInfo)
    }(project)
  }

  private def finishExtractTrait(trt: ScTrait, extractInfo: ExtractInfo): Unit = {
    val memberInfos = extractInfo.memberInfos
    val clazz = extractInfo.clazz

    addSelfType(trt, extractInfo.selfTypeText)
    addTypeParameters(trt, extractInfo.typeParameters)
    ExtractSuperUtil.addExtendsTo(clazz, trt, extractInfo.typeArgs)
    val pullUpProcessor = new ScalaPullUpProcessor(clazz.getProject, clazz, trt, memberInfos)
    pullUpProcessor.moveMembersToBase()
  }

  private def addSelfType(trt: ScTrait, selfTypeText: Option[String]): Unit = {
    import trt.projectContext

    selfTypeText match {
      case None =>
      case Some(selfTpe) =>
        val traitText = s"trait ${trt.name} {\n$selfTpe\n}"
        val dummyTrait = createTemplateDefinitionFromText(traitText, trt.getParent, trt)
        val selfTypeElem = dummyTrait.extendsBlock.selfTypeElement.get
        val extendsBlock = trt.extendsBlock

        val templateBody = extendsBlock.templateBody match {
          case Some(tb) => tb
          case None => extendsBlock.add(createTemplateBody)
        }

        val lBrace = templateBody.getFirstChild
        val ste = templateBody.addAfter(selfTypeElem, lBrace)
        templateBody.addAfter(createNewLine(), lBrace)
        ScalaPsiUtil.adjustTypes(ste)
    }
  }

  private def addTypeParameters(trt: ScTrait, typeParamsText: String): Unit = {
    if (typeParamsText == null || typeParamsText.isEmpty) return
    val clause = createTypeParameterClauseFromTextWithContext(typeParamsText, trt, trt.nameId)
    trt.addAfter(clause, trt.nameId)
  }

  private def createTraitFromTemplate(name: String, packageName: String, clazz: ScTemplateDefinition): ScTrait = {
    val currentPackageName = ExtractSuperUtil.packageName(clazz)
    val dir =
      if (packageName == currentPackageName) clazz.getContainingFile.getContainingDirectory
      else {
        val pckg = JavaPsiFacade.getInstance(clazz.getProject).findPackage(packageName)
        if (pckg == null || pckg.getDirectories.isEmpty) throw new IllegalArgumentException("Cannot find directory for new trait")
        else pckg.getDirectories()(0)
      }
    ScalaDirectoryService.createClassFromTemplate(dir, name, "Scala Trait", askToDefineVariables = false).asInstanceOf[ScTrait]
  }

  private class ExtractInfo(val clazz: ScTemplateDefinition, val memberInfos: Seq[ScalaExtractMemberInfo]) {
    private val classesForSelfType = mutable.Buffer[PsiClass]()
    private val selected = memberInfos.map(_.getMember)
    private var currentMemberName: String = _
    private val typeParams = mutable.Set[ScTypeParam]()
    val conflicts: MultiMap[PsiElement, String] = new MultiMap[PsiElement, String]

    private def forMember[T](elem: PsiElement)(action: PsiMember => Option[T]): Option[T] = {
      elem match {
        case ScalaPsiUtil.inNameContext(m: ScMember) => action(m)
        case m: PsiMember => action(m)
        case _ => None
      }
    }

    private object inSameClassOrAncestor {
      def unapply(elem: PsiElement): Option[(PsiMember, PsiClass)] = {
        forMember(elem) {
          m => m.containingClass match {
            case null => None
            case `clazz` => Some(m, clazz)
            case c if clazz.isInheritor(c, true) => Some(m, c)
            case _ => None
          }
        }
      }
    }

    private object inSelfType {
      def unapply(elem: PsiElement): Option[PsiClass] = {
        val selfTypeOfClazz = clazz.extendsBlock.selfType
        if (selfTypeOfClazz.isEmpty) return None

        forMember(elem) {
          m => m.containingClass match {
            case null => None
            case c if selfTypeOfClazz.get.conforms(ScalaType.designator(c)) => Some(c)
            case _ => None
          }
        }
      }
    }

    private def addToClassesForSelfType(cl: PsiClass): Unit = {
      if (!classesForSelfType.contains(cl) && !classesForSelfType.exists(_.isInheritor(cl, true))) {
        classesForSelfType --= classesForSelfType.filter(cl.isInheritor(_, true))
        classesForSelfType += cl
      }
    }

    private def collectForSelfType(resolve: PsiElement): Unit = {
      resolve match {
        case inSameClassOrAncestor(m, cl: PsiClass) if !selected.contains(m) => addToClassesForSelfType(cl)
        case inSelfType(cl) => addToClassesForSelfType(cl)
        case _ =>
      }
    }

    private def collectConflicts(ref: ScReference, resolve: PsiElement): Unit = {
      resolve match {
        case named: PsiNamedElement =>
          ScalaPsiUtil.nameContext(named) match {
            case m: ScMember if m.containingClass == clazz && m.isPrivate=>
              val message = ScalaBundle.message("private.member.cannot.be.used.in.extracted.member", named.name, currentMemberName)
              conflicts.putValue(m, message)
            case m: ScMember if clazz.is[ScNewTemplateDefinition] && m.containingClass == clazz && !selected.contains(m) =>
              val message = ScalaBundle.message("member.of.anonymous.class.cannot.be.used.in.extracted.member", named.name, currentMemberName)
              conflicts.putValue(m, message)
            case m: PsiMember
              if m.containingClass != null && ref.qualifier.exists(_.is[ScSuperReference]) && clazz.isInheritor(m.containingClass, true) =>
              val message = ScalaBundle.message("super.reference.used.in.extracted.member", currentMemberName)
              conflicts.putValue(m, message)
            case _ =>
          }
        case _ =>
      }
    }

    private def collectTypeParameters(resolve: PsiElement): Unit = {
      val visitor = new ScalaRecursiveElementVisitor {
        override def visitReference(ref: ScReference): Unit = {
          ref.resolve() match {
            case tp: ScTypeParam => typeParams += tp
            case _ =>
          }
        }
      }

      resolve match {
        case typeParam: ScTypeParam if typeParam.owner == clazz =>
          typeParams += typeParam
          typeParam.accept(visitor)
        case _ =>
      }
    }

    def collect(): Unit = {
      val visitor = new ScalaRecursiveElementVisitor {
        override def visitReference(ref: ScReference): Unit = {
          val resolve = ref.resolve()
          collectForSelfType(resolve)
          collectConflicts(ref, resolve)
          collectTypeParameters(resolve)
        }
      }

      for {
        info <- memberInfos
        member = info.getMember
      } {
        currentMemberName = info.getDisplayName
        if (info.isToAbstract) member.children.foreach {
          case _: ScExpression =>
          case other => other.accept(visitor)
        }
        else member.accept(visitor)
      }

      classesForSelfType.foreach {
        case cl: PsiClass if cl.getTypeParameters.nonEmpty =>
          val message = ScalaBundle.message("type.parameters.for.self.type.not.supported", cl.name)
          conflicts.putValue(cl, message)
        case _ =>
      }
    }

    def selfTypeText: Option[String] = {
      val alias = clazz.extendsBlock.selfTypeElement.fold("this")(_.name)

      val typeText = classesForSelfType.reverseIterator.map {
        case obj: ScObject => s"${obj.qualifiedName}.type"
        case cl: ScTypeDefinition => cl.qualifiedName
        case cl: PsiClass => cl.qualifiedName
      }.mkString(TypePresentationContext(clazz).compoundTypeSeparatorText)

      if (classesForSelfType.nonEmpty) {
        val arrow = ScalaPsiUtil.functionArrow(clazz.getProject)
        Some(s"$alias: $typeText $arrow")
      } else None
    }

    def typeParameters: String = {
      val paramTexts = typeParams.toSeq.sortBy(_.getTextRange.getStartOffset).map(_.getText)
      if (paramTexts.isEmpty) "" else paramTexts.mkString("[", ", ", "]")
    }

    def typeArgs: String = {
      val names = typeParams.toSeq.sortBy(_.getTextRange.getStartOffset).map(_.name)
      if (names.isEmpty) "" else names.mkString("[", ", ", "]")
    }
  }

}
