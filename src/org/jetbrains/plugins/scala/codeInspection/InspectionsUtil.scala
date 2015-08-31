package org.jetbrains.plugins.scala
package codeInspection

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.types._

/**
 * User: Alexander Podkhalyuzin
 * Date: 02.06.2009
 */

object InspectionsUtil {
  val ERROR_HANDLING = "(Scala) Error Handling"

  val SCALA = "Scala: General"

  val SCALADOC = "Scala: Scaladoc"

  val MethodSignature = "Scala: Method signature"

  def isExpressionOfType(className: String, expr: ScExpression): Boolean = {
    def parameterizedTypeFromClassName(fqn: String, project: Project): Option[ScType] = {
      val clazz = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project))
      Option(clazz).map { c =>
        val designatorType = ScDesignatorType(c)
        val undefines = c.getTypeParameters.toSeq.map(ptp =>
          ScUndefinedType(new ScTypeParameterType(ptp, ScSubstitutor.empty))
        )
        ScParameterizedType(designatorType, undefines)
      }
    }

    val scType = parameterizedTypeFromClassName(className, expr.getProject)
    val exprType = expr.getType().getOrAny
    if (exprType == StdType.NULL || exprType == StdType.NOTHING) false
    else scType.exists(exprType.conforms(_))
  }
}