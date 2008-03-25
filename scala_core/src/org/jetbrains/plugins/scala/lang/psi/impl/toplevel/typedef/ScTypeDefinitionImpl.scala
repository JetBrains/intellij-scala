package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

/**
 * @author ilyas
 */

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiClass}
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.editor.colors._

import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import psi.api.toplevel.packaging._
import psi.api.toplevel.templates._

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.psi._;
import com.intellij.navigation._;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.util.IncorrectOperationException;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import _root_.java.util.Collection;
import _root_.java.util.Collections;
import _root_.java.util.List;

abstract class ScTypeDefinitionImpl(node: ASTNode) extends ScalaPsiElementImpl(node) with ScTypeDefinition{
  
  override def getTextOffset() = getNameIdentifierScala().getTextRange().getStartOffset()

  def getNameIdentifierScala() = findChildByType(TokenSets.PROPERTY_NAMES)

}