package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature.changeInfo

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScClass
import org.jetbrains.plugins.scala.lang.refactoring.changeSignature.ScalaParameterInfo
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

/**
 * Nikolay.Tropin
 * 2014-08-29
 */
private[changeInfo] trait ParametersChangeInfo {
  this: ScalaChangeInfo =>

  private val oldParameters = ScalaParameterInfo.allForMethod(function)
  private val oldParametersArray = oldParameters.flatten.toArray
  private val oldParameterNames: Array[String] = oldParametersArray.map(_.name)
  private val oldParameterTypes: Array[String] = oldParametersArray.map(_.getTypeText)

  val toRemoveParm: Array[Boolean] = oldParametersArray.zipWithIndex.map {
    case (p, i) => !newParameters.exists(_.oldIndex == i)
  }

  val isParameterSetOrOrderChanged: Boolean = {
    oldParameters.map(_.length) != newParams.map(_.length) ||
            newParameters.zipWithIndex.exists {case (p, i) => p.oldIndex != i}
  }

  val isParameterNamesChanged: Boolean = newParameters.zipWithIndex.exists {
    case (p, i) => p.oldIndex == i && p.getName != getOldParameterNames(i)
  }

  val isParameterTypesChanged: Boolean = newParameters.zipWithIndex.exists {
    case (p, i) =>  (p.oldIndex == i) &&
      (p.getTypeText != getOldParameterTypes(i) ||
              p.isRepeatedParameter != oldParametersArray(i).isRepeatedParameter ||
              p.isByName != oldParametersArray(i).isByName)
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
