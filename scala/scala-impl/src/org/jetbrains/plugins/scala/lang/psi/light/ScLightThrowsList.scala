package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.light.LightReferenceListBuilder
import com.intellij.psi.{PsiClassType, PsiManager, PsiNamedElement, PsiReferenceList}
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, ParameterizedType}

private object ScLightThrowsList {
  def empty(manager: PsiManager): PsiReferenceList =
    new LightReferenceListBuilder(manager, PsiReferenceList.Role.THROWS_LIST)

  def apply(named: PsiNamedElement): PsiReferenceList = {
    def extractExceptionClassType(classOFException: ScType): Option[PsiClassType] = classOFException match {
      case ParameterizedType(ExtractClass(clazz), Seq(arg)) if clazz.qualifiedName == "java.lang.Class" =>
        arg.toPsiType match {
          case ct: PsiClassType => Some(ct)
          case _ => None
        }
      case _ => None
    }

    named.nameContext match {
      case holder: ScAnnotationsHolder =>

        val refList = new LightReferenceListBuilder(holder.getManager, holder.getLanguage, PsiReferenceList.Role.THROWS_LIST)

        for {
          annotation <- holder.annotations("scala.throws")
          expression <- annotation.constructorInvocation.args.map(_.exprs).getOrElse(Seq.empty)
          clazzType  <- expression.`type`().toOption //classOf[Exception]
          exception  <- extractExceptionClassType(clazzType)
        } {
          refList.addReference(exception)
        }
        refList

      case _ => empty(named.getManager)
    }
  }
}
