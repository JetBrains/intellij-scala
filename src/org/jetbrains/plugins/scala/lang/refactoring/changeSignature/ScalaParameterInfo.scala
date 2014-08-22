package org.jetbrains.plugins.scala
package lang.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterInfo
import org.jetbrains.plugins.scala.lang.psi.types.ScType

/**
 * Nikolay.Tropin
 * 2014-08-10
 */
class ScalaParameterInfo(var name: String, tpe: ScType, oldIdx: Int, var useAnyVar: Boolean, defValue: String = "")
        extends ParameterInfo {

  override def getName: String = name

  override def setName(name: String): Unit = {
    this.name = name
  }

  override def getTypeText: String = tpe.presentableText

  override def getOldIndex: Int = oldIdx

  override def isUseAnySingleVariable: Boolean = useAnyVar

  override def setUseAnySingleVariable(b: Boolean): Unit = {
    useAnyVar = b
  }

  override def getDefaultValue: String = defValue
}
