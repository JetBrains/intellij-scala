package org.jetbrains.plugins.scala.editor.documentationProvider

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.types.ScType

private object ScalaDocumentationUtils {

  // TODO: review usages, maybe propper way will be to use null / None?
  val EmptyDoc = ""

  def getKeyword(element: PsiElement): String = element match {
    case _: ScClass                     => "class "
    case _: ScObject                    => "object "
    case _: ScTrait                     => "trait "
    case _: ScFunction                  => "def "
    case c: ScClassParameter if c.isVal => "val "
    case c: ScClassParameter if c.isVar => "var "
    case _: ScValue                     => "val "
    case _: ScVariable                  => "var "
    case _                              => ""
  }
}
