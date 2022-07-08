package org.jetbrains.plugins.scala.lang.refactoring.extractMethod

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.{PsiAnnotation, PsiType, PsiTypeVisitor}
import com.intellij.refactoring.util.VariableData
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.fake.FakePsiParameter
import org.jetbrains.plugins.scala.lang.psi.types.nonvalue.Parameter
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.lang.refactoring.ScTypePresentationExt
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

  private class FakePsiType(val tp: ScType) extends PsiType(PsiAnnotation.EMPTY_ARRAY) {

    override def getPresentableText(boolean: Boolean): String = getPresentableText

    override def getPresentableText: String = tp.codeText(TypePresentationContext.emptyContext)

    override def getCanonicalText: String = tp.canonicalCodeText

    override def isValid: Boolean = true

    override def equalsToText(text: String): Boolean = false

    override def accept[A](visitor: PsiTypeVisitor[A]): A = visitor.visitType(this)

    override def getResolveScope: GlobalSearchScope = null

    override def getSuperTypes: Array[PsiType] = Array.empty
  }
}
