package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.psi._
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.base.ScFieldId
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScTypeParam
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScTypeAlias, ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake

object ScalaFindUsagesUtil {
  def getType(element: PsiElement): String = element match {
    case _: ScTypeAlias => "type"
    case _: ScClass => "class"
    case _: ScObject => "object"
    case _: ScTrait => "trait"
    case c: PsiClass if !c.isInstanceOf[PsiClassFake] => if (c.isInterface) "interface" else "class"
    case _: PsiMethod => "method"
    case _: ScTypeParam => "type parameter"
    case _: ScBindingPattern =>
      val parents = element.withParents
      val parent = parents.find {
        case null | _: ScValue | _: ScVariable => true
        case _ => false
      }
      parent.fold("pattern")(_ => "variable")
    case _: PsiField => "field"
    case _: PsiParameter => "parameter"
    case _: PsiVariable => "variable"
    case f: ScFieldId =>
      ScalaPsiUtil.nameContext(f) match {
        case _: ScValue => "pattern"
        case _: ScVariable => "variable"
        case _ => "pattern"
      }
    case _ => ""
  }
}
