package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.generation.{ClassMember => JClassMember}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.command.{CommandProcessor, WriteCommandAction}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.{DumbService, Project}
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi._
import com.intellij.psi.util.{PsiTreeUtil, PsiUtilCore}
import com.intellij.util.ThrowableRunnable
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.{ApiStatus, VisibleForTesting}
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.api.base.Constructor
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScEnum, ScMember, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createOverrideImplementVariableWithClass
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers._
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.resolve.ResolveUtils
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.statistics.ScalaActionUsagesCollector

import scala.collection.immutable.ArraySeq
import scala.collection.mutable
import scala.jdk.CollectionConverters._

//noinspection InstanceOf
object ScalaOIUtil {

  private def toClassMember(signature: Signature, isOverride: Boolean, tdContext: ScTemplateDefinition): Option[ClassMember] = {
    val Signature(named, substitutor) = signature
    val maybeContext = Option(named.nameContext)

    def createMember(parameter: ScClassParameter): ScValue = {
      implicit val projectContext: ProjectContext = parameter.projectContext

      val member = createOverrideImplementVariableWithClass(
        variable = parameter,
        substitutor = substitutor,
        needsOverrideModifier = false,
        isVal = true,
        clazz = parameter.containingClass,
        features = tdContext
      ).asInstanceOf[ScValue]

      member.context = tdContext
      member
    }

    named match {
      case typedDefinition: ScTypedDefinition =>
        maybeContext.collect {
          case x: ScValue => new ScValueMember(x, typedDefinition, substitutor, isOverride)
          case x: ScVariable => new ScVariableMember(x, typedDefinition, substitutor, isOverride)
          case x: ScClassParameter if x.isVal => new ScValueMember(createMember(x), typedDefinition, substitutor, isOverride)
        }
      case _ =>
        maybeContext.collect {
          case x: ScTypeAlias if x.containingClass != null => ScAliasMember(x, substitutor, isOverride)
          case x: PsiField => JavaFieldMember(x, substitutor)
        }
    }
  }

  def invokeOverrideImplement(file: PsiFile, isImplement: Boolean)
                             (implicit project: Project, editor: Editor): Unit =
    invokeOverrideImplement(file, isImplement, None)

  @VisibleForTesting
  def invokeOverrideImplement(
    file: PsiFile,
    isImplement: Boolean,
    methodName: Option[String]
  )(implicit project: Project, editor: Editor): Unit = {
    val td = getTemplateDefinitionForOverrideImplementAction(file, editor)
    td.foreach { td =>
      invokeOverrideImplement(td, isImplement, methodName)
    }
  }

  @VisibleForTesting
  @ApiStatus.Internal
  def getTemplateDefinitionForOverrideImplementAction(
    file: PsiFile,
    editor: Editor
  ): Option[ScTemplateDefinition] = {
    val elementAtCaret = ScalaGenerateMembersUtil.getElementAtCaretAdjustedForIndentationBasedSyntax(file, editor)
    val templateDefinition = elementAtCaret.parentOfType(classOf[ScTemplateDefinition])
    templateDefinition.map {
      case enumCase: ScEnumCase => enumCase.enumParent
      case td => td
    }
  }

  def invokeOverrideImplement(clazz: ScTemplateDefinition, isImplement: Boolean)
                             (implicit project: Project, editor: Editor): Unit =
    invokeOverrideImplement(clazz, isImplement, None)

  def invokeOverrideImplement(
    clazz: ScTemplateDefinition,
    isImplement: Boolean,
    methodName: Option[String]
  )(implicit project: Project, editor: Editor): Unit = {
    ScalaActionUsagesCollector.logOverrideImplement(project)

    val classMembers =
      if (isImplement) getMembersToImplement(clazz, withSelfType = true)
      else getMembersToOverride(clazz)

    if (classMembers.nonEmpty) {
      val selectedMembers = methodName match {
        case None =>
          if (ApplicationManager.getApplication.isUnitTestMode)
            classMembers //if no explicit methodName is specified in tests, implement all members
          else
            showSelectMembersDialogAndGet(clazz, isImplement, classMembers)
        case Some(name) =>
          classMembers.find {
            case named: ScalaNamedMember if named.name == name => true
            case _ => false
          }.toSeq
      }
      if (selectedMembers.nonEmpty) {
        runAction(selectedMembers, isImplement, clazz)
      }
    }
  }

  private def showSelectMembersDialogAndGet(
    clazz: ScTemplateDefinition,
    isImplement: Boolean,
    classMembers: Seq[ClassMember]
  ): Seq[ClassMember] = {
    val title = if (isImplement) ScalaBundle.message("select.method.implement") else ScalaBundle.message("select.method.override")
    val chooserDialog = new ScalaMemberChooser[ClassMember](
      classMembers.to(ArraySeq),
      false,
      true,
      isImplement,
      true,
      true,
      clazz
    )
    chooserDialog.setTitle(title)
    if (isImplement) {
      chooserDialog.selectElements(classMembers.toArray[JClassMember])
    }
    chooserDialog.show()
    Option(chooserDialog.getSelectedElements).map(_.asScala.toSeq).getOrElse(Seq.empty)
  }

  /**
   * @note Written according to [[com.intellij.codeInsight.generation.OverrideImplementUtil.showAndPerform]].
   */
  @RequiresEdt
  def runAction(selectedMembers: Seq[ClassMember],
                isImplement: Boolean,
                clazz: ScTemplateDefinition)
               (implicit project: Project, editor: Editor): Unit = {
    if (selectedMembers.isEmpty) return
    PsiUtilCore.ensureValid(clazz)

    val insertMembers: Runnable = { () =>
      val inserted = ScalaGenerateMembersUtil.insertMembersAtCaretPosition(selectedMembers, clazz, editor, addOverrideModifierIfEnforcedBySettings = true)
      ScalaGenerateMembersUtil.positionCaret(editor, inserted)
    }

    val performImplementOverrideRunnable: ThrowableRunnable[RuntimeException] =
      () => DumbService.getInstance(project).withAlternativeResolveEnabled(insertMembers)

    if (Registry.is("run.refactorings.under.progress")) {
      if (!FileModificationService.getInstance().preparePsiElementsForWrite(clazz)) return
      val commandName = CommandProcessor.getInstance().getCurrentCommandName
      val title = if (isImplement) ScalaBundle.message("action.implement.method") else ScalaBundle.message("action.override.method")

      val performUnderProgress: java.util.function.Consumer[ProgressIndicator] = { indicator =>
        indicator.setIndeterminate(false)
        indicator.setFraction(0)
        performImplementOverrideRunnable.run()
      }

      val runnable: Runnable = { () =>
        //noinspection ApiStatus
        ApplicationManagerEx.getApplicationEx.runWriteActionWithCancellableProgressInDispatchThread(
          title, project, null, performUnderProgress)
      }
      if (commandName eq null) {
        CommandProcessor.getInstance().executeCommand(project, runnable, title, null)
      } else {
        runnable.run()
      }
    } else {
      WriteCommandAction.writeCommandAction(project, clazz.getContainingFile).run(performImplementOverrideRunnable)
    }
  }

  def getMembersToImplement(clazz: ScTemplateDefinition, withOwn: Boolean = false, withSelfType: Boolean = false): Seq[ClassMember] =
    classMembersWithFilter(clazz, withSelfType, isOverride = false)(needImplement(_, clazz, withOwn), needImplement(_, clazz, withOwn))

  def getAllMembersToOverride(clazz: ScTemplateDefinition): Seq[ClassMember] =
    classMembersWithFilter(clazz, withSelfType = true)(Function.const(true), Function.const(true))

  def getMembersToOverride(clazz: ScTemplateDefinition): Seq[ClassMember] =
    classMembersWithFilter(clazz, withSelfType = true)(needOverride(_, clazz), needOverride(_, clazz))

  private[this] def classMembersWithFilter(
    definition: ScTemplateDefinition,
    withSelfType: Boolean,
    isOverride: Boolean = true
  )(
    f1: PhysicalMethodSignature => Boolean,
    f2: PsiNamedElement => Boolean
  ): Seq[ClassMember] = {
    val maybeThisType = if (withSelfType)
      for {
        selfType <- definition.selfType
        clazzType = definition.getTypeWithProjections().getOrAny

        glb = selfType.glb(clazzType)
        if glb.isInstanceOf[ScCompoundType]
      } yield (glb.asInstanceOf[ScCompoundType], Some(clazzType))
    else
      None

    val types = maybeThisType.fold(getTypes(definition)) {
      case (compoundType, compoundTypeThisType) => getTypes(compoundType, compoundTypeThisType)
    }.allSignatures

    val signatures = maybeThisType.fold(getSignatures(definition)) {
      case (compoundType, compoundTypeThisType) => getSignatures(compoundType, compoundTypeThisType)
    }

    val methodsAndExtensionMethods = signatures.allSignatures.filter {
      case signature: PhysicalMethodSignature if signature.namedElement.isValid =>
        f1(signature)
      case _ => false
    }.map {
      case signature: PhysicalMethodSignature =>
        val method = signature.method
        assert(method.containingClass != null, s"Containing Class is null: ${method.getText}")
        if (signature.isExtensionMethod)
          ScExtensionMethodMember(signature, isOverride)
        else
          ScMethodMember(signature, isOverride)
    }

    val values = signatures.allSignatures.filter(isValSignature)

    val aliasesAndValues = (types ++ values).filter {
      case Signature(named, _) => named.isValid && f2(named)
    }.flatMap {
      toClassMember(_, isOverride, definition)
    }

    (methodsAndExtensionMethods ++ aliasesAndValues).toSeq
  }

  private def isProductAbstractMethod(m: PsiMethod, cls: PsiClass): Boolean = {
    val methodName = m.name

    // First observation:
    // If the methodName is `productArity` or `productElement`, then the containing class of the method must be
    // `scala.Product`. There is no point in recursing the type hierarchy if this is the case.
    if (methodName == "productArity" || methodName == "productElement") {
      val containing = m.containingClass
      if ((containing ne null) && containing.qualifiedName == "scala.Product")
        return true
    }

    // Second observation:
    // If the name of the method is also not `apply` and not `canEqual`, we can immediately exit the search.
    if (methodName != "apply" && methodName != "canEqual")
      return false

    // Third observation:
    // If the name of the method is `apply` or `canEqual`, we now need to search for a `ScTypeDefinition` which is
    // either a case class or an enum.
    def isCaseClassOrEnum(cls: PsiClass): Boolean = cls match {
      case td: ScTypeDefinition => td.isCase || td.is[ScEnum]
      case _ => false
    }

    // Implemented as a breadth-first search written in an imperative style.
    // Compared to non tail recursive recursion (previous implementation), this can be executed in a single
    // stack frame and avoid potential StackOverflowErrors in deep type hierarchies.
    val visited = mutable.Set.empty[PsiClass]
    val queue = mutable.Queue(cls)

    while (queue.nonEmpty) {
      val current = queue.dequeue()
      visited += current

      if (isCaseClassOrEnum(current)) {
        // The `PsiClass` which we're currently examining is a case class or an enum. Exit the search.
        return true
      }

      current match {
        case x: ScTemplateDefinition =>
          // The `PsiClass` which we're currently examining is a `ScTemplateDefinition`. We need to examine its
          // supertypes, keeping in mind not to search already visited classes.
          queue ++= x.supers.filterNot(visited)
        case _ =>
          // Ignore other types.
      }
    }

    // The whole type hierarchy has been searched, this method is not an abstract method of `scala.Product`.
    false
  }

  private def needOverride(sign: PhysicalMethodSignature, clazz: ScTemplateDefinition): Boolean = {
    sign.method match {
      case method if isProductAbstractMethod(method, clazz) => true
      case f: ScFunctionDeclaration if !f.isNative => false
      case x if x.name == "$tag" || x.name == "$init$" => false
      case x: ScFunction if x.isCopyMethod && x.isSynthetic => false
      case x if x.containingClass == clazz => false
      case x: PsiModifierListOwner if (x.hasModifierPropertyScala("abstract") &&
        !x.isInstanceOf[ScFunctionDefinition])
        || x.hasModifierPropertyScala("final") => false
      case Constructor(_) => false
      case method if !ResolveUtils.isAccessible(method, clazz.extendsBlock) => false
      case method =>
        var flag = false
        if (method match {
          case x: ScFunction => x.parameters.isEmpty
          case _ => method.getParameterList.getParametersCount == 0
        }) {
          for (term <- clazz.allVals; v = term.namedElement) if (v.name == method.name) {
            v.nameContext match {
              case x: ScValue if x.containingClass == clazz => flag = true
              case x: ScVariable if x.containingClass == clazz => flag = true
              case _ =>
            }
          }
        }
        !flag
    }
  }

  private def needImplement(sign: PhysicalMethodSignature, clazz: ScTemplateDefinition, withOwn: Boolean): Boolean = {
    val m = sign.method
    val name = if (m == null) "" else m.name
    val place = clazz.extendsBlock

    m match {
      case _ if sign.exportedIn.nonEmpty                       => false
      case _ if isProductAbstractMethod(m, clazz)              => false
      case method if !ResolveUtils.isAccessible(method, place) => false
      case _ if name == "$tag" || name == "$init$"             => false
      case x if !withOwn && x.containingClass == clazz         => false
      case x
          if x.containingClass != null && x.containingClass.isInterface &&
            !x.containingClass.isInstanceOf[ScTrait] && x.hasModifierProperty("abstract") =>
        true
      case x
          if x.hasModifierPropertyScala("abstract") && !x.isInstanceOf[ScFunctionDefinition] &&
            !x.isInstanceOf[ScPatternDefinition] && !x.isInstanceOf[ScVariableDefinition] =>
        true
      case x: ScFunctionDeclaration if !x.isNative => true
      case _                                       => false
    }
  }

  private def needOverride(named: PsiNamedElement, clazz: ScTemplateDefinition) = {
    named.nameContext match {
      case x: PsiModifierListOwner if x.hasModifierPropertyScala("final") => false
      case m: PsiMember if !ResolveUtils.isAccessible(m, clazz.extendsBlock) => false
      case x: ScValue if x.containingClass != clazz =>
        var flag = false
        for (signe <- clazz.allMethods if signe.method.containingClass == clazz) {
          //containingClass == clazz so we sure that this is ScFunction (it is safe cast)
          signe.method match {
            case fun: ScFunction if fun.parameters.isEmpty && x.declaredElements.exists(_.name == fun.name) =>
              flag = true
            case _ => //todo: ScPrimaryConstructor?
          }
        }
        for (term <- clazz.allVals; v = term.namedElement) if (v.name == named.name) {
          v.nameContext match {
            case x: ScValue if x.containingClass == clazz => flag = true
            case _ =>
          }
        }
        !flag
      case x: ScTypeAliasDefinition => x.containingClass != clazz
      case x: ScClassParameter if x.isVal => x.containingClass != clazz
      case _ => false
    }
  }

  private def needImplement(named: PsiNamedElement, clazz: ScTemplateDefinition, withOwn: Boolean): Boolean = {
    named.nameContext match {
      case m: PsiMember if !ResolveUtils.isAccessible(m, clazz.extendsBlock) => false
      case x: ScValueDeclaration if withOwn || x.containingClass != clazz => true
      case x: ScVariableDeclaration if withOwn || x.containingClass != clazz => true
      case x: ScTypeAliasDeclaration if withOwn || x.containingClass != clazz => true
      case _ => false
    }
  }

  def getAnchor(offset: Int, clazz: ScTemplateDefinition): Option[PsiElement] = {
    val elementAtOffset = clazz.getContainingFile.findElementAt(offset)
    getAnchor(clazz, elementAtOffset)
  }

  def getAnchor(clazz: ScTemplateDefinition, leaf: PsiElement): Option[PsiElement] = {
    val elementClosestToBody = getElementAtCaretClosestToBodyChildren(clazz, leaf)
    elementClosestToBody.flatMap {
      case ws: PsiWhiteSpace =>
        Some(ws)
      case member: ScMember =>
        Some(member)
      case colon if colon.elementType == ScalaTokenTypes.tCOLON =>
        //Handle empty indentation-based class body `class A extends B:`
        None
      case el =>
        val nextMember = PsiTreeUtil.getNextSiblingOfType(el, classOf[ScMember])
        Some(if (nextMember == null) el else nextMember)
    }
  }

  private def getElementAtCaretClosestToBodyChildren(clazz: ScTemplateDefinition, leaf: PsiElement): Option[PsiElement] = {
    val body: ScTemplateBody = clazz.extendsBlock.templateBody match {
      case Some(x) => x
      case _ =>
        return None
    }

    getElementClosestToChildrenOf(leaf, body)
  }


  private def getElementClosestToChildrenOf(leaf: PsiElement, parent: PsiElement): Option[PsiElement] = {
    var element: PsiElement = leaf
    while (element != null && element.getParent != parent) {
      element = element.getParent
    }
    Option(element)
  }

  def methodSignaturesToOverride(definition: ScTemplateDefinition): Iterator[PhysicalMethodSignature] =
    definition.allSignatures.filter {
      case signature: PhysicalMethodSignature => needOverride(signature, definition)
      case _ => false
    }.map {
      _.asInstanceOf[PhysicalMethodSignature]
    }
}
