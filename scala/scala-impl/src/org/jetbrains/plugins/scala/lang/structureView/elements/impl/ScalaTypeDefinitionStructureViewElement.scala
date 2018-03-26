package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.structureView.StructureViewUtil
import org.jetbrains.plugins.scala.lang.structureView.elements.ScalaStructureViewElement
import org.jetbrains.plugins.scala.lang.structureView.elements.impl.ScalaTypeDefinitionStructureViewElement.Presentation

import scala.collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaTypeDefinitionStructureViewElement(definition: ScTypeDefinition) extends ScalaStructureViewElement(definition, inherited = false) {
  override def getPresentation: ItemPresentation = new Presentation(definition)

  override def getChildren: Array[TreeElement] = {
    val children = new ArrayBuffer[TreeElement]
    for (body <- definition.extendsBlock.templateBody; child <- body.getChildren if child.isInstanceOf[ScBlockExpr])
      children += new ScalaBlockStructureViewElement(child.asInstanceOf[ScBlockExpr])
    val members = definition.members
    for (member <- members) {
      member match {
        case func: ScFunction =>
          children ++= ScalaFunctionStructureViewElement(func, false)
        case constr: ScPrimaryConstructor =>
          definition match {
            case c: ScClass if c.isCase =>
              constr.effectiveFirstParameterSection.foreach {
                children += new ScalaValOrVarParameterStructureViewElement(_, false)
              }
            case _ =>
              constr.valueParameters.foreach {
                children += new ScalaValOrVarParameterStructureViewElement(_, false)
              }
          }
        case member: ScVariable =>
          for (f <- member.declaredElements)
            children ++= ScalaVariableStructureViewElement(f, false)
        case member: ScValue =>
          for (f <- member.declaredElements)
            children ++= ScalaValueStructureViewElement(f, false)
        case member: ScTypeAlias =>
          children += new ScalaTypeAliasStructureViewElement(member, false)
        case _ =>
      }
    }
    for (typeDef <- definition.typeDefinitions)
      children += new ScalaTypeDefinitionStructureViewElement(typeDef)
    children.toArray
  }
}

private object ScalaTypeDefinitionStructureViewElement {
  class Presentation(definition: ScTypeDefinition) extends ScalaItemPresentation(definition) {
    def getPresentableText: String = {
      val typeParameters = definition.typeParametersClause.map(_.typeParameters.map(_.name).mkString("[", ", ", "]"))

      val valueParameters = definition.asOptionOf[ScClass].flatMap {
        _.constructor.map(it => StructureViewUtil.getParametersAsString(it.parameterList))
      }

      val name = Option(definition.nameId).map(_.getText)

      name.getOrElse("") + typeParameters.getOrElse("") + valueParameters.getOrElse("")
    }
  }
}