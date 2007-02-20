package org.jetbrains.plugins.scala.lang.resolve

/** 
* @author Ilya Sergey
*
*/

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.tree.IElementType;

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.Template
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScParamClauses
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.formatting.patterns.indent._
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements.ScTemplateStatement
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTopDefTemplate
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.resolve.processors._
import org.jetbrains.plugins.scala.lang.psi.impl.top._
import org.jetbrains.plugins.scala.lang.psi._
import org.jetbrains.plugins.scala.lang.psi.impl.top.defs._

object ScalaResolveUtil {

  /**
  * Main function that walks up by PSI Tree an finds declaration using processor
  *
  */
  def treeWalkUp(processor: PsiScopeProcessor,
          elt: PsiElement,
          lastParent: PsiElement,
          place: PsiElement): PsiElement = {
    if (processor.isInstanceOf[ScalaPsiScopeProcessor]) {

      if (elt == null) return null
      var cur = elt
      while (cur.processDeclarations(processor,
              PsiSubstitutor.EMPTY,
              if (cur == elt) lastParent else null,
              place)) {
        if (cur.isInstanceOf[PsiFile]) return null
        cur = cur.getParent
      }

      val result = processor.asInstanceOf[ScalaPsiScopeProcessor].getResult
      result

    } else null


  }

  /**
  * Returns qualified name prefix of current element
  *
  */
  def getQualifiedPrefix(elt: PsiElement): String = {

    var result = ""
    var parent = if (! elt.isInstanceOf[ScalaFile]) elt.getParent else elt
    while (! parent.isInstanceOf[ScalaFile] && parent!= null) {
      parent match {
        case pack: ScPackaging => {
          result = pack.asInstanceOf[ScPackaging].getFullPackageName + "." + result
        }
        case tmplBody: ScTemplateBody => {
          result = tmplBody.getParent.getParent.asInstanceOf[ScTmplDef].getName + "." + result
        }
        case _ => {}
      }
      parent = parent.getParent
    }

    if (parent.isInstanceOf[ScalaFile]) {
      val packageStatement = parent.asInstanceOf[ScalaFile].getChild(ScalaElementTypes.PACKAGE_STMT).asInstanceOf[ScPackageStatement]
      if (packageStatement == null) "" else {
        val packageName = packageStatement.getFullPackageName
        if (packageName != null) result = packageName + "." + result
      }
    }
    result
  }


}