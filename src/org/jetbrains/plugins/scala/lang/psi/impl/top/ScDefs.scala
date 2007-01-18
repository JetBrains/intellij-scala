package org.jetbrains.plugins.scala.lang.psi.impl.top.defs {

import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.Template
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScParamClauses
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes


/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2006
 * Time: 15:08:18
 */
/*************** definitions **************/
  abstract class ScTmplDef( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template definition"

    def getName : String

    def isTypeDef : boolean = { this.isInstanceOf[ScTypeDef] }

    def getQualifiedName : String = {
      def inner (e : PsiElement, acc : String) : String = {
        val parent = e.getParent
        def append (s1 : String, s2 : String) = {if (s1 == "")  s2 else s1 + "." + s2}
        append (
          parent match {
            case pack : ScPackaging => append (inner(parent, acc), pack.asInstanceOf[ScPackaging].getFullPackageName)
            case tmplDef : ScTmplDef => append (inner(parent, acc), tmplDef.asInstanceOf[ScTmplDef].getName)
            case f : ScalaFile => {
              val packageStatement : ScPackageStatement = f.getChild[ScPackageStatement]
              if (packageStatement == null) "" else {
                val packageName = packageStatement.getFullPackageName
                if (packageName == null) "" else packageName
              }
            }
          },
          getName)
      }

      inner (this, "")
    }

    import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTopDefTemplate
    def getTemplate = getChild[ScTopDefTemplate]

    def getTemplates = getChildren
  }

  trait ScTypeDef extends ScTmplDef {
    def getTypeParameterClause : ScTypeParamClause = {
      getChild[ScTypeParamClause]
    }

    override def getName : String = {
      def isName = (elementType : IElementType) => (elementType == ScalaTokenTypes.tIDENTIFIER)
      childSatisfyPredicateForElementType(isName).getText()
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
    override def getName : String = {
      def isName = (elementType : IElementType) => (elementType == ScalaTokenTypes.tIDENTIFIER)

      childSatisfyPredicateForElementType(isName).getText()
    }

  }

  case class ScTraitDefinition( node : ASTNode ) extends ScTypeDef (node) {
    override def toString: String = super.toString + ": " + "trait"
  }
}