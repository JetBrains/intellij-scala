package org.jetbrains.plugins.scala.lang.psi.types

import com.intellij.psi.PsiClass
import org.jetbrains.plugins.scala.extensions.{PsiClassExt, PsiElementExt, PsiParameterExt, StubBasedExt}
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPrimaryConstructor
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScSimpleTypeElement
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.templates.ScExtendsBlockImpl
import org.jetbrains.plugins.scala.lang.psi.types.api.{ExtractClass, ValType}

object ValueClassType {

  object Param {
    def unapply(tp: ScType): Option[ScClassParameter] = tp match {
      case _: ValType => None
      case ExtractClass(cl: ScClass) if isValueClass(cl) =>
        cl.constructors match {
          case Seq(pc: ScPrimaryConstructor) => pc.parameters.headOption
          case _ => None
        }
      case _ => None
    }
  }

  def unapply(tp: ScType): Option[ScType] =
    ValueClassType.Param.unapply(tp).map(_.paramType())

  def isValueType(tp: ScType): Boolean = unapply(tp).isDefined

  def isValueClass(cl: PsiClass): Boolean = cl match {
    case scClass: ScClass =>
      scClass.parameters match {
        case Seq(p) if isValOrCompiled(p) => extendsAnyVal(cl)
        case _ => false
      }
    case _ => false
  }

  def extendsAnyVal(cl: PsiClass): Boolean = {
    cl.getSupers.map(_.qualifiedName).contains("scala.AnyVal")
  }

  // Whenever the project is indexed, use extendsAnyVal above. The below is only offered as a cheap and inaccurate
  // alternative. Note that this method will return true for an implicit class that extends an arbitrary type called
  // `AnyVal`. It does not check whether this type comes from the Scala library.
  def extendsAnyValDumbMode(c: ScClass): Boolean = {
    val extendsBlock = c.stubOrPsiChild(ScalaElementType.EXTENDS_BLOCK).collect { case eb: ScExtendsBlockImpl => eb }.toSeq
    val templateParents = extendsBlock.flatMap(_.templateParents.toSeq)
    val allTypeElements = templateParents.flatMap(_.allTypeElements)
    val allTypeElementStableCodeReferences = allTypeElements.collect { case s: ScSimpleTypeElement => s }.flatMap(_.reference.toSeq)
    val qualNames = allTypeElementStableCodeReferences.map(_.qualName)
    qualNames.contains("AnyVal")
  }

  private def isValOrCompiled(p: ScClassParameter) = {
    if (p.isVal || p.isCaseClassVal) true
    else p.containingScalaFile.exists(_.isCompiled)
  }

  object ImplicitValueClass {
    def unapply(templateDefinition: ScTemplateDefinition): Option[ScClass] =
      templateDefinition match {
        case null => None
        case c: ScClass
          if c.getModifierList.isImplicit && extendsAnyVal(c) => Some(c)
        case _ => None
      }
  }

  object ImplicitValueClassDumbMode {
    def unapply(templateDefinition: ScTemplateDefinition): Option[ScClass] =
      templateDefinition match {
        case null => None
        case c: ScClass
          if c.getModifierList.isImplicit && extendsAnyValDumbMode(c) => Some(c)
        case _ => None
      }
  }
}
