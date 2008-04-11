package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lang.cacheBuilder._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.annotations.{Nullable, NotNull}
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import lang.psi.api.toplevel._
import lang.psi.api.toplevel.typedef._

object ScalaFindUsagesProvider extends FindUsagesProvider {
  @Nullable
  override def getWordsScanner(): WordsScanner = new DefaultWordsScanner(new ScalaLexer(),
     ScalaTokenTypes.IDENTIFIER_TOKEN_SET,
     ScalaTokenTypes.COMMENTS_TOKEN_SET,
     ScalaTokenTypes.STRING_LITERAL_TOKEN_SET);

  override def canFindUsagesFor(element: PsiElement): Boolean = element.isInstanceOf[ScTypeDefinition] &&
                                                                !element.isInstanceOf[ScObject] //todo

  @Nullable
  override def getHelpId(psiElement: PsiElement): String = null //todo

  //todo
  @NotNull
  override def getType(element: PsiElement): String = {
    element match {
      case _ : ScClass=> "class"
      case _ : ScObject=> "object"
      case _ : ScTrait=> "trait"
      case _ => ""
    }
  }



  //todo
  @NotNull
  override def getDescriptiveName(element: PsiElement): String = {
    element match {
      case c : ScTypeDefinition => c.getName
      case _ => ""
    }
  }

  //todo
  @NotNull
  override def getNodeText(element: PsiElement, useFullName : Boolean): String = {
    element match {
      case c : ScTypeDefinition => c.getName
      case _ => ""
    }
  }
}