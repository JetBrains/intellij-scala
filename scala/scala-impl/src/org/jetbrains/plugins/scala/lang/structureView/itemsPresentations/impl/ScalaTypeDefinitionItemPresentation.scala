package org.jetbrains.plugins.scala
package lang
package structureView
package itemsPresentations
package impl

import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._

/**
* @author Alexander Podkhalyuzin
* Date: 04.05.2008
*/

class ScalaTypeDefinitionItemPresentation(definition: ScTypeDefinition) extends ScalaItemPresentation(definition) {
  def getPresentableText: String = {
    val typeParameters = definition.typeParametersClause.map(_.typeParameters.map(_.name).mkString("[", ", ", "]"))

    val valueParameters = definition.asOptionOf[ScClass].flatMap {
      _.constructor.map(it => StructureViewUtil.getParametersAsString(it.parameterList))
    }

    val name = Option(definition.nameId).map(_.getText)

    name.getOrElse("") + typeParameters.getOrElse("") + valueParameters.getOrElse("")
  }
}