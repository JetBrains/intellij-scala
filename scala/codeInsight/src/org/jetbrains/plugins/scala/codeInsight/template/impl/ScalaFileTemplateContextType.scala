package org.jetbrains.plugins.scala.codeInsight.template.impl

import com.intellij.codeInsight.template.{FileTypeBasedContextType, TemplateActionContext, TemplateContextType}
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}
import org.jetbrains.plugins.scala.{ScalaFileType, ScalaLanguage}

final class ScalaFileTemplateContextType extends FileTypeBasedContextType(
  ScalaLanguage.INSTANCE.getDisplayName,
  ScalaFileType.INSTANCE
)

object ScalaFileTemplateContextType {

  private[impl] abstract class ElementContextType(@Nls presentableName: String)
    extends TemplateContextType(presentableName) {

    protected def isInContextInScalaFile(context: TemplateActionContext)(implicit file: ScalaFile): Boolean

    override final def isInContext(context: TemplateActionContext): Boolean =
      context.getFile match {
        case scalaFile: ScalaFile =>
          isInContextInScalaFile(context)(scalaFile)
        case _ =>
          false
      }
  }

  //noinspection ConvertibleToMethodValue
  private[impl] def isInContext[T <: ScalaPsiElement](
    context: TemplateActionContext,
    clazz: Class[T]
  )(
    predicate: T => Boolean = (x: T) => true
  )(implicit file: ScalaFile): Boolean = {
    val parent = PsiTreeUtil.getParentOfType(file.findElementAt(context.getStartOffset), clazz)
    parent != null &&  predicate(parent)
  }
}
