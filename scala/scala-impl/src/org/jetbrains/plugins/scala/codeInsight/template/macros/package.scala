package org.jetbrains.plugins.scala
package codeInsight
package template

import com.intellij.codeInsight.template.{ExpressionContext, Result}
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.scala.lang.completion.lookups.ScalaLookupItem
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.ScTypeDefinitionImpl
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{JavaArrayType, ParameterizedType}

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

  private[macros] def resultToScExpr(result: Result)
                                    (implicit context: ExpressionContext): Option[ScType] =
    Try {
      findElementAtOffset.map(ScalaPsiElementFactory.createExpressionFromText(result.toString, _))
    } match {
      case Success(value) => value.flatMap(_.`type`().toOption)
      case Failure(_: IncorrectOperationException) => None
    }

  private[macros] def arrayComponent(scType: ScType): Option[ScType] = scType match {
    case JavaArrayType(argument) => Some(argument)
    case paramType@ParameterizedType(_, Seq(head)) if paramType.canonicalText.startsWith("_root_.scala.Array") => Some(head)
    case _ => None
  }

  private[macros] def createLookupItem(definition: ScTypeDefinition): ScalaLookupItem = {
    import ScTypeDefinitionImpl._
    val name = toQualifiedName(packageName(definition)(Nil, DefaultSeparator) :+ Right(definition))()

    val lookupItem = new ScalaLookupItem(definition, name, Option(definition.getContainingClass))
    lookupItem.shouldImport = true
    lookupItem
  }
}
