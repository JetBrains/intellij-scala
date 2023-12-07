package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import com.intellij.refactoring.util.VariableData
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.{FakePsiParameter, FakePsiType}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.refactoring.extractMethod.ScalaVariableData._

class ScalaVariableData(val element: ScTypedDefinition,
                        val isInsideOfElements: Boolean,
                        val scType: ScType)
        extends VariableData(createFakeParameter(element, scType)) {

  `type` = new FakePsiType(scType)
  passAsParameter = true
  name = element.name
}

object ScalaVariableData {
  private def createFakeParameter(element: ScTypedDefinition, scType: ScType): FakePsiParameter =
    new FakePsiParameter(element.getManager, ScalaLanguage.INSTANCE, element.name) {
      override def parameter: Parameter = Parameter(scType, isRepeated = false, index = -1)
    }
}
