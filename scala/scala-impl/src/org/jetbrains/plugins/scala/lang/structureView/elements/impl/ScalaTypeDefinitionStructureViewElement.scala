package org.jetbrains.plugins.scala.lang.structureView.elements.impl

import com.intellij.ide.util.treeView.smartTree.TreeElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.structureView.StructureViewUtil

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaTypeDefinitionStructureViewElement(definition: ScTypeDefinition) extends ScalaStructureViewElement(definition, inherited = false) {
  def getPresentableText: String = {
    val typeParameters = definition.typeParametersClause.map(_.typeParameters.map(_.name).mkString("[", ", ", "]"))

    val valueParameters = definition.asOptionOf[ScClass].flatMap {
      _.constructor.map(it => StructureViewUtil.getParametersAsString(it.parameterList))
    }

    val name = Option(definition.nameId).map(_.getText)

    name.getOrElse("") + typeParameters.getOrElse("") + valueParameters.getOrElse("")
  }

  override def getChildren: Array[TreeElement] = {
    val blocks = definition.extendsBlock.templateBody.toSeq
      .flatMap(_.getChildren)
      .filterBy[ScBlockExpr]

    val members = definition.members.flatMap {
      case function: ScFunction => Seq(function)
      case constructor: ScPrimaryConstructor => definition match {
        case c: ScClass if c.isCase =>
          constructor.effectiveFirstParameterSection
        case _ =>
          constructor.valueParameters
      }
      case member: ScVariable => Seq(member)
      case member: ScValue => Seq(member)
      case member: ScTypeAlias => Seq(member)
      case _ => Seq.empty
    }

    val definitions = definition.typeDefinitions

    (blocks ++ members ++ definitions).flatMap(ScalaStructureViewElement(_)).toArray
  }
}
