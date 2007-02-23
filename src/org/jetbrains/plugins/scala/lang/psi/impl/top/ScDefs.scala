package org.jetbrains.plugins.scala.lang.psi.impl.top.defs

import com.intellij.lang.ASTNode
import com.intellij.psi.{PsiElement, PsiNamedElement}
import com.intellij.psi.tree.IElementType
import com.intellij.navigation.NavigationItem

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

  override def getName = nameNode.getText

  override def setName(name : String) = {
    val newNode = ScalaPsiElementFactory.createIdentifierFromText(name, getManager)
    getNode.replaceChild(nameNode.getNode, newNode)
    this
  }

  override def getTextOffset = nameNode.getTextRange.getStartOffset

  def isTypeDef: boolean = { this.isInstanceOf[ScTypeDef] }

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
                  tmplBody.getParent.getParent.asInstanceOf[ScTmplDef].getName)
        }
        case f: ScalaFile => {
          val packageStatement = f.getChild(ScalaElementTypes.PACKAGE_STMT).asInstanceOf[ScPackageStatement]
          if (packageStatement == null) "" else {
            val packageName = packageStatement.getFullPackageName
            if (packageName == null) "" else packageName
          }
        }
        case _ => getName
      }
    }
    append(iAmInner(this), getName)
  }



  def getTemplate: ScTopDefTemplate = getChild(ScalaElementTypes.TOP_DEF_TEMPLATE).asInstanceOf[ScTopDefTemplate]

  [Nullable]
  def getTemplateStatements: Iterable[ScTemplateStatement] = {
    import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTemplateBody

    val template = getTemplate
    if (template != null) {
      var body = template.getTemplateBody
      if (body != null) return body.childrenOfType[ScTemplateStatement](ScalaElementTypes.TMPL_STMT_BIT_SET)
    }
    null
  }
}

/**********************************************************************************************************************/
/******************************************* Concrete templates *******************************************************/
/**********************************************************************************************************************/

/*
*   Class definition implementation
*/
case class ScClassDefinition(node: ASTNode) extends ScTypeDef (node){
  override def toString: String = super.toString + ": " + "class"
                   
  override def getIcon(flags: Int) = Icons.CLASS
}

/*
*   Object definition implementation
*/
case class ScObjectDefinition(node: ASTNode) extends ScTmplDef (node){
  override def toString: String = super.toString + ": " + "object"
  override def getIcon(flags: Int) = Icons.OBJECT
}

/*
*   Trait definition implementation
*/
trait ScTypeDef extends ScTmplDef  with IfElseIndent{
  def getTypeParameterClause: ScTypeParamClause = {
    getChild(ScalaElementTypes.TYPE_PARAM_CLAUSE).asInstanceOf[ScTypeParamClause]
  }
}

/*
*   Trait definition implementation
*/
case class ScTraitDefinition(node: ASTNode) extends ScTypeDef (node) {
  override def toString: String = super.toString + ": " + "trait"

  override def getIcon(flags: Int) = Icons.TRAIT
}
