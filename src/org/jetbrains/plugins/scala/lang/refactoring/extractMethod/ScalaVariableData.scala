package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import com.intellij.psi.{PsiAnnotation, PsiPrimitiveType}
import com.intellij.refactoring.util.VariableData
import org.jetbrains.plugins.scala.ScalaFileType
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, ScTypeExt}

/**
 * Nikolay.Tropin
 * 2014-04-10
 */
class ScalaVariableData(val element: ScTypedDefinition,
                        val isInsideOfElements: Boolean,
                        val scType: ScType) 
        extends {
          private val parameter = new Parameter("", None, scType, false, false, false, -1)
          private val fakeParam = new FakePsiParameter(element.getManager, ScalaFileType.SCALA_LANGUAGE, parameter, element.name)

        } with VariableData(fakeParam, new FakePsiType(scType)) {

  passAsParameter = true
  name = fakeParam.getName
}

private class FakePsiType(val tp: ScType) extends PsiPrimitiveType("fakeForScala", PsiAnnotation.EMPTY_ARRAY) {
  override def getPresentableText = tp.presentableText
}
