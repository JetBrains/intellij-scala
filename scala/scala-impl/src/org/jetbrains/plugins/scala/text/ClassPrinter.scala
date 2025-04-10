package org.jetbrains.plugins.scala.text

import com.intellij.psi.{PsiClass, PsiElement}
import org.jetbrains.plugins.scala.extensions.{IterableOnceExt, Parent, PsiClassExt}
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScModifierList, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScParameter, ScParameterClause, ScTypeParam}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScEnumCase, ScExtension, ScFunction, ScFunctionDefinition, ScTypeAlias, ScTypeAliasDefinition, ScValue, ScValueOrVariable, ScValueOrVariableDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScEnum, ScGiven, ScGivenDefinition, ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement, ScPackaging, ScTypeBoundsOwner, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult
import org.jetbrains.plugins.scala.lang.psi.types.{ScLiteralType, ScType, TypePresentationContext}
import org.jetbrains.plugins.scala.project.ScalaFeatures.forPsiOrDefault

class ClassPrinter(isScala3: Boolean, extendsSeparator: String = " ", withPrivate: Boolean = true, normalize: Boolean = false) {
  def textOf(e: PsiElement): String = e match {
    case cls: ScTypeDefinition =>
      val sb = new StringBuilder()
      printTo(sb, cls)
      sb.toString
    case f: ScFunction => textOf(f, "")
    case v: ScBindingPattern => textOf(v.nameContext.asInstanceOf[ScValueOrVariable], v, "")
    case t: ScTypeAlias => textOf(t, "")
  }

  def printTo(sb: StringBuilder, cls: ScTypeDefinition): Unit = printTo(sb, cls, "")

  private def printTo(sb: StringBuilder, cls: ScTypeDefinition, indent: String): Unit = {
    val annotations = cls.annotations.map(a => "\n" + indent + textOf(a)).mkString

    val modifiers = textOf(cls.getModifierList)

    val keyword = cls match {
      case _: ScEnum => "enum"
      case _: ScTrait => "trait"
      case _: ScClass => "class"
      case _: ScObject => "object"
      case _: ScGiven => "given"
      case _ => ""
    }

    val isGiven = cls.isInstanceOf[ScGiven]

    val isAnonymous = isGiven && cls.name.startsWith("given_") // TODO .isAnonymous

    val name = if (isAnonymous) "" else cls.name

    val tps = if (cls.typeParameters.isEmpty) "" else cls.typeParameters.map(textOf).mkString("[", ", ", "]")

    val ps = cls.constructors.filterByType[ScPrimaryConstructor].map(textOf(_, inCaseClass = modifiers.contains("case"))).mkString

    val parents = {
      val superTypes = cls.extendsBlock.templateParents.map(_.superTypes).getOrElse(Seq.empty)
      val classParent =
        if (normalize && isScala3) cls.supers.takeWhile(_.isInterface).iterator.flatMap(c => c +: c.getSupers).find(!_.isInterface).filter(_.qualifiedName != "java.lang.Object").map(ScDesignatorType(_)).toList
        else Seq.empty
      if (superTypes.isEmpty) "" else (if (isGiven) (if (isAnonymous && tps.isEmpty && ps.isEmpty) "" else ": ") else s"${extendsSeparator}extends ") + (classParent ++ superTypes).map(textOf(_, parens = 1)).mkString(if (cls.isScala3 && !isGiven) ", " else s"${extendsSeparator}with ")
    }

    val derivations = {
      val refs = cls.extendsBlock.derivesClause.map(_.derivedReferences).getOrElse(Seq.empty)
      val fqns = refs.map(_.resolve()).collect { case f: ScTemplateDefinition => "_root_." + f.qualifiedName }
      if (fqns.isEmpty) "" else s"${extendsSeparator}derives " + fqns.mkString(", ")
    }

    val selfType = cls.selfType.map(t => s" ${cls.selfTypeElement.map(_.name).getOrElse("this")}: " + textOf(t) + " =>").mkString

    val givenClauses = cls match {
      case g: ScGivenDefinition => g.clauses.map(_.clauses).getOrElse(Seq.empty).map(textOf(_, inPrivateConstructor = false, inCaseClass = false)).mkString
      case _ => ""
    }

    sb ++= annotations + "\n" + indent + modifiers + keyword + " " + name + tps + ps + givenClauses + parents + derivations + (if (isGiven) " with" else "") + " {" + selfType

    val previousLength = sb.length

    cls.extendsBlock.members.filter(m => withPrivate || !isPrivate(m)).foreach {
      case f: ScFunction =>
        sb ++= textOf(f, indent)
      case v: ScValueOrVariable =>
        sb ++= textOf(v, v.declaredElements.head, indent)
      case t: ScTypeAlias =>
        sb ++= textOf(t, indent)
      case t: ScExtension =>
        sb ++= textOf(t, indent)
      case td: ScTypeDefinition =>
        printTo(sb, td, indent + "  ")
      case _ =>
    }

    cls match {
      case e: ScEnum =>
        e.cases.foreach { c =>
          sb ++= textOf(c, indent)
        }
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

  private def isPrivate(e: ScModifierListOwner): Boolean =
    e.getModifierList.accessModifier.exists(_.isUnqualifiedPrivateOrThis)

  private def textOf(pc: ScPrimaryConstructor, inCaseClass: Boolean): String = {
    val modifiers = textOf(pc.getModifierList)
    val inPrivateConstructor = isPrivate(pc)
    val clauses = {
      val cs = pc.clauses.map(_.clauses).getOrElse(Seq.empty)
      (cs.take(1).map(textOf(_, inPrivateConstructor, inCaseClass)) ++ cs.drop(1).map(textOf(_, inPrivateConstructor, inCaseClass = false))).mkString
    }
    val annotations = pc.annotations.map(textOf(_, emptyParens = clauses.nonEmpty && modifiers.isEmpty)).mkString(" ")
    val s = (if (annotations.isEmpty) "" else " " + annotations) +
      (if (modifiers.isEmpty) "" else " " + modifiers) +
      (if (annotations.nonEmpty && modifiers.isEmpty) " " else "") +
      (if ((annotations.nonEmpty || modifiers.nonEmpty) && clauses.isEmpty) "()" else clauses)
    if (normalize && !inCaseClass && s == "()") "" else s
  }

  private def textOf(c: ScEnumCase, indent: String): String = {
    val tps = if (c.typeParameters.isEmpty) "" else c.typeParameters.map(textOf).mkString("[", ", ", "]")
    val ps = c.constructors.filterByType[ScPrimaryConstructor].map(textOf(_, inCaseClass = true)).mkString
    "\n" + indent + "  " + "case " + c.name + tps + ps + "\n"
  }

  private def textOf(f: ScFunction, indent: String): String = {
    val isGiven = f.isInstanceOf[ScGiven]
    val isAnonymous = isGiven && f.name.startsWith("given_") // TODO .isAnonymous
    val annotations = f.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
    val modifiers = textOf(f.getModifierList)
    val keyword = if (isGiven) "given " else "def "
    val name = if (isAnonymous) "" else f.name
    val tps = if (f.typeParameters.isEmpty) "" else f.typeParameters.map(textOf).mkString("[", ", ", "]")
    val clauses = f.paramClauses.clauses.map(textOf(_, inPrivateConstructor = false, inCaseClass = false)).mkString
    val tpe = if (f.isConstructor) "" else (if (tps.isEmpty && clauses.isEmpty) spaceAfter(name) else "") + (if (isAnonymous && clauses.isEmpty && tps.isEmpty) "" else ": ") + textOf(f.returnType)
    val rhs = if (f.isInstanceOf[ScFunctionDefinition]) " = ???" else ""
    annotations + "\n" + indent + "  " + modifiers + keyword + name + tps + clauses + tpe + rhs + "\n"
  }

  private def textOf(e: ScExtension, indent: String): String = {
    val tps = if (e.typeParameters.isEmpty) "" else e.typeParameters.map(textOf).mkString("[", ", ", "]")
    val clauses = e.clauses.toSeq.flatMap(_.clauses).map(textOf(_, inPrivateConstructor = false, inCaseClass = false)).mkString
    val methods = e.extensionMethods.map(textOf(_, indent + "  ")).mkString
    "\n" + indent + "  " + "extension " + tps + clauses + methods
  }

  private def textOf(v: ScValueOrVariable, symbol: ScTypedDefinition, indent: String): String = {
    val annotations = v.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
    val modifiers = textOf(v.getModifierList)
    val keyword = if (v.isInstanceOf[ScValue]) "val " else "var "
    val symbolType = symbol.`type`()
    val isConstant = (v.hasModifierPropertyScala("final") || v.hasModifierPropertyScala("inline")) && !v.hasExplicitType && !v.isAbstract && symbolType.exists(canBeTypeOfConstant)
    val name = symbol.name
    val tpe = if (isConstant) "" else (spaceAfter(name) + ": " + textOf(symbolType))
    val rhs = if (isConstant) (" = " + v.asInstanceOf[ScValueOrVariableDefinition].expr.map(_.getText).getOrElse("")) else if (!v.isAbstract) " = ???" else ""
    annotations + "\n" + indent + "  " + modifiers + keyword + name + tpe + rhs + "\n"
  }

  private def canBeTypeOfConstant(tpe: ScType): Boolean = tpe match {
    case _: ScLiteralType => true
    case t if t.isPrimitive => true
    case t if t.isNull => true
    case ScDesignatorType(cls: PsiClass) if cls.getQualifiedName == "java.lang.String" => true
    case _ => false
  }

  def printTo(sb: StringBuilder, alias: ScTypeAlias): Unit = {
    sb ++= textOf(alias, "").split("\n").map(_.stripPrefix("  ")).mkString("\n")
    sb ++= "\n"
  }

  private def textOf(t: ScTypeAlias, indent: String): String = {
    val annotations = t.annotations.map(a => "\n" + indent + "  " + textOf(a)).mkString
    val modifiers = textOf(t.getModifierList)
    val name = t.name
    val tps = if (t.typeParameters.isEmpty) "" else t.typeParameters.map(textOf).mkString("[", ", ", "]")
    val bounds = textOfBoundsIn(t, withLower = !t.isDefinition, withUpper = !t.isDefinition || t.upperTypeElement.isDefined)
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

  private def textOfBoundsIn(o: ScTypeBoundsOwner, withLower: Boolean = true, withUpper: Boolean = true): String = {
    val lower = if (!withLower) "" else {
      val lb = textOf(o.lowerBound)
      if (lb == "_root_.scala.Nothing") "" else " >: " + lb
    }
    val upper = if (!withUpper) "" else {
      val ub = textOf(o.upperBound)
      if (ub == "_root_.scala.Any") "" else " <: " + ub
    }
    lower + upper
  }

  private def textOf(clause: ScParameterClause, inPrivateConstructor: Boolean, inCaseClass: Boolean): String = {
    val ps = clause.parameters.filter(p => withPrivate || !inPrivateConstructor || ((inCaseClass || p.isVal || p.isVar) && !isPrivate(p)))
    ps.map(textOf(_, inCaseClass)).mkString(if (ps.nonEmpty) (if (clause.hasImplicitKeyword) "(implicit " else if (clause.hasUsingKeyword) "(using " else "(") else "(", ", ", ")")
  }

  private def textOf(p: ScParameter, inCaseClass: Boolean): String = {
    val annotations = p.annotations.map(textOf).mkString(" ")
    val modifiers = {
      val s = textOf(p.getModifierList)
      if (withPrivate) s else s.replace("private ", "")
    }
    val keyword =
      if (withPrivate || !isPrivate(p)) if (p.isVal) (if (!normalize || !(inCaseClass && modifiers.isEmpty)) "val " else "") else if (p.isVar) "var " else ""
      else ""
    val name = p.name
    val byName = if (p.isCallByNameParameter) "=> " else ""
    val tpe = textOf(p.`type`())
    val isAnonymous = p.isAnonymous
    val repeated = if (p.isRepeatedParameter) "*" else ""
    val default = if (p.baseDefaultParam) " = ???" else ""
    (if (annotations.isEmpty) "" else annotations + " ") + modifiers + keyword + (if (isAnonymous) "" else name + spaceAfter(name) + ": ") + byName + tpe + repeated + default
  }

  private def textOf(annotation: ScAnnotation): String =
    textOf(annotation, emptyParens = false)

  private def textOf(annotation: ScAnnotation, emptyParens: Boolean): String = {
    val invocation = annotation.constructorInvocation
    val prefix = invocation.simpleTypeElement.map(e => textOf(e.`type`())).getOrElse("")
    val args = invocation.arguments.map(_.exprs.map(e => textWithQualifiers(e).replaceAll("\r?\n\\s*", " ")).mkString(", ")).map("(" + _ + ")").mkString
    "@" + prefix + (if (!emptyParens && args == "()") "" else args)
  }

  private def textWithQualifiers(expr: ScExpression): String =
    expr.getText.replaceAll("""(?<!\.|\w)Array\(""", "_root_.scala.Array(") // TODO Resolve references

  private def textOf(ml: ScModifierList): String = {
    def scope = ml.getParent match {
      case Parent(p: ScPackaging) => p.packageName.split('.').lastOption.getOrElse("")
      case Parent(c: ScNamedElement) => c.name
      case _ => ""
    }
    def qualifier = ml.accessModifier.flatMap(m => if (m.isThis) Some("this") else m.idText).filter(q => !normalize || q != scope).map("[" + _ + "]").getOrElse("")
    (if (ml.isAbstract && ml.isOverride) "abstract " else "") +
      (if (ml.isOverride) "override " else "") +
      (if (ml.isPrivate) "private" + qualifier + " " else "") +
      (if (ml.isProtected) "protected" + qualifier + " " else "") +
      (if (ml.isImplicit) "implicit " else "") +
      (if (ml.isFinal) "final " else "") +
      (if (ml.isSealed) "sealed " else "") +
      (if (ml.isOpen) "open " else "") +
      (if (ml.isAbstract && !ml.isOverride) "abstract " else "") +
      (if (ml.isLazy) "lazy " else "") +
      (if (ml.isTransparent) "transparent " else "") +
      (if (ml.isOpaque) "opaque " else "") +
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
