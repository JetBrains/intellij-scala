package org.jetbrains.plugins.scala.lang.psi.impl.top.defs

/** 
*  @autohr ilyas
*/

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.psi.tree._
import com.intellij.navigation.NavigationItem

import org.jetbrains.plugins.scala.lang.psi.containers._
import org.jetbrains.plugins.scala.lang.psi.impl.top.params._
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
import org.jetbrains.plugins.scala.lang.psi.impl.top.templateStatements._

/**
*  Main class, that describes behaviour of scala templates, such ass class, object and trait
*  @see ScJavaClass
*/
abstract class ScTmplDef(node: ASTNode) extends ScalaPsiElementImpl (node) with TemplateIndent with NavigationItem with PsiNamedElement {

  override def toString: String = "template definition"

  def nameNode = {
    def isName = (elementType: IElementType) => (elementType == ScalaTokenTypes.tIDENTIFIER)
    childSatisfyPredicateForElementType(isName)
  }

  /**
  *  Returns definitions of inner templates
  *
  */
  def getTmplDefs: Iterable[ScTmplDef] = {
    import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTemplateBody
    val template = getTemplate
    if (template != null) {
      val body = template.getTemplateBody
      if (body != null) {
        val children = body.childrenOfType[ScTmplDef](ScalaElementTypes.TMPL_DEF_BIT_SET)
        return (children :\ (Nil: List[ScTmplDef]))((y: ScTmplDef, x: List[ScTmplDef]) =>
          y.asInstanceOf[ScTmplDef] :: y.asInstanceOf[ScTmplDef].getTmplDefs.toList ::: x)
      }
    }
    return Nil: List[ScTmplDef]
  }

  override def getName = if (nameNode != null) {
    nameNode.getText
  } else {
    ""
  }

/*
  override def setName(name: String) = {
    val newNode = ScalaPsiElementFactory.createIdentifierFromText(name, getManager)
    getNode.replaceChild(nameNode.getNode, newNode)
    this
  }
*/

  override def getTextOffset = nameNode.getTextRange.getStartOffset

  def isTypeDef: boolean = { this.isInstanceOf[ScTypeDefinition] }

  /**
  * Returns qualified name of current template definition
  *
  */
  def getQualifiedName: String = {
    def append(s1: String, s2: String) = {if (s1 == "")  s2 else s1 + "." + s2}
    def iAmInner(e: PsiElement): String = {
      val parent = e.getParent
      parent match {
        case pack: ScPackaging => append(iAmInner(parent), pack.asInstanceOf[ScPackaging].getFullPackageName)
        case tmplBody: ScTemplateBody => {
          append(iAmInner(tmplBody.getParent.getParent),
              tmplBody.getParent.asInstanceOf[ScTmplDef].getName)
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

  def getTemplate: ScTopDefTemplate = getChild(ScalaElementTypes.TOP_DEF_TEMPLATE).asInstanceOf[ScTopDefTemplate]

  def getFieldOrMethodWithoutArguments(name: String): ScTemplateStatement = {
    val statements = getTemplateStatements.toList
    if (getTemplateStatements != null) {
      for (val statement <- getTemplateStatements){
        // TODO Implement other cases
        if (statement.isInstanceOf[ScFunction] &&
        name.equals(statement.asInstanceOf[ScFunction].getFunctionName)) {
          return statement
        }
      }
    }
    null
  }

  [Nullable]
  def getTemplateStatements: Iterable[ScTemplateStatement] = {
    import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTemplateBody

    val body = getChild(ScalaElementTypes.TEMPLATE_BODY).asInstanceOf[ScTemplateBody]
    if (body != null) return body.getTemplateStatements
    else Nil: List[ScTemplateStatement]
  }
}
