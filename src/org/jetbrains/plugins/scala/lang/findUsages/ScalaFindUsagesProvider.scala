package org.jetbrains.plugins.scala
package lang
package findUsages

import com.intellij.psi._
import psi.api.base.patterns.ScBindingPattern
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.lang.cacheBuilder.{DefaultWordsScanner, WordsScanner}
import lexer.{ScalaLexer, ScalaTokenTypes}
import psi.api.statements.{ScFunction, ScValue, ScTypeAlias, ScVariable}
import psi.api.toplevel.ScNamedElement
import psi.api.toplevel.typedef.{ScClass, ScTypeDefinition, ScTrait, ScObject}
import com.intellij.lang.findUsages.FindUsagesProvider
import org.jetbrains.annotations.{Nullable, NotNull}
import com.intellij.psi.tree.TokenSet

class ScalaFindUsagesProvider extends FindUsagesProvider {
  @Nullable
  override def getWordsScanner(): WordsScanner = new DefaultWordsScanner(new ScalaLexer(),
    ScalaTokenTypes.IDENTIFIER_TOKEN_SET,
    ScalaTokenTypes.COMMENTS_TOKEN_SET,
    ScalaTokenTypes.STRING_LITERAL_TOKEN_SET);

  override def canFindUsagesFor(element: PsiElement): Boolean = {
    element match {
      case _: ScNamedElement | _: PsiMethod | _: PsiClass | _: PsiVariable => true
      case _ => false
    }
  }

  @Nullable
  override def getHelpId(psiElement: PsiElement): String = null

  @NotNull
  override def getType(element: PsiElement): String = {
    element match {
      case _: ScTypeAlias => "type"
      case _: ScClass => "class"
      case _: ScObject => "object"
      case _: ScTrait => "trait"
      case c: PsiClass => if (c.isInterface) "interface" else "class"
      case _: PsiMethod => "method"
      case _: ScBindingPattern => {
        var parent = element
        while (parent match {case null | _: ScValue | _: ScVariable => false case _ => true}) parent = parent.getParent
        parent match {
          case null => "pattern"
          case _ => "variable"
        }
      }
      case _: PsiField => "field"
      case _: PsiParameter => "parameter"
      case _: PsiVariable => "variable"
      case _ => ""
    }
  }



  @NotNull
  override def getDescriptiveName(element: PsiElement): String = {
    val name = element match {
      case c: PsiClass => c.getQualifiedName
      case x: PsiMethod => PsiFormatUtil.formatMethod(x, PsiSubstitutor.EMPTY,
        PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
        PsiFormatUtilBase.SHOW_TYPE) + " of " + getDescriptiveName(x.getContainingClass)
      case x: PsiVariable => x.getName
      case x: PsiFile => x.getName
      case x: ScNamedElement => x.getName
      case _ => element.getText
    }
    if (name == null) return "anonymous" else name
  }

  @NotNull
  override def getNodeText(element: PsiElement, useFullName: Boolean): String = {
    element match {
      case c: PsiClass => if (useFullName) c.getQualifiedName else c.getName
      case c: PsiMethod => PsiFormatUtil.formatMethod(c, PsiSubstitutor.EMPTY,
              PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
              PsiFormatUtilBase.SHOW_TYPE)
      case c: PsiVariable => c.getName
      case c: PsiFile => c.getName
      case c: ScNamedElement => c.getName
      case _ => {
        val text = element.getText
        if (text == null) "" else text
      }
    }
  }
}