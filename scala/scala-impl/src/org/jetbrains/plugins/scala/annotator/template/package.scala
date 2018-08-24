package org.jetbrains.plugins.scala
package annotator

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.types.ScType

package object template {

  private[template] def superRefs(definition: ScTemplateDefinition) =
    collectSuperRefs(definition)(_.extractClass)

  private[template] def collectSuperRefs[T](definition: ScTemplateDefinition)
                                           (extractor: ScType => Option[T]) =
    for {
      parents <- definition.physicalExtendsBlock.templateParents.toSeq
      typeElement <- parents.typeElements
      scType <- typeElement.`type`().toOption
      extracted <- extractor(scType)
    } yield (typeElement, extracted)
}
