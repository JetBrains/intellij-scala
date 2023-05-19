package org.jetbrains.plugins.scala.text

import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScModifierList, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScFunction, ScFunctionDefinition, ScTypeAlias, ScTypeAliasDefinition, ScValue, ScValueOrVariable, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeBoundsOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.project.ScalaFeatures.forPsiOrDefault

private class ClassPrinter(isScala3: Boolean) {
  def printTo(sb: StringBuilder, cls: ScTypeDefinition): Unit = printTo(sb, cls, "")

  private def printTo(sb: StringBuilder, cls: ScTypeDefinition, indent: String): Unit = {
    val annotations = cls.annotations.map(a => "\n" + indent + textOf(a)).mkString

    val modifiers = textOf(cls.getModifierList)

    val keyword = cls match {
      case _: ScTrait => "trait"
      case _: ScClass => "class"
      case _: ScObject => "object"
      case _ => "TODO"
    }

    val name = cls.name

    val tps = if (cls.typeParameters.isEmpty) "" else cls.typeParameters.map(textOf).mkString("[", ", ", "]")

    val ps = cls.constructors.filterByType[ScPrimaryConstructor].map(textOf).mkString

    val parents = {
      val superTypes = cls.extendsBlock.templateParents.map(_.superTypes).getOrElse(Seq.empty)
      if (superTypes.isEmpty) "" else " extends " + superTypes.map(textOf(_, parens = 1)).mkString(if (cls.isScala3) ", " else " with ")
    }

    val selfType = cls.selfType.map(t => s" ${cls.selfTypeElement.map(_.name).getOrElse("this")}: " + textOf(t) + " =>").mkString

    sb ++= annotations + "\n" + indent + modifiers + keyword + " " + name + tps + ps + parents + " {" + selfType

    val previousLength = sb.length

    cls.extendsBlock.members.foreach {
      case f: ScFunction =>
        sb ++= textOf(f, indent)
      case v: ScValueOrVariable =>
        sb ++= textOf(v, indent)
      case t: ScTypeAlias =>
        sb ++= textOf(t, indent)
      case td: ScTypeDefinition =>
        printTo(sb, td, indent + "  ")
      case _ =>
    }

    if (sb.length > previousLength) {
      sb ++= indent + "}"
    } else {
      if (selfType.isEmpty) {
        sb.setLength(sb.length - 2)
      } else {
        sb ++= "\n"
        sb ++= indent + "}"
      }
    }

    sb ++= "\n"
  }

  private def textOf(pc: ScPrimaryConstructor): String = {
    val modifiers = textOf(pc.getModifierList)
    val clauses = pc.clauses.map(_.clauses).getOrElse(Seq.empty).map(textOf).mkString
    val annotations = pc.annotations.map(textOf(_, emptyParens = clauses.nonEmpty && modifiers.isEmpty)).mkString(" ")
    (if (annotations.isEmpty) "" else " " + annotations) +
      (if (modifiers.isEmpty) "" else " " + modifiers) +
      (if (annotations.nonEmpty && modifiers.isEmpty) " " else "") +
      (if ((annotations.nonEmpty || modifiers.nonEmpty) && clauses.isEmpty) "()" else clauses)
  }

  private def textOf(f: ScFunction, indent: String): String = {
    val annotations = f.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
    val modifiers = textOf(f.getModifierList)
    val name = f.name
    val tps = if (f.typeParameters.isEmpty) "" else f.typeParameters.map(textOf).mkString("[", ", ", "]")
    val clauses = f.paramClauses.clauses.map(textOf).mkString
    val tpe = if (f.isConstructor) "" else (if (tps.isEmpty && clauses.isEmpty) spaceAfter(name) else "") + ": " + textOf(f.returnType)
    val rhs = if (f.isInstanceOf[ScFunctionDefinition]) " = ???" else ""
    annotations + "\n" + indent + "  " + modifiers + "def " + name + tps + clauses + tpe + rhs + "\n"
  }

  private def textOf(v: ScValueOrVariable, indent: String): String = {
    val annotations = v.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
    val modifiers = textOf(v.getModifierList)
    val keyword = if (v.isInstanceOf[ScValue]) "val " else "var "
    val isConstant = !v.hasExplicitType && !v.isAbstract
    val name = v.declaredNames.head
    val tpe = if (isConstant) "" else (spaceAfter(name) + ": " + textOf(v.`type`()))
    val rhs = if (isConstant) (" = " + v.asInstanceOf[ScValueOrVariableDefinition].expr.map(_.getText).getOrElse("")) else if (!v.isAbstract) " = ???" else ""
    annotations + "\n" + indent + "  " + modifiers + keyword + name + tpe + rhs + "\n"
  }

  private def textOf(t: ScTypeAlias, indent: String): String = {
    val annotations = t.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
    val modifiers = textOf(t.getModifierList)
    val name = t.name
    val tps = if (t.typeParameters.isEmpty) "" else t.typeParameters.map(textOf).mkString("[", ", ", "]")
    val bounds = if (t.isDefinition) "" else textOfBoundsIn(t)
    val rhs = t match {
      case definition: ScTypeAliasDefinition => " = " + textOf(definition.aliasedType)
      case _ => ""
    }
    annotations + "\n" + indent + "  " + modifiers + "type " + name + tps + bounds + rhs + "\n"
  }

  private def textOf(p: ScTypeParam): String = {
    val annotations = p.annotations.map(textOf).mkString(" ")
    val variance = if (p.isCovariant) "+" else if (p.isContravariant) "-" else ""
    val name = p.name
    val clauses = p.typeParametersClause.map(_.typeParameters.map(textOf).mkString("[", ", ", "]")).mkString
    val typeBounds = textOfBoundsIn(p)
    val contextBound = p.contextBound.map(t => ": " + textOf(t)).mkString
    (if (annotations.isEmpty) "" else annotations + " ") + variance + name + clauses + typeBounds + contextBound
  }

  private def textOfBoundsIn(o: ScTypeBoundsOwner): String = {
    val lb = textOf(o.lowerBound)
    val ub = textOf(o.upperBound)
    val lower = if (lb == "_root_.scala.Nothing") "" else " >: " + lb
    val upper = if (ub == "_root_.scala.Any") "" else " <: " + ub
    lower + upper
  }

  private def textOf(clause: ScParameterClause): String =
    clause.parameters.map(textOf(_, clause.isUsing)).mkString(if (clause.isImplicit) "(implicit " else if (clause.isUsing) "(using " else "(", ", ", ")")

  private def textOf(p: ScParameter, isUsing: Boolean): String = {
    val annotations = p.annotations.map(textOf).mkString(" ")
    val modifiers = textOf(p.getModifierList)
    val keyword = if (p.isVal) "val " else if (p.isVar) "var " else ""
    val name = p.name
    val byName = if (p.isCallByNameParameter) "=> " else ""
    val tpe = textOf(p.`type`())
    val isAnonymous = isUsing && name == tpe // SCL-21204
    val repeated = if (p.isRepeatedParameter) "*" else ""
    val default = if (p.isDefaultParam) " = ???" else ""
    (if (annotations.isEmpty) "" else annotations + " ") + modifiers + keyword + (if (isAnonymous) "" else name + spaceAfter(name) + ": ") + byName + tpe + repeated + default
  }

  private def textOf(annotation: ScAnnotation): String =
    textOf(annotation, emptyParens = false)

  private def textOf(annotation: ScAnnotation, emptyParens: Boolean): String = {
    val invocation = annotation.constructorInvocation
    val prefix = invocation.simpleTypeElement.map(e => textOf(e.`type`())).getOrElse("")
    val args = invocation.arguments.map(_.exprs.map(_.getText).mkString(", ")).map("(" + _ + ")").mkString
    "@" + prefix + (if (!emptyParens && args == "()") "" else args)
  }

  private def textOf(ml: ScModifierList): String = {
    def scope = ml.accessModifier.flatMap(m => if (m.isThis) Some("this") else m.idText).map("[" + _ + "]").getOrElse("")
    (if (ml.isAbstract && ml.isOverride) "abstract " else "") +
      (if (ml.isOverride) "override " else "") +
      (if (ml.isPrivate) "private" + scope + " " else "") +
      (if (ml.isProtected) "protected" + scope + " " else "") +
      (if (ml.isImplicit) "implicit " else "") +
      (if (ml.isFinal) "final " else "") +
      (if (ml.isSealed) "sealed " else "") +
      (if (ml.isOpen) "open " else "") +
      (if (ml.isAbstract && !ml.isOverride) "abstract " else "") +
      (if (ml.isLazy) "lazy " else "") +
      (if (ml.isTransparent) "transparent " else "") +
      (if (ml.isInline) "inline " else "") +
      (if (ml.isCase) "case " else "")
  }

  private def textOf(tpe: ScType, parens: Int = 0): String = tpe match {
    case FunctionType(_, _) if !tpe.isAliasType && parens > 0 => "(" + tpe.canonicalText + ")"
//    case AliasType(ta, _, _) => ta.containingClass.name + ".this." + ta.name
    case _ =>
      tpe.canonicalText(context)
  }

  private val context = new TypePresentationContext {
    override def nameResolvesTo(name: String, target: PsiElement): Boolean = false

    override def compoundTypeWithAndToken: Boolean = isScala3
  }

  private def textOf(tr: TypeResult): String = tr match {
    case Left(f) => f.toString
    case Right(t) => textOf(t)
  }

  private def spaceAfter(name: String): String =
    if (name.lastOption.exists(c => !c.isLetterOrDigit && c != '`')) " " else ""
}
