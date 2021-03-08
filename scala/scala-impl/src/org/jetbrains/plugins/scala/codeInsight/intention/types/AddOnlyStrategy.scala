package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.codeInsight.intention.types.AddOnlyStrategy._
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
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, StdTypes}
import org.jetbrains.plugins.scala.lang.psi.types.api.presentation.ScTypeText
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, ScType, TermSignature}
import org.jetbrains.plugins.scala.lang.refactoring._
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.annotations.Implementation

class AddOnlyStrategy(editor: Option[Editor] = None) extends Strategy {

  override def functionWithType(function: ScFunctionDefinition,
                                typeElement: ScTypeElement): Boolean = true

  override def valueWithType(value: ScPatternDefinition,
                             typeElement: ScTypeElement): Boolean = true

  override def variableWithType(variable: ScVariableDefinition,
                                typeElement: ScTypeElement): Boolean = true

  override def patternWithType(pattern: ScTypedPattern): Boolean = true

  override def parameterWithType(param: ScParameter): Boolean = true

  override def underscoreSectionWithType(underscore: ScUnderscoreSection) = true

  override def functionWithoutType(function: ScFunctionDefinition): Boolean = {
    addTypeAnnotation(typesForMember(function), function, function.paramClauses)
    true
  }

  override def valueWithoutType(value: ScPatternDefinition): Boolean = {
    addTypeAnnotation(typesForMember(value), value, value.pList)
    true
  }

  override def variableWithoutType(variable: ScVariableDefinition): Boolean = {
    addTypeAnnotation(typesForMember(variable), variable, variable.pList)
    true
  }

  override def patternWithoutType(pattern: ScBindingPattern): Boolean = {
    addTypeAnnotation(pattern.expectedType, pattern.getParent, pattern)
    true
  }

  override def wildcardPatternWithoutType(pattern: ScWildcardPattern): Boolean = {
    addTypeAnnotation(pattern.expectedType, pattern.getParent, pattern)
    true
  }

  override def parameterWithoutType(param: ScParameter): Boolean = {
    param.parentsInFile.findByType[ScFunctionExpr] match {
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
    addTypeAnnotation(underscore.`type`().toOption, underscore.getParent, underscore)
    true
  }

  def addTypeAnnotation(ty: ScType, context: PsiElement, anchor: PsiElement): Unit =
    addTypeAnnotation(Some(ty), context, anchor)

  def addTypeAnnotation(tyOpt: Option[ScType], context: PsiElement, anchor: PsiElement): Unit = {
    val ty = tyOpt.getOrElse(StdTypes.instance(context).Any)
    addTypeAnnotation(Seq(TypeForAnnotation(ty)), context, anchor)
  }

  def addTypeAnnotation(types: Seq[TypeForAnnotation], context: PsiElement, anchor: PsiElement): Unit = {
    assert(types.nonEmpty)
    val tps = types.flatMap(_.typeWithSuperTypes)
    val validVariants = tps.reverse.flatMap(_.`type`().toOption).map(ScTypeText(_)(context))

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

        maybeExpression.foreach {
          case call@Implementation.EmptyCollectionFactoryCall(ref) =>
            val replacement = createElementFromText(ref.getText)(ref.projectContext)
            call.replace(replacement)
          case _ =>
        }
    }
  }

  private def typesForMember(element: ScMember): Seq[TypeForAnnotation] = {

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

    val computedType = {
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
      computedType.map(TypeForAnnotation(_))
    }

    val typeFromSuper = superSignatures(element)
      .iterator
      .map(signatureType)
      .find(_.nonEmpty)
      .flatten
      .map(TypeForAnnotation(_, addSuperTypes = false))

    typeFromSuper.toSeq ++ computedType match {
      case Seq(st, t) if t.ty.isNothing || t.ty.isAny =>
        // if the computed type is nothing or any, the super type can only be more expressive
        Seq(st)
      case Seq(st, t) if !t.ty.isAliasType && t.ty.equiv(st.ty) =>
        // if both types are equivalent, use the super type, because it might be a type alias
        // except, of course, the computed type is a type alias then better let the user choose
        Seq(st)
      case Seq() =>
        implicit val projectContext: ProjectContext = element.getProject
        Seq(TypeForAnnotation(StdTypes.instance.Any))
      case oneOrBoth => oneOrBoth
    }
  }
}

object AddOnlyStrategy {
  case class TypeForAnnotation(ty: ScType, addSuperTypes: Boolean = true) {
    def typeWithSuperTypes: Seq[ScTypeElement] =
      if (addSuperTypes) AddOnlyStrategy.annotationsFor(ty)
      else Seq(createTypeElementFromText(ty.canonicalCodeText)(ty.projectContext))
  }

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
      case Some(sc: ScTypeDefinition) if sc.isObject && sc.supers.exists(_.isSealed) =>
        // if the type is the type of an object we prefer the super class
        get(tpe).find(_.extractClass.exists(_.isSealed)).toSeq
          .map(_.canonicalCodeText)
      case _ => Seq.empty
    })
  }
}
