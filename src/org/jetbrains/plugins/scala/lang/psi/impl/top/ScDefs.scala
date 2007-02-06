package org.jetbrains.plugins.scala.lang.psi.impl.top.defs {

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement;
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


/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2006
 * Time: 15:08:18
 */
/*************** definitions **************/
  abstract class ScTmplDef( node : ASTNode ) extends ScalaPsiElementImpl ( node ) with TemplateIndent{
    override def toString: String = "template definition"

    def nameNode = {
      def isName = (elementType : IElementType) => (elementType == ScalaTokenTypes.tIDENTIFIER)

      childSatisfyPredicateForElementType(isName)
    }

    def getTmplDefs : Iterable[ScTmplDef] = {

      import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTemplateBody

      val template = getTemplate
      var body : ScTemplateBody = null
      if ( template != null) {
        body = template.getTemplateBody
        if (body != null ) return body.childrenOfType[ScTmplDef] (ScalaElementTypes.TMPL_DEF_BIT_SET)
      }
      null
    }

    //todo: 
    override def getName = nameNode.getText

    override def getTextOffset = nameNode.getTextRange.getStartOffset

    def isTypeDef : boolean = { this.isInstanceOf[ScTypeDef] }

    def getQualifiedName : String = {
      def append (s1 : String, s2 : String) = {if (s1 == "")  s2 else s1 + "." + s2}

      def inner (e : PsiElement) : String = {
        val parent = e.getParent
        parent match {
          case pack: ScPackaging => append(inner(parent), pack.asInstanceOf[ScPackaging].getFullPackageName)
          case tmplDef: ScTmplDef => tmplDef.asInstanceOf[ScTmplDef].getQualifiedName
          case f: ScalaFile => {
            val packageStatement = f.getChild(ScalaElementTypes.PACKAGE_STMT).asInstanceOf[ScPackageStatement]
            if (packageStatement == null) "" else {
              val packageName = packageStatement.getFullPackageName
              if (packageName == null) "" else packageName
            }
          }
        }
      }

      append (inner (this), getName)
    }

    import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTopDefTemplate
    def getTemplate : ScTopDefTemplate = getChild(ScalaElementTypes.TOP_DEF_TEMPLATE).asInstanceOf[ScTopDefTemplate]

    [Nullable]
    def getTemplateStatements : Iterable[ScTemplateStatement] = {
      import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTemplateBody

      val template = getTemplate
      var body : ScTemplateBody = null
      if ( template != null) {
        body = template.getTemplateBody
        if (body != null ) return body.childrenOfType[ScTemplateStatement](ScalaElementTypes.TMPL_STMT_BIT_SET)
      }
      null
    }
  }

  trait ScTypeDef extends ScTmplDef  with IfElseIndent{
    def getTypeParameterClause : ScTypeParamClause = {
      getChild(ScalaElementTypes.TYPE_PARAM_CLAUSE).asInstanceOf[ScTypeParamClause]
    }

  }

  /*
  *   Class definition implementation
  */
  case class ScClassDefinition( node : ASTNode ) extends ScTypeDef (node){
    override def toString: String = super.toString + ": " + "class"

  }

  case class ScObjectDefinition( node : ASTNode ) extends ScTmplDef ( node ){
    override def toString: String = super.toString + ": " + "object"

    //todo
  }

  case class ScTraitDefinition( node : ASTNode ) extends ScTypeDef (node) {
    override def toString: String = super.toString + ": " + "trait"
  }
}