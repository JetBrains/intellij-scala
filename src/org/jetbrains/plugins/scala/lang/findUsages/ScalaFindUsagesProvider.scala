package org.jetbrains.plugins.scala.lang.findUsages

import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.lang.cacheBuilder._
import com.intellij.psi._
import org.jetbrains.plugins.scala.lang.lexer.ScalaLexer
import org.jetbrains.annotations.{Nullable, NotNull}
import com.intellij.psi.tree.TokenSet
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._

object ScalaFindUsagesProvider extends FindUsagesProvider {
  [Nullable]
  override def getWordsScanner(): WordsScanner = new DefaultWordsScanner(new ScalaLexer(),
     ScalaTokenTypes.IDENTIFIER_TOKEN_SET,
     ScalaTokenTypes.COMMENTS_TOKEN_SET,
     ScalaTokenTypes.STRING_LITERAL_TOKEN_SET);

  override def canFindUsagesFor(element: PsiElement): Boolean = element.isInstanceOf[ScTmplDef] //todo

  [Nullable]
  override def getHelpId(psiElement: PsiElement): String = null //todo

  //todo
  [NotNull]
  override def getType(element: PsiElement): String = {
    element match {
      case _ : ScClassDefinition => "class"
      case _ : ScObjectDefinition => "object"
      case _ : ScTraitDefinition => "trait"
      case _ => ""
    }
  }

  //todo
  [NotNull]
  override def getDescriptiveName(element: PsiElement): String = {
    element match {
      case c : ScTmplDef => c.getName
      case _ => ""
    }
  }

  //todo
  [NotNull]
  override def getNodeText(element: PsiElement, useFullName : Boolean): String = {
    element match {
      case c : ScTmplDef => c.getName
      case _ => ""
    }
  }
}