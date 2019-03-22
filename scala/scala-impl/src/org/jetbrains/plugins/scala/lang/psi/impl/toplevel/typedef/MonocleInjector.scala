package org.jetbrains.plugins.scala.lang.psi.impl
package toplevel
package typedef

import com.intellij.psi.PsiAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl

class MonocleInjector extends SyntheticMembersInjector {

  import MonocleInjector.mkLens

  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    val companionClass = source match {
      case obj: ScObject => obj.fakeCompanionClassOrCompanionClass
      case _ => null
    }

    companionClass match {
      case clazz: ScClass => mkLens(clazz, clazz.findAnnotationNoAliases("monocle.macros.Lenses"))
      case _ => Seq.empty
    }
  }
}

object MonocleInjector {

  // Monocle lenses generation
  private def mkLens(clazz: ScClass, annotation: PsiAnnotation): Seq[String] = annotation match {
    case null => Seq.empty
    case _ =>
      val prefix = annotation.findAttributeValue("value") match {
        case ScLiteralImpl.string(value) => value
        case _ => ""
      }

      mkLens(clazz, prefix)
  }

  private[this] def mkLens(clazz: ScClass, prefix: String): Seq[String] = {
    import org.jetbrains.plugins.scala.lang.psi.types.result._
    val typeParametersText = clazz.typeParameters.map(_.getText) match {
      case Seq() => ""
      case seq => seq.mkString("[", ",", "]")
    }

    clazz.allVals.collect {
      case (f: ScClassParameterImpl, _) if f.isCaseClassVal => f
    }.map { parameter =>
      val typeText = if (typeParametersText.isEmpty) {
        parameter.`type`().toOption.map(_.canonicalText).getOrElse("Any")
      } else {
        parameter.typeElement.get.calcType.toString
      }

      s"def $prefix${parameter.name}$typeParametersText: _root_.monocle.Lens[${clazz.`type`().getOrAny}, $typeText] = ???"
    }.toSeq
  }
}
