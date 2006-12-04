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
  abstract class TmplDef( node : ASTNode ) extends ScalaPsiElementImpl ( node ) {
    override def toString: String = "template definition"

    def getTemplateName : String
    def getTemplate : Template

    def isTypeDef : boolean = { this.isInstanceOf[TypeDef] }
    def isInstanceDef : boolean = { this.isInstanceOf[InstanceDef] }

    //todo: getQualifiedName
    //todo:if there is no package
    def getQualifiedName : String = {
      var packageNode = getParent

      if (packageNode!= null && packageNode.getParent!= null && packageNode.getParent.getFirstChild.isInstanceOf[ScPackageStatement]) return packageNode.getParent.getFirstChild.asInstanceOf[ScPackaging].getFullPackageName + "." + this.getName

      var fullPackageName = "";

      while (packageNode!= null && packageNode.getParent!= null && packageNode.getParent.isInstanceOf[ScPackaging]) {
        fullPackageName = packageNode.getParent.asInstanceOf[ScPackaging].getFullPackageName + "." + fullPackageName
        packageNode = packageNode.getParent.getParent
      }

      return fullPackageName + "." + this.getName
    }
  }

  trait TypeDef extends TmplDef (node) {
    def getTypeParameterClause : ScTypeParamClause = {
      getChild[ScTypeParamClause]
    }

    import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScRequiresBlock

    def getRequiresBlock = getChild[ScRequiresBlock]
    def hasRequiresBlock = hasChild[ScRequiresBlock]
  }

  trait InstanceDef extends TmplDef (node){
    def isCase : boolean = getFirstChild.getText == "case"

    //[case] class A
    override def getTemplateName : String = {val children = getChildren; if (isCase) children(2).getText else children(1).getText}

    import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScClassTemplate
    override def getTemplate = getChild[ScClassTemplate]
  }

  case class ScClassDefinition( node : ASTNode ) extends InstanceDef (node) with TypeDef {
    override def toString: String = super.toString + ": " + "class"
  }

  case class ScObjectDefinition( node : ASTNode ) extends InstanceDef ( node ) {
    override def toString: String = super.toString + ": " + "object"
  }

  case class ScTraitDefinition( node : ASTNode ) extends TypeDef (node){
    override def toString: String = super.toString + ": " + "trait"

    override def getTemplateName : String = {val children = getChildren; children(1).getText}

    import org.jetbrains.plugins.scala.lang.psi.impl.top.templates.ScTraitTemplate
    override def getTemplate = getChild[ScTraitTemplate]
  }
}