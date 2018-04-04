package org.jetbrains.plugins.scala.lang.structureView.element

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{ObjectExt, _}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.structureView.StructureViewUtil
import org.jetbrains.plugins.scala.lang.structureView.element.TypeDefinition.childrenOf

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/
class TypeDefinition(definition: ScTypeDefinition) extends AbstractTreeElement(definition) {
  override def getPresentableText: String = {
    val typeParameters = definition.typeParametersClause.map(_.typeParameters.map(_.name).mkString("[", ", ", "]"))

    val valueParameters = definition.asOptionOf[ScClass].flatMap {
      _.constructor.map(it => StructureViewUtil.getParametersAsString(it.parameterList))
    }

    val name = Option(definition.nameId).map(_.getText)

    name.getOrElse("") + typeParameters.getOrElse("") + valueParameters.getOrElse("")
  }

  override def children: Seq[PsiElement] = childrenOf(definition)

  override def isAlwaysLeaf: Boolean = false

  override def isAlwaysShowsPlus: Boolean = true
}

object TypeDefinition {
   def childrenOf(definition: ScTypeDefinition): Seq[PsiElement] = {
     val blocks = definition.extendsBlock.templateBody.toSeq
       .flatMap(_.getChildren)
       .filterBy[ScBlockExpr]

     val members = definition.members.flatMap {
       case constructor: ScPrimaryConstructor => definition match {
         case c: ScClass if c.isCase => constructor.effectiveFirstParameterSection
         case _ => constructor.valueParameters
       }
       case element @ (_: ScFunction | _: ScVariable | _: ScValue | _: ScTypeAlias) => Seq(element)
       case _ => Seq.empty
     }

     val definitions = definition.typeDefinitions

     blocks ++ members ++ definitions
   }
}
