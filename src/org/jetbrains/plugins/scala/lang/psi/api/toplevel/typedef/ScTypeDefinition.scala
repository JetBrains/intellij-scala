package org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem

import org.jetbrains.plugins.scala.lang.parser._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.lexer._
import psi.api.toplevel.packaging._
import psi.api.toplevel.templates._

/** 
* @autor Alexander Podkhalyuzin
*/

trait ScTypeDefinition extends ScalaPsiElement with NavigationItem with PsiNamedElement with ScTypeDefinitionOwner {

  def getQualifiedName: String = {
    def append(s1: String, s2: String) = {if (s1 == "")  s2 else s1 + "." + s2}
    def iAmInner(e: PsiElement): String = {
      val parent = e.getParent
      parent match {
        case pack: ScPackaging => append(iAmInner(parent), pack.getFullPackageName)
        case tmplBody: ScTemplateBody => {
          append(iAmInner(tmplBody.getParent.getParent),
              tmplBody.getParent.asInstanceOf[ScTypeDefinition].getName)
        }
        case f: ScalaFile => {
          val packageStatement = f.getChild(ScalaElementTypes.PACKAGE_STMT).asInstanceOf[ScPackageStatement]
          if (packageStatement == null) "" else {
            val packageName = packageStatement.getFullPackageName
            if (packageName == null) "" else packageName
          }
        }
        case null => ""
        case x if x.getParent != null => iAmInner(x)
        case _ => ""
      }
    }
    append(iAmInner(this), getName)
  }

  override def getName = if (nameNode != null) nameNode.getText else ""

  def nameNode = {
    def isName = (elementType: IElementType) => (elementType == ScalaTokenTypes.tIDENTIFIER)
    childSatisfyPredicateForElementType(isName)
  }




}