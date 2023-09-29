package org.jetbrains.plugins.scala.structureView.element

import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.NlsString
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, ObjectExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlockExpr
import org.jetbrains.plugins.scala.lang.psi.api.statements.*
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.*
import org.jetbrains.plugins.scala.structureView.element.TypeDefinition.childrenOf
import org.jetbrains.plugins.scala.util.ScalaElementPresentationUtil

import javax.swing.Icon

class TypeDefinition(definition: ScTypeDefinition) extends AbstractTreeElementDelegatingChildrenToPsi(definition) {

  override def getIcon(open: Boolean): Icon = {
    val layerFlags = ScalaElementPresentationUtil.getRunnableObjectFlags(definition)
    definition.getIconWithExtraLayerFlags(Iconable.ICON_FLAG_VISIBILITY, layerFlags)
  }

  override def getPresentableText: String = {
    val name = Option(definition.nameId).map(_.getText)

    val typeParameters = definition.typeParametersClause.map(_.typeParameters.map(_.name).mkString("[", ", ", "]"))

    val valueParameters = definition.asOptionOf[ScConstructorOwner].flatMap {
      _.constructor.map { constructor =>
        FromStubsParameterRenderer.renderClauses(constructor.parameterList)
      }
    }

    NlsString.force(name.getOrElse("") + typeParameters.getOrElse("") + valueParameters.getOrElse(""))
  }

  override def children: Seq[PsiElement] = childrenOf(definition)

  override def isAlwaysLeaf: Boolean = false

  override def isAlwaysShowsPlus: Boolean = true
}

object TypeDefinition {
   private[structureView] def childrenOf(definition: ScTemplateDefinition): Seq[PsiElement] = {
     val blocks = definition.extendsBlock.templateBody.toSeq
       .flatMap(_.getChildren)
       .filterByType[ScBlockExpr]

     val members = definition.members.flatMap {
       case constructor: ScPrimaryConstructor => definition match {
         case c: ScClass if c.isCase => constructor.effectiveFirstParameterSection
         case _ => constructor.valueParameters
       }
       case element @ (_: ScFunction | _: ScVariable | _: ScValue | _: ScTypeAlias | _: ScExtension) => Seq(element)
       case _ => Seq.empty
     }

     val enumCases = definition match {
       case ec: ScEnum => ec.cases
       case _               => Nil
     }

     val typeDefinitions = definition.typeDefinitions

     enumCases ++ blocks ++ members ++ typeDefinitions
   }
}
