package org.jetbrains.plugins.scala
package codeInsight
package template

import com.intellij.codeInsight.template.{Expression, ExpressionContext, Result}
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType}
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt

import scala.util._

package object macros {

  private[macros] def findElementAtOffset(implicit context: ExpressionContext) = {
    val editor = context.getEditor
    val manager = PsiDocumentManager.getInstance(editor.getProject)
    val document = editor.getDocument

    manager.commitDocument(document)
    manager.getPsiFile(document) match {
      case scalaFile: ScalaFile => Option(scalaFile.findElementAt(context.getStartOffset))
      case _ => None
    }
  }

  private[macros] def resultToScExpr(result: Result)(implicit context: ExpressionContext): Option[ScType] =
    for {
      element <- findElementAtOffset
      expression <- ScalaPsiElementFactory.safe(_.createExpressionWithContextFromText(result.toString, element))
      typ <- expression.`type`().toOption
    } yield typ

  private[macros] def resolveScType(typeExpression: Expression)(implicit context: ExpressionContext): Option[ScType] = {
    val typeText = typeExpression.calculateResult(context).toString
    resolveScType(typeText, context.getPsiElementAtStartOffset)
  }

  private[macros] def resolveScType(typeText: String, context: PsiElement): Option[ScType] =
    for {
      te: ScTypeElement <- scTypeElement(typeText, context)
      t: ScType         <- te.`type`().toOption
    } yield t

  private[macros] def scTypeElement(typeExpression: Expression)(implicit context: ExpressionContext): Option[ScTypeElement] = {
    val typeText = typeExpression.calculateResult(context).toString
    scTypeElement(typeText, context.getPsiElementAtStartOffset)
  }

  private[macros] def scTypeElement(typeText: String, context: PsiElement): Option[ScTypeElement] =
    ScalaPsiElementFactory.safe(_.createTypeElementFromText(typeText, context.features)(context))

  private[macros] def arrayComponent(scType: ScType): Option[ScType] = scType match {
    case JavaArrayType(argument)                                         => Some(argument)
    case paramType@ParameterizedType(_, Seq(head)) if isArray(paramType) => Some(head)
    case _                                                               => None
  }

  private[macros] def isArray(scType: ScType): Boolean =
    scType.canonicalText.startsWith("_root_.scala.Array")

  private[macros] def createLookupItem(definition: ScTypeDefinition): ScalaLookupItem = {
    import ScTypeDefinitionImpl._
    val name = toQualifiedName(getPackageName(definition, DefaultSeparator, forJvmRepresentation = false) :+ Right(definition))()

    val lookupItem = new ScalaLookupItem(definition, name, Option(definition.getContainingClass))
    lookupItem.shouldImport = true
    lookupItem
  }
}
