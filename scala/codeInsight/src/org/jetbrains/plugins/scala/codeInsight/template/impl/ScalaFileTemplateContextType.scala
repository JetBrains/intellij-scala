package org.jetbrains.plugins.scala
package codeInsight
package template
package impl

import com.intellij.codeInsight.template.{FileTypeBasedContextType, TemplateContextType}
import com.intellij.psi._
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.lang.psi.api.{ScalaFile, ScalaPsiElement}

final class ScalaFileTemplateContextType extends FileTypeBasedContextType(
  ScalaLanguage.INSTANCE.getDisplayName,
  ScalaFileType.INSTANCE
)

object ScalaFileTemplateContextType {

  private[impl] abstract class ElementContextType(@Nls presentableName: String)
    extends TemplateContextType(presentableName) {

    protected def isInContext(offset: Int)
                             (implicit file: ScalaFile): Boolean

    override final def isInContext(file: PsiFile, offset: Int): Boolean = file match {
      case scalaFile: ScalaFile => isInContext(offset)(scalaFile)
      case _ => false
    }
  }

  //noinspection ConvertibleToMethodValue
  private[impl] def isInContext[T <: ScalaPsiElement](offset: Int, clazz: Class[T])
                                                     (predicate: T => Boolean = Function.const(true) _)
                                                     (implicit file: ScalaFile): Boolean =
    util.PsiTreeUtil.getParentOfType(file.findElementAt(offset), clazz) match {
      case null => false
      case parent => predicate(parent)
    }
}
