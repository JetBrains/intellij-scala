package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl
import org.jetbrains.plugins.scala.lang.psi.types.result.Typeable.TypingContext

import scala.collection.mutable.ArrayBuffer

class MonocleInjector extends SyntheticMembersInjector {
  override def injectFunctions(source: ScTypeDefinition): Seq[String] = {
    source match {
      // Monocle lenses generation
      case obj: ScObject =>
        obj.fakeCompanionClassOrCompanionClass match {
          case clazz: ScClass if clazz.findAnnotationNoAliases("monocle.macros.Lenses") != null => mkLens(obj)
          case _ => Seq.empty
        }
      case _ => Seq.empty
    }
  }

  private def mkLens(obj: ScObject): ArrayBuffer[String] = {
    val buffer = new ArrayBuffer[String]
    val clazz = obj.fakeCompanionClassOrCompanionClass.asInstanceOf[ScClass]
    val fields = clazz.allVals.collect({ case (f: ScClassParameterImpl, _) => f }).filter(_.isCaseClassVal)
    val prefix = Option(clazz.findAnnotationNoAliases("monocle.macros.Lenses").findAttributeValue("value")) match {
      case Some(literal: ScLiteralImpl) => literal.getValue.toString
      case _ => ""
    }
    fields.foreach({ i =>
      val template = if (clazz.typeParameters.isEmpty)
        s"def $prefix${i.name}: _root_.monocle.Lens[${clazz.qualifiedName}, ${i.getType(TypingContext).map(_.canonicalText).getOrElse("Any")}] = ???"
      else {
        val tparams = s"[${clazz.typeParameters.map(_.getText).mkString(",")}]"
        s"def $prefix${i.name}$tparams: _root_.monocle.Lens[${clazz.qualifiedName}$tparams, ${i.typeElement.get.calcType}] = ???"
      }
      buffer += template
    })
    buffer
  }
}
