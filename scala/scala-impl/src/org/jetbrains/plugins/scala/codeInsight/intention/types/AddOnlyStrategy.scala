package org.jetbrains.plugins.scala
package codeInsight
package intention
package types

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiClass, PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.{ScBindingPattern, ScTypedPattern, ScWildcardPattern}
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScMember, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, ScTypeText}
import org.jetbrains.plugins.scala.lang.psi.types.{BaseTypes, ScCompoundType, ScType, Signature}
import org.jetbrains.plugins.scala.project.ProjectContext
import org.jetbrains.plugins.scala.settings.annotations.Implementation

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
    typeForMember(function).foreach {
      addTypeAnnotation(_, function, function.paramClauses)
    }

    true
  }

  override def valueWithoutType(value: ScPatternDefinition): Boolean = {
    typeForMember(value).foreach {
      addTypeAnnotation(_, value, value.pList)
    }

    true
  }

  override def variableWithoutType(variable: ScVariableDefinition): Boolean = {
    typeForMember(variable).foreach {
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
    underscore.`type`().foreach {
      addTypeAnnotation(_, underscore.getParent, underscore)
    }

    true
  }

  def addTypeAnnotation(t: ScType, context: PsiElement, anchor: PsiElement): Unit = {
    import AddOnlyStrategy._
    val tps = annotationsFor(t, context)
    val added = addActualType(tps.head, anchor)

    editor match {
      case Some(e) if tps.size > 1 =>
        val texts = tps.reverse.flatMap(_.`type`().toOption).map(ScTypeText)
        val expr = new ChooseTypeTextExpression(texts)
        // TODO Invoke the simplification
        IntentionUtil.startTemplate(added, context, expr, e)
      case _ =>
        ScalaPsiUtil.adjustTypes(added)

        val maybeExpression = context match {
          case variable: ScVariableDefinition => variable.expr
          case pattern: ScPatternDefinition => pattern.expr
          case function: ScFunctionDefinition => function.body
          case _ => None
        }

        maybeExpression
          .zip(simplify(maybeExpression))
          .foreach {
            case (expression, replacement) => expression.replace(replacement)
          }
    }
  }

  private def simplify(maybeExpression: Option[ScExpression]) = maybeExpression.collect {
    case ScGenericCall(referenced, _) if Implementation.isEmptyCollectionFactory(referenced) =>
      implicit val context = referenced.projectContext
      createExpressionFromText(referenced.getText)
  }

  private def typeForMember(element: ScMember): Option[ScType] = {

    def signatureType(sign: Signature): Option[ScType] = {
      val substitutor = sign.substitutor
      sign.namedElement match {
        case f: ScFunction =>
          f.returnType.toOption.map(substitutor.subst)
        case m: PsiMethod =>
          implicit val ctx: Project = m.getProject
          Option(m.getReturnType).map(psiType => substitutor.subst(psiType.toScType()))
        case t: ScTypedDefinition =>
          t.`type`().toOption.map(substitutor.subst)
        case _ => None
      }
    }

    def superSignatures(member: ScMember): Seq[Signature] = {
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
      val signaturesForNamed =
        signatureMap
          .forName(named.name)._1
          .filter(sign => sign._1.namedElement == named)
      signaturesForNamed.flatMap(_._2.supers.map(_.info))
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

    val shouldTrySuperMember = computedType match {
      case None => true
      case Some(t) => t.isNothing || t.isAny
    }

    if (shouldTrySuperMember) {
      val supers = superSignatures(element).iterator
      supers
        .map(signatureType)
        .find(_.nonEmpty)
        .flatten
    }
    else computedType
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
        underscore.replace(if (needsParentheses) e else e.expr.get)

      case _ =>
        anchor.appendSiblings(createColon, createWhitespace, annotation).last
    }
  }

  def annotationFor(`type`: ScType, anchor: PsiElement): Option[ScTypeElement] =
    annotationsFor(`type`, anchor).headOption

  private def annotationsFor(`type`: ScType, context: PsiElement): Seq[ScTypeElement] = {
    import `type`.projectContext

    val canonicalTypes = `type` match {
      case compound@ScCompoundType(comps, _, _) =>
        val uselessTypes = Set("_root_.scala.Product", "_root_.scala.Serializable", "_root_.java.lang.Object")
        val filtered = comps.filterNot(c => uselessTypes.contains(c.canonicalText))
        val newCompType = compound.copy(components = filtered)
        Seq(newCompType.canonicalText)
      case tp =>
        import BaseTypes.get

        val baseTypes = tp.extractClass match {
          case Some(sc: ScTypeDefinition) if sc.getTruncedQualifiedName == "scala.Some" =>
            get(tp).map(_.canonicalText)
              .filter(_.startsWith("_root_.scala.Option"))
          case Some(sc: ScTypeDefinition) if sc.getTruncedQualifiedName.startsWith("scala.collection") =>
            val goodTypes = Set(
              "_root_.scala.collection.mutable.Seq[",
              "_root_.scala.collection.immutable.Seq[",
              "_root_.scala.collection.mutable.Set[",
              "_root_.scala.collection.immutable.Set[",
              "_root_.scala.collection.mutable.Map[",
              "_root_.scala.collection.immutable.Map["
            )

            get(tp).map(_.canonicalText)
              .filter(t => goodTypes.exists(t.startsWith))
          case Some(sc: ScTypeDefinition) if (sc +: sc.supers).exists(isSealed) =>
            get(tp).find(_.extractClass.exists(isSealed)).toSeq
              .map(_.canonicalText)
          case _ => Seq.empty
        }

        tp.canonicalText +: baseTypes
    }

    canonicalTypes.map(createTypeElementFromText)
  }

  private[this] def isSealed(clazz: PsiClass) = clazz match {
    case _: ScClass | _: ScTrait => clazz.hasModifierPropertyScala("sealed")
    case _ => false
  }
}
