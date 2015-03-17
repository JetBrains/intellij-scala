package org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef

import com.intellij.psi.{PsiElement, PsiMethod}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.lang.psi.impl.base.ScLiteralImpl
import org.jetbrains.plugins.scala.lang.psi.impl.statements.params.ScClassParameterImpl
import org.jetbrains.plugins.scala.project._

import scala.collection.mutable.ArrayBuffer

/**
 * @author Mikhail.Mutcianko
 *         date 26.12.14
 */
object SyntheticMembersInjector {

  def inject(source: ScTypeDefinition): Seq[PsiMethod] = processRules(source)

  private def processRules(source: ScTypeDefinition): Seq[PsiMethod] = {
    val buf = new ArrayBuffer[PsiMethod]
    source match {
      // legacy macro emulation - in 2.10 quasiquotes were implemented by a compiler plugin
      // so we need to manually add QQ interpolator stub
      case c:ScClass if c.qualifiedName == "scala.StringContext" && needQQEmulation(c) =>
        val template = "def q(args: Any*): scala.reflect.runtime.universe.Tree = ???"
        try {
          val method = ScalaPsiElementFactory.createMethodWithContext(template, c, c)
          method.setSynthetic(c)
          buf += method
        } catch { case  e: Exception => Seq()}
      // Monocle lenses generation
      case obj:ScObject =>
        obj.fakeCompanionClassOrCompanionClass match {
          case clazz:ScClass if clazz.findAnnotation("monocle.macros.Lenses") != null =>
            buf ++= mkLens(obj)
          case _ =>
        }
      case _ =>
    }
    buf.toSeq
  }

  private def mkLens(obj: ScObject): ArrayBuffer[PsiMethod] = {
    val buf = new ArrayBuffer[PsiMethod]
    val clazz = obj.fakeCompanionClassOrCompanionClass.asInstanceOf[ScClass]
    val fields = clazz.allVals.collect({case (f: ScClassParameterImpl, _) => f}).filter(_.isCaseClassVal)
    val prefix = Option(clazz.findAnnotation("monocle.macros.Lenses").findAttributeValue("value")) match {
      case Some(literal: ScLiteralImpl) => literal.getValue.toString
      case _ => ""
    }
    fields.foreach({ i =>
      try {
        val template = s"def $prefix${i.name}: monocle.Lens[${clazz.getQualifiedName}, ${i.typeElement.get.calcType}] = ???"
        val method = ScalaPsiElementFactory.createMethodWithContext(template, clazz, obj)
        method.setSynthetic(clazz)
        buf += method
      } catch { case _: Throwable => }
    })
    buf
  }

  private def needQQEmulation(e: PsiElement) =
    e.module.exists(_.scalaCompilerSettings.plugins.exists(_.contains("paradise_2.10")))
}
