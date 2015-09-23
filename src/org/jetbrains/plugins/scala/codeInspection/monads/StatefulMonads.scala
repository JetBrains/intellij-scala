package org.jetbrains.plugins.scala.codeInspection.monads

import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.codeInspection.InspectionsUtil
import org.jetbrains.plugins.scala.lang.psi.types.ScType

import scala.concurrent.Future
import scala.util.Try

/**
 * @author Sergey Tolmachev (tolsi.ru@gmail.com)
 * @since 29.09.15
 */
object StatefulMonads {
  private[monads] final lazy val StatefulMonadsTypes: Set[Class[_]] = Set(classOf[Future[_]], classOf[Try[_]])
  private[monads] final lazy val StatefulMonadsTypesNames = StatefulMonadsTypes.map(_.getCanonicalName)

  private[monads] def isStatefulMonadType(t: ScType, projection: Project): Boolean = {
    StatefulMonadsTypesNames.exists(typeName => InspectionsUtil.conformsToTypeFromClass(t, typeName, projection))
  }
}