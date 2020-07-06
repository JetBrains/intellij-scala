package org.jetbrains.plugins.scala
package lang
package psi
package implicits

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt}
import org.jetbrains.plugins.scala.lang.completion.ScalaCompletionUtil.findInheritorObjectsForOwner
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.MixinNodes
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ImplicitConversionIndex
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.util.CommonQualifiedNames.AnyFqn

@Service
final class ImplicitConversionCache(implicit val project: Project) {

  def getPossibleConversions(expr: ScExpression): Map[GlobalImplicitConversion, ScType] = {
    expr.getTypeWithoutImplicits().toOption match {
      case None               => Map.empty
      case Some(originalType) =>
        val withSuperClasses = originalType.widen.extractClass match {
          case Some(clazz) => MixinNodes.allSuperClasses(clazz).map(_.qualifiedName) + clazz.qualifiedName + AnyFqn
          case _ => Set.empty
        }

        (for {
          qName            <- withSuperClasses
          function         <- ImplicitConversionIndex.conversionCandidatesForFqn(qName, expr.resolveScope)

          if ImplicitConversionProcessor.applicable(function, expr)

          containingObject <- findInheritorObjectsForOwner(function)
          globalConversion =  GlobalImplicitConversion(containingObject, function)
          data             <- ImplicitConversionData(function, globalConversion.substitutor)
          resultType       <- data.resultType(originalType, expr).map((globalConversion, _))
        } yield resultType)
          .toMap
    }
  }

}

object ImplicitConversionCache {
  def apply(project: Project): ImplicitConversionCache = project.getService(classOf[ImplicitConversionCache])
}
