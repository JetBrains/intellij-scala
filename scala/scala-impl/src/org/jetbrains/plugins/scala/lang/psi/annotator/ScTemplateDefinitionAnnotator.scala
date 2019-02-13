package org.jetbrains.plugins.scala.lang.psi.annotator

import com.intellij.lang.annotation.AnnotationHolder
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.quickfix.ImplementMethodsQuickFix
import org.jetbrains.plugins.scala.annotator.template._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.annotator.ScTemplateDefinitionAnnotator.objectCreationImpossibleMessage
import org.jetbrains.plugins.scala.lang.psi.api.Annotatable
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaration, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition}
import org.jetbrains.plugins.scala.lang.psi.types.ValueClassType
import org.jetbrains.plugins.scala.overrideImplement.{ScalaOIUtil, ScalaTypedMember}

trait ScTemplateDefinitionAnnotator extends Annotatable { self: ScTemplateDefinition =>

  abstract override def annotate(holder: AnnotationHolder, typeAware: Boolean): Unit = {
    super.annotate(holder, typeAware)

    annotateFinalClassInheritance(holder)
    annotateMultipleInheritance(holder)
    annotateNeedsToBeTrait(holder)
    annotateUndefinedMember(holder)

    if (typeAware) {
      annotateIllegalInheritance(holder)
      annotateObjectCreationImpossible(holder)
    }
  }

  private def annotateFinalClassInheritance(holder: AnnotationHolder): Unit = {
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

  private def annotateObjectCreationImpossible(holder: AnnotationHolder) {
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

  private def annotateUndefinedMember(holder: AnnotationHolder): Unit = {
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
}

// TODO make package-private, or (even better), write messages in the test as they are
object ScTemplateDefinitionAnnotator {
  def objectCreationImpossibleMessage(members: (String, String)*): String =
    s"Object creation impossible, since " + members.map {
      case (first, second) => s" member $first in $second is not defined"
    }.mkString("; ")
}
