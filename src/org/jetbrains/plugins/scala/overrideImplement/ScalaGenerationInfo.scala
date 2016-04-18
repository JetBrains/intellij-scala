package org.jetbrains.plugins.scala
package overrideImplement

import java.util.Properties

import com.intellij.codeInsight.generation.GenerationInfoBase
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.pom.Navigatable
import com.intellij.psi._
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.plugins.scala.actions.ScalaFileTemplateUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import org.jetbrains.plugins.scala.overrideImplement.ScalaGenerationInfo._
import org.jetbrains.plugins.scala.project.ScalaLanguageLevel.Scala_2_10
import org.jetbrains.plugins.scala.project._
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings

/**
 * Nikolay.Tropin
 * 12/25/13
 */
class ScalaGenerationInfo(classMember: ClassMember)
        extends GenerationInfoBase {

  private var myMember: PsiMember = classMember.getElement

  override def getPsiMember: PsiMember = myMember

  override def insert(aClass: PsiClass, anchor: PsiElement, before: Boolean): Unit = {
    val templDef = aClass match {
      case td: ScTemplateDefinition => td
      case _ => return
    }

    classMember match {
      case member: ScMethodMember => myMember = insertMethod(member, templDef, anchor)
      case member: ScAliasMember =>
        val alias = member.getElement
        val substitutor = member.substitutor
        val needsOverride = member.isOverride || toAddOverrideToImplemented
        val m = ScalaPsiElementFactory.createOverrideImplementType(alias, substitutor, alias.getManager, needsOverride)
        val added = templDef.addMember(m, Option(anchor))
        myMember = added
        TypeAdjuster.markToAdjust(added)
      case _: ScValueMember | _: ScVariableMember =>
        val isVal = classMember match {case _: ScValueMember => true case _: ScVariableMember => false}
        val value = classMember match {case x: ScValueMember => x.element case x: ScVariableMember => x.element}
        val (substitutor, needsOverride) = classMember match {
          case x: ScValueMember => (x.substitutor, x.isOverride)
          case x: ScVariableMember => (x.substitutor, x.isOverride)
        }
        val addOverride = needsOverride || toAddOverrideToImplemented
        val m = ScalaPsiElementFactory.createOverrideImplementVariable(value, substitutor, value.getManager,
          addOverride, isVal, needsInferType)
        val added = templDef.addMember(m, Option(anchor))
        myMember = added
        TypeAdjuster.markToAdjust(added)
      case _ =>
    }
  }

  override def findInsertionAnchor(aClass: PsiClass, leaf: PsiElement): PsiElement = {
    aClass match {
      case td: ScTemplateDefinition => ScalaOIUtil.getAnchor(leaf.getTextRange.getStartOffset, td).orNull
      case _ => super.findInsertionAnchor(aClass, leaf)
    }
  }

  override def positionCaret(editor: Editor, toEditMethodBody: Boolean): Unit = {
    val element = getPsiMember
    ScalaGenerationInfo.positionCaret(editor, element)
  }
}

object ScalaGenerationInfo {
  def defaultValue(returnType: ScType, file: PsiFile) = {
    val standardValue = ScalaPsiElementFactory.getStandardValue(returnType)

    if (file.scalaLanguageLevel.exists(_ < Scala_2_10)) standardValue else "???"
  }

  def positionCaret(editor: Editor, element: PsiMember) {
    //hack for postformatting IDEA bug.
    val member = CodeStyleManager.getInstance(element.getProject).reformat(element)
    //Setting selection
    val body: PsiElement = member match {
      case ta: ScTypeAliasDefinition => ta.aliasedTypeElement
      case ScPatternDefinition.expr(expr) => expr
      case ScVariableDefinition.expr(expr) => expr
      case method: ScFunctionDefinition => method.body match {
        case Some(x) => x
        case None => return
      }
      case _ => return
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
    import td.typeSystem
    val superOrSelfQual: String = td.selfType match {
      case None => "super."
      case Some(st: ScType) =>
        val psiClass = st.extractClass(td.getProject).getOrElse(return "super.")

        def nonStrictInheritor(base: PsiClass, inheritor: PsiClass): Boolean = {
          if (base == null || inheritor == null) false
          else base == inheritor || inheritor.isInheritorDeep(base, null)
        }

        if (nonStrictInheritor(method.containingClass, psiClass))
          td.selfTypeElement.get.name + "."
        else "super."
    }
    def paramText(param: PsiParameter) = {
      val name = ScalaNamesUtil.changeKeyword(param.name).toOption.getOrElse("")
      val whitespace = if (name.endsWith("_")) " " else ""
      name + (if (param.isVarArgs) whitespace + ": _*" else "")
    }
    val methodName = ScalaNamesUtil.changeKeyword(method.name)
    val parametersText: String = {
      method match {
        case fun: ScFunction =>
          val clauses = fun.paramClauses.clauses.filter(!_.isImplicit)
          clauses.map(_.parameters.map(_.name).mkString("(", ", ", ")")).mkString
        case method: PsiMethod =>
          if (method.isAccessor && method.getParameterList.getParametersCount == 0) ""
          else method.getParameterList.getParameters.map(paramText).mkString("(", ", ", ")")
      }
    }
    superOrSelfQual + methodName + parametersText
  }

  def getMethodBody(member: ScMethodMember, td: ScTemplateDefinition, isImplement: Boolean):String = {
    val templateName =
      if (isImplement) ScalaFileTemplateUtil.SCALA_IMPLEMENTED_METHOD_TEMPLATE
      else ScalaFileTemplateUtil.SCALA_OVERRIDDEN_METHOD_TEMPLATE

    val template = FileTemplateManager.getInstance().getCodeTemplate(templateName)

    val properties = new Properties()

    val returnType = member.scType

    val standardValue = ScalaPsiElementFactory.getStandardValue(returnType)

    val method = member.getElement

    properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, returnType.presentableText)
    properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, standardValue)
    properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuperText(td, method))
    properties.setProperty("Q_MARK", ScalaGenerationInfo.defaultValue(returnType, td.getContainingFile))

    ScalaFileTemplateUtil.setClassAndMethodNameProperties(properties, method.containingClass, method)

    template.getText(properties)
  }

  def insertMethod(member: ScMethodMember, td: ScTemplateDefinition, anchor: PsiElement): ScFunction = {
    val method: PsiMethod = member.getElement
    val sign = member.sign

    val isImplement = !member.isOverride

    val body = getMethodBody(member, td, isImplement)

    val needsOverride = !isImplement || toAddOverrideToImplemented
    val m = ScalaPsiElementFactory.createOverrideImplementMethod(sign, method.getManager, needsOverride, needsInferType, body)
    val added = td.addMember(m, Option(anchor))
    TypeAdjuster.markToAdjust(added)
    added.asInstanceOf[ScFunction]
  }

  def toAddOverrideToImplemented =
    if (ApplicationManager.getApplication.isUnitTestMode) false
    else ScalaApplicationSettings.getInstance.ADD_OVERRIDE_TO_IMPLEMENTED

  def needsInferType = ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY
}
