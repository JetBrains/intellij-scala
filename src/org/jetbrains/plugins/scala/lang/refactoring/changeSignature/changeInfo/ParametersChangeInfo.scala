package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature.changeInfo

import com.intellij.psi.JavaPsiFacade
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.ScalaParameterInfo
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * Nikolay.Tropin
 * 2014-08-29
 */
private[changeInfo] trait ParametersChangeInfo {
  this: ScalaChangeInfo =>

  private val oldParameters = function.getParameterList.params.toArray
  private val oldParameterNames: Array[String] = oldParameters.map(_.getName)
  private val oldParameterTypes: Array[String] = {
    val factory = JavaPsiFacade.getElementFactory(function.getProject)
    oldParameters.map(p => factory.createTypeElement(p.getType).getText)
  }

  val toRemoveParm: Array[Boolean] = oldParameters.zipWithIndex.map {
    case (p, i) => !newParameters.exists(_.oldIndex == i)
  }

  val isParameterSetOrOrderChanged: Boolean = {
    oldParameters.length != newParameters.length ||
            newParameters.zipWithIndex.exists {case (p, i) => p.oldIndex != i}
  }

  val isParameterNamesChanged: Boolean = newParameters.zipWithIndex.exists {
    case (p, i) => p.oldIndex == i && p.getName != getOldParameterNames(i)
  }

  val isParameterTypesChanged: Boolean = newParameters.zipWithIndex.exists {
    case (p, i) =>  (p.oldIndex == i) &&
      (p.getTypeText != getOldParameterTypes(i) ||
              p.isRepeatedParameter != oldParameters(i).isRepeatedParameter ||
              p.isByName != oldParameters(i).isCallByNameParameter)
  }

  val wasVararg: Boolean = false
  val isObtainsVarags: Boolean = false
  val isRetainsVarargs: Boolean = false
  val isArrayToVarargs: Boolean = false

  def newParameters: Seq[ScalaParameterInfo] = newParams.flatten

  def getOldParameterNames: Array[String] = oldParameterNames

  def getOldParameterTypes: Array[String] = oldParameterTypes

  def defaultParameterForJava(p: ScalaParameterInfo, idx: Int): String = {
    if (this.isAddDefaultArgs) {
      if (this.function.isConstructor) {
        this.function.containingClass match {
          case c: ScClass =>
            val className = ScalaNamesUtil.toJavaName(c.name)
            s"$className.$$lessinit$$greater$$default$$${idx + 1}()"
          case _ => p.defaultValue
        }
      } 
      else s"${this.getNewName}$$default$$${idx + 1}()"
    }
    else p.defaultValue
  }
}
