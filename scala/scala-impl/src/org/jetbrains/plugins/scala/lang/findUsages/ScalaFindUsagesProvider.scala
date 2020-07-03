package org.jetbrains.plugins.scala
package lang
package findUsages

import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi._
import com.intellij.psi.util.{PsiFormatUtil, PsiFormatUtilBase}
import org.jetbrains.annotations.{NotNull, Nullable}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake

class ScalaFindUsagesProvider extends FindUsagesProvider {
  @Nullable
  override def getWordsScanner: WordsScanner = new ScalaWordsScanner()

  override def canFindUsagesFor(element: PsiElement): Boolean = {
    element match {
      case _: ScNamedElement | _: PsiMethod | _: PsiClass | _: PsiVariable => true
      case _ => false
    }
  }

  @Nullable
  override def getHelpId(psiElement: PsiElement): String = null

  //noinspection ReferencePassedToNls
  @NotNull
  override def getType(element: PsiElement): String = ScalaFindUsagesUtil.getType(element)

  @NotNull
  override def getDescriptiveName(element: PsiElement): String = {
    val name = element match {
      case x: PsiMethod =>
        var res = PsiFormatUtil.formatMethod(x, PsiSubstitutor.EMPTY,
        PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
        PsiFormatUtilBase.SHOW_TYPE)
        if (x.containingClass != null) res = res + " of " + getDescriptiveName(x.containingClass)
        res
      case x: PsiVariable => x.name
      case x: PsiFile => x.name
      case x: ScTypeDefinition => x.qualifiedName
      case x: ScNamedElement => x.name
      case c: PsiClass if !c.isInstanceOf[PsiClassFake] => c.qualifiedName
      case _ => element.getText
    }
    //noinspection ReferencePassedToNls
    Option(name) getOrElse "anonymous"
  }

  @NotNull
  override def getNodeText(element: PsiElement, useFullName: Boolean): String = {
    val name = element match {
      case c: PsiMethod => PsiFormatUtil.formatMethod(c, PsiSubstitutor.EMPTY,
              PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
              PsiFormatUtilBase.SHOW_TYPE)
      case c: PsiVariable => c.name
      case c: PsiFile => c.name
      case c: ScTypeDefinition => if (useFullName) c.qualifiedName else c.name
      case c: ScNamedElement => c.name
      case c: PsiClass if !c.isInstanceOf[PsiClassFake] => if (useFullName) c.qualifiedName else c.name
      case _ => element.getText
    }
    //noinspection ReferencePassedToNls
    Option(name) getOrElse "anonymous"
  }
}
