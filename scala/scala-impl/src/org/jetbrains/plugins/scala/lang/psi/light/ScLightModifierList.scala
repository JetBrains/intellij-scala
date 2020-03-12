package org.jetbrains.plugins.scala.lang.psi.light

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi._
import com.intellij.psi.impl.light.LightModifierList
import gnu.trove.THashSet
import org.jetbrains.plugins.scala.lang.psi.api.ScalaPsiElement
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScLiteral}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScModifierListOwner, ScNamedElement}
import org.jetbrains.plugins.scala.lang.psi.light.ScLightModifierList._

import scala.annotation.tailrec

private[light] class ScLightModifierList(scalaElement: ScalaPsiElement,
                                         isStatic: Boolean,
                                         isAbstract: Boolean,
                                         isInTrait: Boolean,
                                         isOverride: Boolean)
  extends LightModifierList(scalaElement.getManager) {

  private lazy val annotations: Array[PsiAnnotation] = computeAnnotations()
  private lazy val modifiers: java.util.Set[String] = computeModifiers()

  override def findAnnotation(qualifiedName: String): PsiAnnotation = {
    annotations.find(_.getQualifiedName == qualifiedName).orNull
  }

  override def getAnnotations: Array[PsiAnnotation] = annotations

  override def hasModifierProperty(name: String): Boolean = modifiers.contains(name)

  override def hasExplicitModifier(name: String): Boolean = modifiers.contains(name)

  private def computeModifiers(): java.util.Set[String] = {
    val set = new THashSet[String]

    val annotationsHolder = annotHolder(scalaElement)

    Option(annotationsHolder).foreach { holder =>
      keywordAnnotations.foreach {
        case (fqn, keyword) =>
          if (holder.hasAnnotation(fqn))
            set.add(keyword)
      }
    }

    if (isStatic)
      set.add("static")

    if (isAbstract)
      set.add("abstract")

    Option(modifiersOwner(scalaElement)).foreach { owner =>
      if (owner.hasModifierProperty("final") && !isInTrait) //final methods in traits may be overridden in java
        set.add("final")

      // don't add an access modifier if the element is a parameter
      if (!owner.isInstanceOf[PsiParameter]) {
        owner.getModifierList.accessModifier match {
          case Some(a) if a.isUnqualifiedPrivateOrThis =>
            set.add("private")
          case _ =>
            set.add("public")
        }
      }
    }
    set
  }

  @tailrec
  private def annotHolder(element: PsiElement): ScAnnotationsHolder = element match {
    case h: ScAnnotationsHolder => h
    case n: ScNamedElement => annotHolder(n.nameContext)
    case _ => null
  }

  @tailrec
  private def modifiersOwner(element: PsiElement): ScModifierListOwner = element match {
    case m: ScModifierListOwner => m
    case n: ScNamedElement => modifiersOwner(n.nameContext)
    case _ => null
  }

  private def computeAnnotations(): Array[PsiAnnotation] = {
    val annotationHolder = annotHolder(scalaElement)
    if (annotationHolder == null)
      return PsiAnnotation.EMPTY_ARRAY

    val convertibleAnnotations = annotationHolder.annotations.filterNot { a =>
      a.getQualifiedName match {
        case null => true
        case s if keywordAnnotations.keySet.contains(s) => true
        case s if Set("scala.throws", "scala.inline", "scala.unchecked").contains(s) => true
        case s if s.endsWith("BeanProperty") => true
        case _ => false
      }
    }
    val annotationTexts = convertibleAnnotations.map { a =>
      val fqn = a.getQualifiedName
      val args = convertArgs(a.constructorInvocation.args.toSeq.flatMap(_.exprs))
      s"@$fqn$args"
    }

    val overrideAnnotation =
      if (isOverride) Seq("@" + CommonClassNames.JAVA_LANG_OVERRIDE)
      else Seq.empty

    val factory = PsiElementFactory.getInstance(annotationHolder.getProject)
    (annotationTexts ++ overrideAnnotation).map(factory.createAnnotationFromText(_, this)).toArray
  }

  private def convertExpression(e: ScExpression): String = {
    def problem = "CannotConvertExpression"

    e match {
      case a: ScAssignment =>
        val res = a.leftExpression.getText + " = "
        a.rightExpression match {
          case Some(expr) => res + convertExpression(expr)
          case _ => res
        }
      case l: ScLiteral if !l.isMultiLineString => l.getText
      case l: ScLiteral => "\"" + StringUtil.escapeStringCharacters(l.getValue.toString) + "\""
      case call: ScMethodCall =>
        if (call.getInvokedExpr.getText.endsWith("Array")) {
          call.args.exprs.map(convertExpression).mkString("{", ", ", "}")
        } else problem
      case call: ScGenericCall =>
        if (call.referencedExpr.getText.endsWith("classOf")) {
          val arguments = call.arguments
          if (arguments.length == 1) {
            val typeResult = arguments.head.`type`()
            typeResult match {
              case Right(tp) =>
                tp.extractClass match {
                  case Some(clazz) => clazz.getQualifiedName + ".class"
                  case _ => problem
                }
              case _ => problem
            }
          } else problem
        } else problem
      case n: ScNewTemplateDefinition =>
        n.extendsBlock.templateParents.flatMap(_.constructorInvocation) match {
          case Some(constr) =>
            constr.reference match {
              case Some(ref) =>
                ref.resolve() match {
                  case c: PsiClass =>
                    var res = "@" + c.getQualifiedName
                    constr.args match {
                      case Some(constrArgs) => res += convertArgs(constrArgs.exprs)
                      case _ =>
                    }
                    res
                  case _ => problem
                }
              case _ => problem
            }
          case _ => problem
        }
      case _ => problem
    }
  }

  private def convertArgs(args: Seq[ScExpression]): String = {
    if (args.isEmpty) ""
    else args.map(convertExpression).mkString("(", ", ", ")")
  }
}

private[light] object ScLightModifierList {
  val keywordAnnotations: Map[String, String] = Map(
    "scala.native" -> "native",
    "scala.annotation.strictfp" -> "strictfp",
    "scala.volatile" -> "volatile",
    "scala.transient" -> "transient")

  def apply(scalaElement: ScalaPsiElement,
            isStatic: Boolean = false,
            isAbstract: Boolean = false,
            isInTrait: Boolean = false,
            isOverride: Boolean = false): LightModifierList =
    new ScLightModifierList(scalaElement, isStatic, isAbstract, isInTrait, isOverride)

  def empty(manager: PsiManager): LightModifierList = new LightModifierList(manager)
}