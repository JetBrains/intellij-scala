package org.jetbrains.plugins.scala.lang.psi.impl.top.defs {

import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement;
import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.Template
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScTypeParamClause
import org.jetbrains.plugins.scala.lang.psi.impl.top.params.ScParamClauses


/**
 * User: Dmitry.Krasilschikov
 * Date: 13.11.2006
 * Time: 15:08:18
 */
/*************** definitions **************/
  abstract class ScTmplDef( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template definition"

    def getTemplateName : String

    def isTypeDef : boolean = { this.isInstanceOf[ScTypeDef] }
  /*  def isInstanceDef : boolean = { this.isInstanceOf[ScInstanceDef] } */

    //todo: getQualifiedName
    //todo:if there is no package
    def getQualifiedName : String = {
      var packageNode = getParent

      if (packageNode != null && packageNode.getParent != null && packageNode.getParent.getFirstChild.isInstanceOf[ScPackageStatement]) return packageNode.getParent.getFirstChild.asInstanceOf[ScPackaging].getFullPackageName + "." + this.getName

      var fullPackageName = "";
      while (packageNode!= null && packageNode.getParent!= null && packageNode.getParent.isInstanceOf[ScPackaging]) {
        fullPackageName = packageNode.getParent.asInstanceOf[ScPackaging].getFullPackageName + "." + fullPackageName
        packageNode = packageNode.getParent.getParent
      }

      return fullPackageName + "." + this.getName
    }

    import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTopDefTemplate
    def getTemplate = getChild[ScTopDefTemplate]

    def getTemplates = getChildren
  }

  trait ScTypeDef extends ScTmplDef {
    def getTypeParameterClause : ScTypeParamClause = {
      getChild[ScTypeParamClause]
    }

    import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScRequiresBlock

    def getRequiresBlock = getChild[ScRequiresBlock]
    def hasRequiresBlock = hasChild[ScRequiresBlock]

    override def getTemplateName : String = {val children = getChildren; children(1).getText}
  }

  /* trait ScInstanceDef extends ScTmplDef {
    def isCase : boolean = getFirstChild.getText == "case"

    //[case] class A
    override def getTemplateName : String = {val children = getChildren; if (isCase) children(2).getText else children(1).getText}


  }  */

  case class ScClassDefinition( node : ASTNode ) extends /*ScInstanceDef (node) with */ScTypeDef (node){
    override def toString: String = super.toString + ": " + "class"
  }

  case class ScObjectDefinition( node : ASTNode ) /*extends ScInstanceDef ( node ) */ extends ScalaPsiElementImpl ( node ){
    override def toString: String = super.toString + ": " + "object"
  }

  case class ScTraitDefinition( node : ASTNode ) extends ScTypeDef (node) {
    override def toString: String = super.toString + ": " + "trait"

//    override def getTemplateName : String = {val children = getChildren; children(1).getText}

//    import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTraitTemplate
//    override def getTemplate = getChild[ScTopDefTemplate]
  }
}