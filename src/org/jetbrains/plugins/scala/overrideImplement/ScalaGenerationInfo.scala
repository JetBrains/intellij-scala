package org.jetbrains.plugins.scala
package overrideImplement

import com.intellij.codeInsight.generation.GenerationInfoBase
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScSubstitutor}
import org.jetbrains.plugins.scala.lang.psi.types.result.TypingContext
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.actions.ScalaFileTemplateUtil
import com.intellij.ide.fileTemplates.{FileTemplate, FileTemplateManager}
import java.util.Properties
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil
import extensions._
import com.intellij.openapi.editor.Editor
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.pom.Navigatable
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.scala.config.ScalaVersionUtil._
import org.jetbrains.plugins.scala.lang.psi.types.result.Failure
import org.jetbrains.plugins.scala.lang.psi.types.result.Success
import org.jetbrains.plugins.scala.settings.ScalaApplicationSettings
import com.intellij.openapi.application.ApplicationManager


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
    val addOverrideToImplemented = 
      if (ApplicationManager.getApplication.isUnitTestMode) false 
      else ScalaApplicationSettings.getInstance.ADD_OVERRIDE_TO_IMPLEMENTED
    val needsInferType = ScalaApplicationSettings.getInstance.SPECIFY_RETURN_TYPE_EXPLICITLY

    classMember match {
      case member: ScMethodMember =>
        val method: PsiMethod = member.getElement
        val sign = member.sign.updateSubst(addUpdateThisType(_, templDef))

        val isImplement = !member.needsOverride
        val templateName =
          if (isImplement) ScalaFileTemplateUtil.SCALA_IMPLEMENTED_METHOD_TEMPLATE
          else ScalaFileTemplateUtil.SCALA_OVERRIDDEN_METHOD_TEMPLATE

        val template = FileTemplateManager.getInstance().getCodeTemplate(templateName)

        val properties = new Properties()

        val returnType = member.scType

        val standardValue = ScalaPsiElementFactory.getStandardValue(returnType)
        properties.setProperty(FileTemplate.ATTRIBUTE_RETURN_TYPE, ScType.presentableText(returnType))
        properties.setProperty(FileTemplate.ATTRIBUTE_DEFAULT_RETURN_VALUE, standardValue)
        properties.setProperty(FileTemplate.ATTRIBUTE_CALL_SUPER, callSuperText(templDef, method))
        properties.setProperty("Q_MARK", ScalaGenerationInfo.defaultValue(returnType, aClass.getContainingFile))

        ScalaFileTemplateUtil.setClassAndMethodNameProperties(properties, method.containingClass, method)

        val body = template.getText(properties)

        val needsOverride = !isImplement || addOverrideToImplemented
        val m = ScalaPsiElementFactory.createOverrideImplementMethod(sign, method.getManager, needsOverride, needsInferType, body)
        val added = templDef.addMember(m, Option(anchor))
        myMember = added
        ScalaPsiUtil.adjustTypes(added)
      case member: ScAliasMember =>
        val alias = member.getElement
        val substitutor = addUpdateThisType(member.substitutor, templDef)
        val needsOverride = member.needsOverride || addOverrideToImplemented
        val m = ScalaPsiElementFactory.createOverrideImplementType(alias, substitutor, alias.getManager, needsOverride)
        val added = templDef.addMember(m, Option(anchor))
        myMember = added
        ScalaPsiUtil.adjustTypes(added)
      case _: ScValueMember | _: ScVariableMember =>
        val isVal = classMember match {case _: ScValueMember => true case _: ScVariableMember => false}
        val value = classMember match {case x: ScValueMember => x.element case x: ScVariableMember => x.element}
        val (origSubstitutor, needsOverride) = classMember match {
          case x: ScValueMember => (x.substitutor, x.needsOverride)
          case x: ScVariableMember => (x.substitutor, x.needsOverride)
        }
        val substitutor = addUpdateThisType(origSubstitutor, templDef)
        val addOverride = needsOverride || addOverrideToImplemented
        val m = ScalaPsiElementFactory.createOverrideImplementVariable(value, substitutor, value.getManager,
          addOverride, isVal, needsInferType)
        val added = templDef.addMember(m, Option(anchor))
        myMember = added
        ScalaPsiUtil.adjustTypes(added)
      case _ =>
    }
  }

  private def callSuperText(td: ScTemplateDefinition, method: PsiMethod): String = {
    val superOrSelfQual: String = td.selfType match {
      case None => "super."
      case Some(st: ScType) =>
        val psiClass = ScType.extractClass(st, Option(td.getProject)).getOrElse(return "super.")

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

  override def findInsertionAnchor(aClass: PsiClass, leaf: PsiElement): PsiElement = {
    aClass match {
      case td: ScTemplateDefinition => ScalaOIUtil.getAnchor(leaf.getTextRange.getStartOffset, td).getOrElse(null)
      case _ => super.findInsertionAnchor(aClass, leaf)
    }
  }

  override def positionCaret(editor: Editor, toEditMethodBody: Boolean): Unit = {
    val element = getPsiMember
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
        if (statements.length == 0) {
          editor.getCaretModel.moveToOffset(body.getTextRange.getStartOffset + 1)
        } else {
          val range = new TextRange(statements(0).getTextRange.getStartOffset, statements(statements.length - 1).getTextRange.getEndOffset)
          editor.getCaretModel.moveToOffset(range.getStartOffset)
          editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
        }
      case _ =>
        val range = body.getTextRange
        editor.getCaretModel.moveToOffset(range.getStartOffset)
        editor.getSelectionModel.setSelection(range.getStartOffset, range.getEndOffset)
    }
  }

  private def addUpdateThisType(subst: ScSubstitutor, clazz: ScTemplateDefinition) = clazz.getType(TypingContext.empty) match {
    case Success(tpe, _) => subst.addUpdateThisType(tpe)
    case Failure(_, _) => subst
  }
}

object ScalaGenerationInfo {
  def defaultValue(returnType: ScType, file: PsiFile) = {
    val standardValue = ScalaPsiElementFactory.getStandardValue(returnType)

    if (isGeneric(file, false, SCALA_2_7, SCALA_2_8, SCALA_2_9)) standardValue
    else "???"
  }
}
