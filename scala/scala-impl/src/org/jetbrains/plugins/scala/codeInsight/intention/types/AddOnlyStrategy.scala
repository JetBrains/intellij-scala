package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import java.util.concurrent.CompletableFuture

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.ui.popup.{JBPopupFactory, PopupStep}
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiMethod}
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ScTypeText}
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, ScType, TermSignature}
import org.jetbrains.plugins.scala.lang.refactoring._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.annotations.Implementation

import scala.collection.JavaConverters._

class AddOnlyStrategy(editor: Option[Editor] = None) extends Strategy {

  def functionWithType(function: ScFunctionDefinition,
                       typeElement: ScTypeElement): Boolean = true

  def valueWithType(value: ScPatternDefinition,
                    typeElement: ScTypeElement): Boolean = true

  def variableWithType(variable: ScVariableDefinition,
                       typeElement: ScTypeElement): Boolean = true

  override def patternWithType(pattern: ScTypedPattern): Boolean = true

  override def parameterWithType(param: ScParameter): Boolean = true

  override def underscoreSectionWithType(underscore: ScUnderscoreSection) = true

  override def functionWithoutType(function: ScFunctionDefinition): Boolean = {
    selectTypeForMember(function).thenAccept {
      addTypeAnnotation(_, function, function.paramClauses)
    }

    true
  }

  override def valueWithoutType(value: ScPatternDefinition): Boolean = {
    selectTypeForMember(value).thenAccept {
      addTypeAnnotation(_, value, value.pList)
    }

    true
  }

  override def variableWithoutType(variable: ScVariableDefinition): Boolean = {
    selectTypeForMember(variable).thenAccept {
      addTypeAnnotation(_, variable, variable.pList)
    }

    true
  }

  override def patternWithoutType(pattern: ScBindingPattern): Boolean = {
    pattern.expectedType.foreach {
      addTypeAnnotation(_, pattern.getParent, pattern)
    }

    true
  }

  override def wildcardPatternWithoutType(pattern: ScWildcardPattern): Boolean = {
    pattern.expectedType.foreach {
      addTypeAnnotation(_, pattern.getParent, pattern)
    }

    true
  }

  override def parameterWithoutType(param: ScParameter): Boolean = {
    param.parentsInFile.instanceOf[ScFunctionExpr] match {
      case Some(func) =>
        val index = func.parameters.indexOf(param)
        func.expectedType() match {
          case Some(FunctionType(_, params)) =>
            if (index >= 0 && index < params.length) {
              val paramExpectedType = params(index)
              addTypeAnnotation(paramExpectedType, param.getParent, param)
            }
          case _ =>
        }
      case _ =>
    }

    true
  }

  override def underscoreSectionWithoutType(underscore: ScUnderscoreSection): Boolean = {
    underscore.`type`().foreach {
      addTypeAnnotation(_, underscore.getParent, underscore)
    }

    true
  }

  def addTypeAnnotation(t: ScType, context: PsiElement, anchor: PsiElement): Unit = {
    import AddOnlyStrategy._
    val tps = annotationsFor(t)
    val validVariants = tps.reverse.flatMap(_.`type`().toOption).map(ScTypeText)

    val added = addActualType(tps.head, anchor)

    editor match {
      case Some(e) if validVariants.size > 1 =>
        val expr = new ChooseTypeTextExpression(validVariants)
        // TODO Invoke the simplification
        startTemplate(added, context, expr, e)
      case _ =>
        ScalaPsiUtil.adjustTypes(added)

        val maybeExpression = context match {
          case variable: ScVariableDefinition => variable.expr
          case pattern: ScPatternDefinition => pattern.expr
          case function: ScFunctionDefinition => function.body
          case _ => None
        }

        maybeExpression.collect {
          case call@Implementation.EmptyCollectionFactoryCall(ref) =>
            (call, createElementFromText(ref.getText)(ref.projectContext))
        }.foreach {
          case (expression, replacement) => expression.replace(replacement)
        }
    }
  }

  private def selectTypeForMember(element: ScMember): CompletableFuture[ScType] = {
    import CompletableFuture.completedFuture

    typeForMember(element) match {
      case Seq() => completedFuture(element.projectContext.stdTypes.Any)
      case Seq(one) => completedFuture(one)
      case multiple =>
        editor.fold(
          completedFuture(multiple.head)
        )(
          showTypeChooser(multiple, _)
        )
    }
  }

  private def typeForMember(element: ScMember): Seq[ScType] = {

    def signatureType(sign: TermSignature): Option[ScType] = {
      val substitutor = sign.substitutor
      sign.namedElement match {
        case f: ScFunction =>
          f.returnType.toOption.map(substitutor)
        case m: PsiMethod =>
          implicit val ctx: Project = m.getProject
          Option(m.getReturnType).map(psiType => substitutor(psiType.toScType()))
        case t: ScTypedDefinition =>
          t.`type`().toOption.map(substitutor)
        case _ => None
      }
    }

    def superSignatures(member: ScMember): Seq[TermSignature] = {
      val named = member match {
        case n: ScNamedElement => n
        case v: ScValueOrVariable if v.declaredElements.size == 1 => v.declaredElements.head
        case _ => return Seq.empty
      }

      val aClass = member match {
        case ContainingClass(c) => c
        case _ => return Seq.empty
      }

      val signatureMap = TypeDefinitionMembers.getSignatures(aClass)
      val signatureForNamed =
        signatureMap
          .forName(named.name)
          .findNode(named)

      signatureForNamed match {
        case None => Seq.empty
        case Some(node) => node.supers.map(_.info)
      }
    }

    val computedType = element match {
      case function: ScFunctionDefinition =>
        function.returnType.toOption
      case value: ScPatternDefinition =>
        value.`type`().toOption
      case variable: ScVariableDefinition =>
        variable.`type`().toOption
      case _ =>
        None
    }

    val typeFromSuper = superSignatures(element)
      .iterator
      .map(signatureType)
      .find(_.nonEmpty)
      .flatten

    typeFromSuper.toSeq ++ computedType match {
      case Seq(st, t) if t.isNothing || t.isAny =>
        // if the computed type is nothing or any, the super type can only be more expressive
        Seq(st)
      case Seq(st, t) if t.isAliasType.isEmpty && t.equiv(st) =>
        // if both types are equivalent, use the super type, because it might be a type alias
        // except, of course, the computed type is a type alias then better let the user choose
        Seq(st)
      case oneOrBoth => oneOrBoth
    }
  }

  def showTypeChooser(multiple: Seq[ScType], editor: Editor): CompletableFuture[ScType] = {
    implicit val project: Project = editor.getProject
    val resultFuture = new CompletableFuture[ScType]()
    val title = ScalaBundle.message("choose.inferred.or.super.type.popup.title")
    val popup: BaseListPopupStep[ScType] = new BaseListPopupStep[ScType](title, multiple.asJava) {
      override def getIconFor(value: ScType): Icon =
        value.extractDesignated(expandAliases = false).map(_.getIcon(0)).orNull

      override def getTextFor(value: ScType): String = {
        value.presentableText
      }

      override def onChosen(selectedValue: ScType, finalChoice: Boolean): PopupStep[_] = {
        if (selectedValue != null && finalChoice) {
          executeWriteActionCommand("Add type annotation action") {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            resultFuture.complete(selectedValue)
          }
        }
        PopupStep.FINAL_CHOICE
      }
    }
    JBPopupFactory.getInstance.createListPopup(popup).showInBestPositionFor(editor)

    resultFuture
  }
}

object AddOnlyStrategy {

  def addActualType(annotation: ScTypeElement, anchor: PsiElement): PsiElement = {
    implicit val ctx: ProjectContext = anchor

    anchor match {
      case p: ScParameter =>
        val parameter = p.getParent match {
          case Parent(Parent(Parent(_: ScBlockExpr))) => p
          // ensure  that the parameter is wrapped in parentheses before we add the type annotation.
          case clause: ScParameterClause if clause.parameters.length == 1 =>
            clause.replace(createClauseForFunctionExprFromText(p.getText.parenthesize()))
              .asInstanceOf[ScParameterClause].parameters.head
          case _ => p
        }

        parameter.nameId.appendSiblings(createColon, createWhitespace, createParameterTypeFromText(annotation.getText)).last

      case underscore: ScUnderscoreSection =>
        val needsParentheses = underscore.getParent match {
          case ScParenthesisedExpr(content) if content == underscore => false
          case _: ScArgumentExprList => false
          case _ => true
        }
        val e = createScalaFileFromText(s"(_: ${annotation.getText})").getFirstChild.asInstanceOf[ScParenthesisedExpr]
        underscore.replace(if (needsParentheses) e else e.innerElement.get)

      case _ =>
        anchor.appendSiblings(createColon, createWhitespace, annotation).last
    }
  }

  def annotationsFor(`type`: ScType): Seq[ScTypeElement] =
    canonicalTypes(`type`)
      .map(createTypeElementFromText(_)(`type`.projectContext))

  private[this] def canonicalTypes(tpe: ScType): Seq[String] = {
    import BaseTypes.get

    tpe.canonicalCodeText +: (tpe.extractClass match {
      case Some(sc: ScTypeDefinition) if sc.qualifiedName == "scala.Some" =>
        get(tpe).map(_.canonicalCodeText)
          .filter(_.startsWith("_root_.scala.Option"))
      case Some(sc: ScTypeDefinition) if sc.qualifiedName.startsWith("scala.collection") =>
        val goodTypes = Set(
          "_root_.scala.collection.mutable.Seq[",
          "_root_.scala.collection.immutable.Seq[",
          "_root_.scala.collection.mutable.Set[",
          "_root_.scala.collection.immutable.Set[",
          "_root_.scala.collection.mutable.Map[",
          "_root_.scala.collection.immutable.Map["
        )

        get(tpe).map(_.canonicalCodeText)
          .filter(t => goodTypes.exists(t.startsWith))
      case Some(sc: ScTypeDefinition) if (sc +: sc.supers).exists(_.isSealed) =>
        get(tpe).find(_.extractClass.exists(_.isSealed)).toSeq
          .map(_.canonicalCodeText)
      case _ => Seq.empty
    })
  }
}
