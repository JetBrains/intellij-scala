package org.jetbrains.plugins.scala.overrideImplement

import com.intellij.codeInsight.generation.GenerationInfoBase
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.pom.Navigatable
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.actions.ScalaFileTemplateUtil
import org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation.addTargetNameAnnotationIfNeeded
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.{TypeAdjuster, types}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScSignatureClause.{TermClause, TypeClause}
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameterClause, ScTypeParamClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.types.{Context, ScType, ScTypeExt, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.overrideImplement.ScalaGenerationInfo._
import org.jetbrains.plugins.scala.project.ScalaFeatures
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

import java.util.Properties

class ScalaGenerationInfo(classMember: ClassMember0, needsOverrideModifier: Boolean, typeAdjuster: TypeAdjuster)
  extends GenerationInfoBase {

  private var myMember: PsiMember = classMember.getElement

  override def getPsiMember: PsiMember = myMember

  override def insert(aClass: PsiClass, anchor: PsiElement, before: Boolean): Unit = {
    val templDef = aClass match {
      case td: ScTemplateDefinition => td
      case _ => return
    }

    val comment =
      if (ScalaApplicationSettings.getInstance().COPY_SCALADOC) {
        val docOwner = Option(classMember.getElement).filterByType[PsiDocCommentOwner]
        docOwner.safeMap(_.getDocComment).map(_.getText).getOrElse("")
      }
      else ""

    classMember match {
      case member: ScMethodMember =>
        myMember = insertMethod(member, templDef, anchor, needsOverrideModifier, typeAdjuster)
      case _: ScExtensionMethodMember =>
        throw new AssertionError("Unexpected extension method member. It's expected that all extension method members are grouped in ScMethodMember")
      case member: ScExtensionMember =>
        myMember = insertExtension(member, templDef, anchor, typeAdjuster)
      case ScAliasMember(alias, substitutor, _) =>
        val m = createOverrideImplementType(alias, substitutor, needsOverrideModifier, aClass, comment)(alias.getManager, Context(anchor))

        val added = templDef.addMember(m, Option(anchor))
        addTargetNameAnnotationIfNeeded(added, alias)
        myMember = added
        typeAdjuster.markToAdjust(added)
      case member: ScValueOrVariableMember[_] =>
        val m: ScMember = createVariable(comment, member, aClass, needsOverrideModifier)
        val added = templDef.addMember(m, Option(anchor))
        addTargetNameAnnotationIfNeeded(added, if (member.element.is[ScClassParameter]) member.element else member.getElement)
        myMember = added
        typeAdjuster.markToAdjust(added)
      case _ =>
    }
  }

  override def findInsertionAnchor(aClass: PsiClass, leaf: PsiElement): PsiElement = {
    aClass match {
      case td: ScTemplateDefinition =>
        ScalaOIUtil.getAnchor(td, leaf).orNull
      case _ =>
        super.findInsertionAnchor(aClass, leaf)
    }
  }

  override def positionCaret(editor: Editor, toEditMethodBody: Boolean): Unit = {
    val element = getPsiMember
    ScalaGenerationInfo.positionCaret(editor, element)
  }
}

object ScalaGenerationInfo {
  def defaultValue: String = "???"

  def positionCaret(editor: Editor, element: PsiMember): Unit = {
    //hack for postformatting IDEA bug.
    val member =
      try CodeStyleManager.getInstance(element.getProject).reformat(element)
      catch { case _: AssertionError => /*¯\_(ツ)_/¯*/  element }

    //Setting selection
    //For example when we implement some method foo we place a selection on the body placeholder ???:
    // override def foo: String = <selection>???</selection>
    val bodyOpt: Option[ScalaPsiElement] = member match {
      case ta: ScTypeAliasDefinition => ta.aliasedTypeElement
      case ScPatternDefinition.expr(expr) => Some(expr)
      case ScVariableDefinition.expr(expr) => Some(expr)
      case method: ScFunctionDefinition => method.body
      case extension: ScExtension =>
        val methods = extension.extensionMethods
        methods match {
          case Seq(singleExtension: ScFunctionDefinition) => singleExtension.body
          case _ => None
        }
      case _ => None
    }

    val body = bodyOpt match {
      case Some(value) => value
      case None =>
        return
    }

    val offset = member.getTextRange.getStartOffset
    val point = editor.visualPositionToXY(editor.offsetToVisualPosition(offset))
    if (!editor.getScrollingModel.getVisibleArea.contains(point)) {
      member match {
        case n: Navigatable => n.navigate(true)
        case _ =>
      }
    }

    body match {
      case e: ScBlockExpr =>
        val statements = e.statements
        if (statements.isEmpty) {
          editor.getCaretModel.moveToOffset(body.getTextRange.getStartOffset + 1)
        } else {
          val range = new TextRange(statements.head.getTextRange.getStartOffset, statements.last.getTextRange.getEndOffset)
          editor.getCaretModel.moveToOffset(range.getStartOffset)
          editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
        }
      case _ =>
        val range = body.getTextRange
        editor.getCaretModel.moveToOffset(range.getStartOffset)
        editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
    }
  }

  private def callSuperText(td: ScTemplateDefinition, method: PsiMethod): String = {
    val superOrSelfQual: String = td.selfType match {
      case None => "super."
      case Some(st: ScType) =>
        val psiClass = st.extractClass.getOrElse(return "super.")

        def nonStrictInheritor(base: PsiClass, inheritor: PsiClass): Boolean = {
          if (base == null || inheritor == null) false
          else base == inheritor || inheritor.isInheritorDeep(base, null)
        }

        if (nonStrictInheritor(method.containingClass, psiClass))
          td.selfTypeElement.get.name + "."
        else "super."
    }

    def paramText(param: PsiParameter) = {
      val name = ScalaNamesUtil.escapeKeyword(param.name).toOption.getOrElse("")
      val whitespace = if (name.endsWith("_")) " " else ""
      name + (if (param.isVarArgs) whitespace + ": _*" else "")
    }

    val methodName = ScalaNamesUtil.escapeKeyword(method.name)
    val parametersText: String = {
      def renderTermClauseArguments(clause: ScParameterClause): String =
        clause.parameters.map(_.name).mkString("(", ", ", ")")

      def renderTypeClauseArguments(clause: ScTypeParamClause): String =
        clause.typeParameters.map(_.name).mkString("[", ", ", "]")

      // When we delegate to super extension call, we need to pass receiver/target argument as well.
      // Extension signature has a classic shape (leading type params + term clauses), so we keep old behavior.
      val extensionArgumentsText: String = method match {
        case Parent(Parent(extension: ScExtension)) =>
          extension
            .allClauses
            .filterNot(_.isImplicit)
            .map(renderTermClauseArguments)
            .mkString
        case _ => ""
      }

      val methodArgumentsText = method match {
        case function: ScFunction =>
          function.signatureClauses.map {
            case TermClause(clause) =>
              if (clause.isImplicit) ""
              else                   renderTermClauseArguments(clause)
            case TypeClause(clause) => renderTypeClauseArguments(clause)
          }.mkString
        case _ => ""
      }

      if (methodArgumentsText.nonEmpty) extensionArgumentsText + methodArgumentsText
      else method match {
        case _: ScFunction => extensionArgumentsText
        case javaMethod =>
          val javaArgumentsText =
            if (javaMethod.isAccessor && javaMethod.getParameterList.getParametersCount == 0) ""
            else javaMethod.parameters.map(paramText).mkString("(", ", ", ")")
          extensionArgumentsText + javaArgumentsText
      }
    }

    superOrSelfQual + methodName + parametersText
  }

  def getMethodBody(member: ScMethodMember, td: ScTemplateDefinition, isImplement: Boolean): String =
    getMethodBody(member.getElement, member.scType, td, isImplement)

  def getExtensionMethodBody(member: ScExtensionMethodMember, td: ScTemplateDefinition, isImplement: Boolean): String =
    getMethodBody(member.getElement, member.scType, td, isImplement)

  private def getMethodBody(
    method: PsiMethod,
    returnType: ScType,
    td: ScTemplateDefinition,
    isImplement: Boolean
  ): String = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(td)
    implicit val context: types.Context = types.Context(td)

    val templateName =
      if (isImplement) ScalaFileTemplateUtil.SCALA_IMPLEMENTED_METHOD_TEMPLATE
      else ScalaFileTemplateUtil.SCALA_OVERRIDDEN_METHOD_TEMPLATE

    val template = FileTemplateManager.getInstance(td.getProject).getCodeTemplate(templateName)

    val properties = new Properties()

    val standardValue = getStandardValue(returnType)

    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType.presentableText)
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, standardValue)
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuperText(td, method))
    properties.setProperty("Q_MARK", ScalaGenerationInfo.defaultValue)

    ScalaFileTemplateUtil.setClassAndMethodNameProperties(properties, method.containingClass, method)

    template.getText(properties)
  }

  def insertMethod(member: ScMethodMember, td: ScTemplateDefinition, anchor: PsiElement, typeAdjuster: TypeAdjuster): ScFunction = {
    insertMethod(member, td, anchor, needsOverrideModifier = member.isOverride || toAddOverrideToImplemented, typeAdjuster)
  }

  private def insertMethod(member: ScMethodMember, td: ScTemplateDefinition, anchor: PsiElement, needsOverrideModifier: Boolean, typeAdjuster: TypeAdjuster): ScFunction = {
    val method: PsiMethod = member.getElement
    val ScMethodMember(signature, isOverride) = member

    val body = getMethodBody(member, td, !isOverride)

    val m = createOverrideImplementMethod(
      signature,
      needsOverrideModifier,
      body,
      td,
      withComment = ScalaApplicationSettings.getInstance().COPY_SCALADOC,
      withAnnotation = false
    )(method.getManager)

    val added = td.addMember(m, Option(anchor))
    addTargetNameAnnotationIfNeeded(added, method)
    TypeAnnotationUtil.removeTypeAnnotationIfNeeded(added, typeAnnotationsPolicy)
    typeAdjuster.markToAdjust(added)
    added.asInstanceOf[ScFunction]
  }

  private def insertExtension(
    member: ScExtensionMember,
    td: ScTemplateDefinition,
    anchor: PsiElement,
    typeAdjuster: TypeAdjuster
  ): ScExtension = {
    val ScExtensionMember(extension, methodMembers) = member

    val extensionMethodConstructionInfos = methodMembers.map { methodMember =>
      val methodSignature = methodMember.signature
      val bodyText = getExtensionMethodBody(methodMember, td, !methodMember.isOverride)
      val needsOverrideModifier = methodMember.isOverride || toAddOverrideToImplemented
      ExtensionMethodConstructionInfo(methodSignature, needsOverrideModifier, bodyText)
    }

    val newExtension = createOverrideImplementExtensionMethods(
      extensionMethodConstructionInfos,
      ScalaFeatures.forPsiOrDefault(td),
      wrapMultipleExtensionsWithBraces = !td.containingFile.exists(_.useIndentationBasedSyntax),
      withComment = ScalaApplicationSettings.getInstance().COPY_SCALADOC,
    )(extension.getManager)

    val addedExtension = td.addMember(newExtension, Option(anchor)).asInstanceOf[ScExtension]

    addedExtension.extensionMethods.zip(methodMembers).foreach { case (addedExtensionMethod, sourceMethodMember) =>
      addTargetNameAnnotationIfNeeded(addedExtensionMethod, sourceMethodMember.getElement)
      TypeAnnotationUtil.removeTypeAnnotationIfNeeded(addedExtensionMethod, typeAnnotationsPolicy)
    }

    //This automatically adjusts types in all child extension methods
    typeAdjuster.markToAdjust(addedExtension)

    addedExtension
  }

  private def createVariable(
    comment: String,
    classMember: ClassMember,
    anchor: PsiElement,
    needsOverrideModifier: Boolean
  ): ScMember = {
    val isVal = classMember.is[ScValueMember]

    val value = classMember match {
      case x: ScValueMember => x.element
      case x: ScVariableMember => x.element
      case _ => ???
    }

    val substitutor = classMember match {
      case x: ScValueMember => x.substitutor
      case x: ScVariableMember => x.substitutor
      case _ => ???
    }

    val m = createOverrideImplementVariable(value, substitutor, needsOverrideModifier, isVal, anchor, comment)(value.getManager)
    TypeAnnotationUtil.removeTypeAnnotationIfNeeded(m, typeAnnotationsPolicy)
    m
  }

  def toAddOverrideToImplemented: Boolean =
    ScalaApplicationSettings.getInstance.ADD_OVERRIDE_TO_IMPLEMENTED

  def typeAnnotationsPolicy: ScalaApplicationSettings.ReturnTypeLevel =
    ScalaApplicationSettings.getInstance().SPECIFY_RETURN_TYPE_EXPLICITLY
}
