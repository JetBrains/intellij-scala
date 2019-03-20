package org.jetbrains.plugins.scala
package lang
package psi
package annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.psi.{PsiMethod, PsiModifier}
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.ErrorAnnotationMessage
import org.jetbrains.plugins.scala.annotator.quickfix.{ImplementMethodsQuickFix, ModifierQuickFix}
import org.jetbrains.plugins.scala.annotator.template._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaration, ScFunctionDefinition, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTrait, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.{Annotatable, ScalaFile}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalSignature, ValueClassType}
import org.jetbrains.plugins.scala.overrideImplement.{ScalaOIUtil, ScalaTypedMember}

trait ScTemplateDefinitionAnnotator extends Annotatable { self: ScTemplateDefinition =>

  import ScTemplateDefinitionAnnotator._

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    annotateFinalClassInheritance(holder)
    annotateMultipleInheritance(holder)
    annotateNeedsToBeTrait(holder)
    annotateUndefinedMember(holder)
    annotateSealedclassInheritance(holder)
    annotateNeedsToBeAbstract(holder, typeAware)
    annotateNeedsToBeMixin(holder)

    if (typeAware) {
      annotateIllegalInheritance(holder)
      annotateObjectCreationImpossible(holder)
    }
  }

  // TODO package private
  def annotateFinalClassInheritance(holder: AnnotationHolder): Unit = {
    val newInstance = isInstanceOf[ScNewTemplateDefinition]
    val hasBody = extendsBlock.templateBody.isDefined

    if (newInstance && !hasBody) return

    superRefs(this).collect {
      case (range, clazz) if clazz.hasFinalModifier =>
        (range, ScalaBundle.message("illegal.inheritance.from.final.kind", kindOf(clazz, toLowerCase = true), clazz.name))
      case (range, clazz) if ValueClassType.extendsAnyVal(clazz) =>
        (range, ScalaBundle.message("illegal.inheritance.from.value.class", clazz.name))
    }.foreach {
      case (range, message) =>
        holder.createErrorAnnotation(range, message)
    }
  }

  private def annotateIllegalInheritance(holder: AnnotationHolder): Unit = {
    selfTypeElement.flatMap(_.`type`().toOption).
      orElse(`type`().toOption)
      .foreach { ownType =>

        collectSuperRefs(this) {
          _.extractClassType
        }.foreach {
          case (range, (clazz: ScTemplateDefinition, substitutor)) =>
            clazz.selfType.filterNot { selfType =>
              ownType.conforms(substitutor(selfType))
            }.foreach { selfType =>
              holder.createErrorAnnotation(range, ScalaBundle.message("illegal.inheritance.self.type", ownType.presentableText, selfType.presentableText))
            }
          case _ =>
        }
      }
  }

  // TODO package private
  def annotateObjectCreationImpossible(holder: AnnotationHolder) {
    val isNew = isInstanceOf[ScNewTemplateDefinition]
    val isObject = isInstanceOf[ScObject]

    if (!isNew && !isObject) return

    val refs = superRefs(this)

    val hasAbstract = refs.exists {
      case (_, clazz) => isAbstract(clazz)
    }

    if (hasAbstract) {
      refs match {
        case (defaultRange, _) :: _ =>
          val undefined = for {
            member <- ScalaOIUtil.getMembersToImplement(this)
            if member.isInstanceOf[ScalaTypedMember] // See SCL-2887
          } yield {
            try {
              (member.getText, member.getParentNodeDelegate.getText)
            } catch {
              case iae: IllegalArgumentException =>
                throw new RuntimeException("member: " + member.getText, iae)
            }
          }

          if (undefined.nonEmpty) {
            val range = this match {
              case _: ScNewTemplateDefinition => defaultRange
              case scalaObject: ScObject => scalaObject.nameId.getTextRange
            }

            val annotation = holder.createErrorAnnotation(range, objectCreationImpossibleMessage(undefined.toSeq: _*))
            annotation.registerFix(new ImplementMethodsQuickFix(this))
          }
        case _ =>
      }
    }
  }

  private def annotateMultipleInheritance(holder: AnnotationHolder): Unit = {
    superRefs(this).groupBy(_._2).flatMap {
      case (clazz, entries) if isMixable(clazz) && entries.size > 1 => entries.map {
        case (range, _) => (range, ScalaBundle.message("illegal.inheritance.multiple", kindOf(clazz), clazz.name))
      }
      case _ => Seq.empty
    }.foreach {
      case (range, message) =>
        holder.createErrorAnnotation(range, message)
    }
  }

  private def annotateNeedsToBeTrait(holder: AnnotationHolder): Unit = superRefs(this) match {
    case _ :: tail =>
      tail.collect {
        case (range, clazz) if !isMixable(clazz) =>
          (range, ScalaBundle.message("illegal.mixin", kindOf(clazz), clazz.name))
      }.foreach {
        case (range, message) =>
          holder.createErrorAnnotation(range, message)
      }
    case _ =>
  }

  // TODO package private
  def annotateUndefinedMember(holder: AnnotationHolder): Unit = {
    val isNew = isInstanceOf[ScNewTemplateDefinition]
    val isObject = isInstanceOf[ScObject]

    if (!isNew && !isObject) return

    physicalExtendsBlock.members.foreach {
      case _: ScTypeAliasDeclaration => // abstract type declarations are allowed
      case declaration: ScDeclaration =>
        val isNative = declaration match {
          case a: ScAnnotationsHolder => a.hasAnnotation("scala.native")
          case _ => false
        }
        if (!isNative) holder.createErrorAnnotation(declaration, ScalaBundle.message("illegal.undefined.member"))
      case _ =>
    }
  }

  // TODO test
  private def annotateSealedclassInheritance(holder: AnnotationHolder): Unit = getContainingFile match {
    case file: ScalaFile if !file.isCompiled =>
      val references = this match {
        case templateDefinition: ScNewTemplateDefinition if templateDefinition.extendsBlock.templateBody.isEmpty => Nil
        case _ => superRefs(this)
      }
      val fileNavigationElement = file.getNavigationElement

      references.collect {
        case (range, definition@ErrorAnnotationMessage(message))
          if definition.getContainingFile.getNavigationElement != fileNavigationElement =>
          (range, message)
      }.foreach {
        case (range, message) =>
          holder.createErrorAnnotation(range, message)
      }
    case _ =>
  }

  // TODO package private
  def annotateNeedsToBeAbstract(holder: AnnotationHolder, typeAware: Boolean): Unit = this match {
    case _: ScNewTemplateDefinition | _: ScObject =>
    case _ if !typeAware || isAbstract(this) =>
    case _ =>
      ScalaOIUtil.getMembersToImplement(this, withOwn = true).collectFirst {
        case member: ScalaTypedMember /* SCL-2887 */ =>
          ScalaBundle.message(
            "member.implementation.required",
            kindOf(this),
            name,
            member.getText,
            member.getParentNodeDelegate.getText)
      }.foreach { message =>
        val nameId = this.nameId
        val fixes = {
          val maybeModifierFix = this match {
            case owner: ScModifierListOwner => Some(new ModifierQuickFix.Add(owner, nameId, lexer.ScalaModifier.Abstract))
            case _ => None
          }

          val maybeImplementFix = if (ScalaOIUtil.getMembersToImplement(this).nonEmpty) Some(new ImplementMethodsQuickFix(this))
          else None

          maybeModifierFix ++ maybeImplementFix

        }
        val annotation = holder.createErrorAnnotation(nameId, message)
        fixes.foreach(annotation.registerFix)
      }
  }

  private def annotateNeedsToBeMixin(holder: AnnotationHolder): Unit = {
    if (isInstanceOf[ScTrait]) return

    val signatures = TypeDefinitionMembers.getSignatures(this)
      .forAllNames()
      .flatMap {
        _.map(_._2)
      }

    def isOverrideAndAbstract(definition: ScFunctionDefinition) =
      definition.hasModifierPropertyScala(PsiModifier.ABSTRACT) &&
        definition.hasModifierPropertyScala("override")

    for (signature <- signatures) {
      signature.info match {
        case PhysicalSignature(function: ScFunctionDefinition, _) if isOverrideAndAbstract(function) =>
          val flag = signature.supers.map(_.info.namedElement).forall {
            case f: ScFunctionDefinition => isOverrideAndAbstract(f)
            case _: ScBindingPattern => true
            case m: PsiMethod => m.hasModifierProperty(PsiModifier.ABSTRACT)
            case _ => true
          }

          for {
            place <- this match {
              case _ if !flag => None
              case typeDefinition: ScTypeDefinition => Some(typeDefinition.nameId)
              case templateDefinition: ScNewTemplateDefinition =>
                templateDefinition.extendsBlock.templateParents
                  .flatMap(_.typeElements.headOption)
              case _ => None
            }

            message = ScalaBundle.message(
              "mixin.required",
              kindOf(this),
              name,
              function.name,
              function.containingClass.name
            )
          } holder.createErrorAnnotation(place, message)
        case _ => //todo: vals?
      }
    }
  }
}

// TODO make package-private, or (even better), write messages in the test as they are
object ScTemplateDefinitionAnnotator {
  def objectCreationImpossibleMessage(members: (String, String)*): String =
    s"Object creation impossible, since " + members.map {
      case (first, second) => s" member $first in $second is not defined"
    }.mkString("; ")
}
