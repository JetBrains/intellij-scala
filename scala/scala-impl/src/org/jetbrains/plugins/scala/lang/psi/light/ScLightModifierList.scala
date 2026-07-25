package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.impl.light.LightModifierList
import com.intellij.psi.util.PsiUtil
import org.jetbrains.annotations.Nullable
import org.jetbrains.plugins.scala.extensions.ObjectExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.literals.ScStringLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.types.ScTypeArgument
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotation, ScAnnotationsHolder, ScConstructorInvocation, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.light.ScLightModifierList._
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

import java.util
import scala.annotation.tailrec

//noinspection ScalaWrongPlatformMethodsUsage
private[light] class ScLightModifierList(
  scalaElement: ScalaPsiElement,
  isStatic: Boolean,
  isAbstract: Boolean,
  isInTrait: Boolean,
  isOverride: Boolean
) extends LightModifierList(scalaElement.getManager) {

  private lazy val annotations: Array[PsiAnnotation] = computeAnnotations()
  private lazy val modifiers: java.util.Set[String] = computeModifiers()

  override def findAnnotation(qualifiedName: String): PsiAnnotation = {
    annotations.find(_.getQualifiedName == qualifiedName).orNull
  }

  override def getAnnotations: Array[PsiAnnotation] = annotations

  override def hasModifierProperty(name: String): Boolean = modifiers.contains(name)

  override def hasExplicitModifier(name: String): Boolean = modifiers.contains(name)

  private def computeModifiers(): java.util.Set[String] = {
    val modifiers = new util.HashSet[String]

    addKeywordAnnotationModifiers(modifiers)
    addExplicitModifiers(modifiers)
    addOwnerModifiers(modifiers)

    modifiers
  }

  private def addKeywordAnnotationModifiers(modifiers: java.util.Set[String]): Unit = {
    for {
      holder <- Option(getAnnotationHolder(scalaElement)).iterator
      (fqn, keyword) <- KeywordAnnotations.iterator
    } {
      if (holder.hasAnnotation(fqn))
        modifiers.add(keyword)
    }
  }

  private def addExplicitModifiers(modifiers: java.util.Set[String]): Unit = {
    if (isStatic)
      modifiers.add("static")

    if (isAbstract)
      modifiers.add("abstract")
  }

  private def addOwnerModifiers(modifiers: java.util.Set[String]): Unit = {
    Option(getModifiersOwner(scalaElement)).foreach { owner =>
      addFinalModifier(owner, modifiers)
      addAccessModifier(owner, modifiers)
    }
  }

  private def addFinalModifier(owner: ScModifierListOwner, modifiers: java.util.Set[String]): Unit =
    if (owner.hasModifierProperty("final") && !isInTrait) //final methods in traits may be overridden in java
      modifiers.add("final")

  private def addAccessModifier(owner: ScModifierListOwner, modifiers: java.util.Set[String]): Unit = {
    // don't add an access modifier if the element is a parameter
    val ignoreAccessModifierForParameter = owner.isInstanceOf[PsiParameter] && !owner.is[ScClassParameter]
    if (!ignoreAccessModifierForParameter) {
      owner.getModifierList.accessModifier match {
        case Some(a) if a.isUnqualifiedPrivateOrThis =>
          modifiers.add("private")
        case _ =>
          modifiers.add("public")
      }
    }
  }

  @Nullable
  @tailrec
  private def getAnnotationHolder(element: PsiElement): ScAnnotationsHolder = element match {
    case h: ScAnnotationsHolder => h
    case n: ScNamedElement => getAnnotationHolder(n.nameContext)
    case _ => null
  }

  @Nullable
  @tailrec
  private def getModifiersOwner(element: PsiElement): ScModifierListOwner = element match {
    case m: ScModifierListOwner => m
    case n: ScNamedElement => getModifiersOwner(n.nameContext)
    case _ => null
  }

  private def computeAnnotations(): Array[PsiAnnotation] = {
    Option(getAnnotationHolder(scalaElement))
      .map(createJavaAnnotations)
      .getOrElse(PsiAnnotation.EMPTY_ARRAY)
  }

  private def createJavaAnnotations(annotationHolder: ScAnnotationsHolder): Array[PsiAnnotation] = {
    val annotations = annotationHolder.annotations
    val annotationsForJava = annotations.filter(isJavaConvertibleAnnotation)
    val annotationsForJavaTexts = annotationsForJava.map(annotationText)

    val allAnnotationTexts = annotationsForJavaTexts ++ overrideAnnotationText.toSeq

    val factory = PsiElementFactory.getInstance(annotationHolder.getProject)
    allAnnotationTexts.map(factory.createAnnotationFromText(_, this)).toArray
  }

  private def isJavaConvertibleAnnotation(annotation: ScAnnotation): Boolean = {
    val fqn = annotation.getQualifiedName

    // Note: we explicitly not simplify this if/else chain for better debuggability
    if (fqn == null)
      false
    else if (KeywordAnnotations.contains(fqn))
      false
    else if (ScalaOnlyAnnotationFqns.contains(fqn))
      false
    else if (fqn.endsWith("BeanProperty"))
      false
    else if (hasJavaKeywordInQualifiedName(fqn))
      false
    else
      true
  }

  private def hasJavaKeywordInQualifiedName(qualifiedName: String): Boolean = {
    val javaLanguageLevel = PsiUtil.getLanguageLevel(getProject)
    ScalaNamesUtil.cleanFqn(qualifiedName).split('.').exists(PsiUtil.isKeyword(_, javaLanguageLevel))
  }

  private def annotationText(annotation: ScAnnotation): String =
    s"@${annotation.getQualifiedName}${annotationArgumentText(annotation)}"

  private def annotationArgumentText(annotation: ScAnnotation): String = {
    val arguments = annotation.constructorInvocation.args.toSeq.flatMap(_.exprs)
    convertArguments(arguments)
  }

  private def overrideAnnotationText: Option[String] =
    if (isOverride) Some(s"@${CommonClassNames.JAVA_LANG_OVERRIDE}")
    else None

  private def convertExpression(expression: ScExpression): String = expression match {
    case assignment: ScAssignment =>
      convertAssignment(assignment)
    case string: ScStringLiteral if string.isMultiLineString =>
      s""""${StringUtil.escapeStringCharacters(string.getValue)}""""
    case literal: ScLiteral =>
      literal.getText
    case call: ScMethodCall =>
      convertArrayCall(call)
    case call: ScGenericCall =>
      convertClassOf(call)
    case newTemplate: ScNewTemplateDefinition =>
      convertNestedAnnotation(newTemplate)
    case _ =>
      CannotConvertExpression
  }

  private def convertAssignment(assignment: ScAssignment): String = {
    val javaName = ScalaNamesUtil.BacktickedName.stripBackticks(assignment.leftExpression.getText)
    val assignmentPrefix = s"$javaName = "

    assignment.rightExpression match {
      case Some(expression) => s"$assignmentPrefix${convertExpression(expression)}"
      case _ => assignmentPrefix
    }
  }

  private def convertArrayCall(call: ScMethodCall): String =
    if (call.getInvokedExpr.getText.endsWith("Array")) {
      val argsConverted = call.args.exprs.map(convertExpression)
      argsConverted.mkString("{", ", ", "}")
    }
    else CannotConvertExpression

  private def convertClassOf(call: ScGenericCall): String = {
    val isClassOfCall = call.referencedExpr.getText.endsWith("classOf")

    if (!isClassOfCall)
      CannotConvertExpression
    else {
      val arguments = call.typeArguments
      if (arguments.length == 1)
        convertClassOfTypeArgument(arguments.head)
      else
        CannotConvertExpression
    }
  }

  private def convertClassOfTypeArgument(typeArgument: ScTypeArgument): String =
    typeArgument.`type`() match {
      case Right(tp) =>
        tp.extractClass match {
          case Some(clazz) =>
            s"${clazz.getQualifiedName}.class"
          case _ => CannotConvertExpression
        }
      case _ => CannotConvertExpression
    }

  private def convertNestedAnnotation(newTemplate: ScNewTemplateDefinition): String =
    nestedAnnotationConstructorAndClass(newTemplate) match {
      case Some((constructor, annotationClass)) =>
        nestedAnnotationText(annotationClass, constructor)
      case _ =>
        CannotConvertExpression
    }

  private def nestedAnnotationConstructorAndClass(
    newTemplate: ScNewTemplateDefinition
  ): Option[(ScConstructorInvocation, PsiClass)] = {
    val constructor = newTemplate.extendsBlock.templateParents.flatMap(_.firstParentClause)
    constructor.flatMap { invocation =>
      resolvedAnnotationClass(invocation).map(annotationClass => invocation -> annotationClass)
    }
  }

  private def resolvedAnnotationClass(constructor: ScConstructorInvocation): Option[PsiClass] =
    constructor.reference
      .flatMap(reference => Option(reference.resolve()))
      .collect { case clazz: PsiClass => clazz }

  private def nestedAnnotationText(annotationClass: PsiClass, constructor: ScConstructorInvocation): String = {
    val arguments = constructor.args.map(args => convertArguments(args.exprs)).getOrElse("")
    s"@${annotationClass.getQualifiedName}$arguments"
  }

  private def convertArguments(arguments: Seq[ScExpression]): String = {
    if (arguments.isEmpty) ""
    else arguments.map(convertExpression).mkString("(", ", ", ")")
  }
}

private[light] object ScLightModifierList {

  private val CannotConvertExpression = "CannotConvertExpression"

  private val ScalaOnlyAnnotationFqns = Set(
    "scala.throws",
    "scala.inline",
    "scala.unchecked"
  )

  private val KeywordAnnotations: Map[String, String] = Map(
    "scala.native" -> "native",
    "scala.scalajs.js.native" -> "native",
    "scala.annotation.strictfp" -> "strictfp",
    "scala.volatile" -> "volatile",
    "scala.transient" -> "transient"
  )

  def apply(
    scalaElement: ScalaPsiElement,
    isStatic: Boolean = false,
    isAbstract: Boolean = false,
    isInTrait: Boolean = false,
    isOverride: Boolean = false
  ): LightModifierList =
    new ScLightModifierList(scalaElement, isStatic, isAbstract, isInTrait, isOverride)

  def empty(manager: PsiManager): LightModifierList = new LightModifierList(manager)
}
