package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.psi.impl.light.LightReferenceListBuilder
import com.intellij.psi.{PsiClassType, PsiManager, PsiNamedElement, PsiReferenceList}
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiClassExt, PsiNamedElementExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder}
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, ParameterizedType}

private object ScLightThrowsList {
  def empty(manager: PsiManager): PsiReferenceList =
    new LightReferenceListBuilder(manager, PsiReferenceList.Role.THROWS_LIST)

  def apply(named: PsiNamedElement): PsiReferenceList = {
    named.nameContext match {
      case holder: ScAnnotationsHolder =>

        val refList = new LightReferenceListBuilder(holder.getManager, holder.getLanguage, PsiReferenceList.Role.THROWS_LIST)
        val throwsAnnotations = holder.annotations("scala.throws")
        val exceptionClassTypes = throwsAnnotations.flatMap(a => fromClassArgument(a) ++ fromTypeArgument(a))

        exceptionClassTypes.foreach(refList.addReference)

        refList
      case _ => empty(named.getManager)
    }
  }

  //for primary constructor `class throws[T <: Throwable](cause: String = "")` of `scala.throws`
  private def fromTypeArgument(annotation: ScAnnotation): Option[PsiClassType] = {
    val typeArgList = annotation.constructorInvocation.typeArgList
    for {
      taList <- typeArgList
      typeArg <- taList.typeArgs.headOption
      tpe <- typeArg.`type`().toOption
      classType <- tpe.toPsiType.asOptionOf[PsiClassType]
    } yield {
      classType
    }
  }

  //for `def this(clazz: Class[T])` constructor or `scala.throws`
  private def fromClassArgument(annotation: ScAnnotation): Option[PsiClassType] = {
    for {
      expression <- annotation.constructorInvocation.args.flatMap(_.exprs.headOption)
      clazzType  <- expression.`type`().toOption //classOf[Exception]
      exception  <- extractExceptionClassType(clazzType)
    } yield {
      exception
    }
  }


  private def extractExceptionClassType(classOFException: ScType): Option[PsiClassType] = classOFException match {
    case ParameterizedType(ExtractClass(clazz), Seq(arg)) if clazz.qualifiedName == "java.lang.Class" =>
      arg.toPsiType match {
        case ct: PsiClassType => Some(ct)
        case _ => None
      }
    case _ => None
  }

}
