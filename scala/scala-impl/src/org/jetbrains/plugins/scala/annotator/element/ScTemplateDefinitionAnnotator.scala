package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiClass, PsiMethod, PsiModifier, PsiModifierList}
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.{NlsString, ScalaBundle, overrideImplement}
import org.jetbrains.plugins.scala.annotator.AnnotatorUtils.ErrorAnnotationMessage
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.quickfix.{ImplementMembersQuickFix, ModifierQuickFix}
import org.jetbrains.plugins.scala.annotator.template._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScBindingPattern
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAnnotationsHolder, ScPrimaryConstructor, ScStableCodeReference}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScNewTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaration, ScEnumCase, ScFunction, ScFunctionDefinition, ScTypeAliasDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.types.{PhysicalMethodSignature, TypePresentationContext, ValueClassType}
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveResult
import org.jetbrains.plugins.scala.overrideImplement.{ScMethodMember, ScalaOIUtil, ScalaTypedMember}
import org.jetbrains.plugins.scala.extensions.&

import scala.util.chaining._

object ScTemplateDefinitionAnnotator extends ElementAnnotator[ScTemplateDefinition] {

  override def annotate(element: ScTemplateDefinition, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit = {
    annotateFinalClassInheritance(element)
    annotateMultipleInheritance(element)
    annotateNeedsToBeTrait(element)
    annotateUndefinedMember(element)
    annotateSealedclassInheritance(element)
    annotateEnumClassInheritance(element)
    annotateTraitPassingConstructorParameters(element)
    annotateParentTraitConstructorParameters(element)

    if (typeAware) {
      annotateNeedsToBeMixin(element)
      annotateNeedsToBeAbstract(element)
      annotateIllegalInheritance(element)
      annotateObjectCreationImpossible(element)
      annotateEnumCaseCreationImpossible(element)
    }
  }

  /**
   * 1. If a class C extends a parameterized trait T, and its superclass does not, C must pass arguments to T.
   * 2. If a class C extends a parameterized trait T, and its superclass does as well, C must not pass arguments to T.
   */
  private def annotateParentTraitConstructorParameters(
    tdef: ScTemplateDefinition
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    def resolveNoCons(ref: ScStableCodeReference): Option[ScalaResolveResult] =
      ref.resolveNoConstructor match {
        case Array(srr) => srr.toOption
        case _          => None
      }

    val superClass           = tdef.superClass
    val directSupers         = tdef.extendsBlock.templateParents.toSeq.flatMap(_.parentClauses)
    val directSupersBuilder  = Set.newBuilder[ScTrait]
    val supers               = tdef.allSupers.toSet

    directSupers.collect {
      case parentClause =>
        val resolvedSuper = parentClause.reference.flatMap(resolveNoCons)

        resolvedSuper.collect {
          case ScalaResolveResult(superTrait: ScTrait, _) if parentClause.args.nonEmpty =>
            directSupersBuilder += superTrait
            superClass.foreach { cls =>
              if (ScalaPsiUtil.isInheritorDeep(cls, superTrait))
                holder.createErrorAnnotation(
                  parentClause,
                  ScalaBundle.message("trait.is.already.implemented.by.superclass", superTrait.name, cls.name)
                )
            }
        }
    }

    if (!tdef.is[ScTrait]) {
      def hasOnlyImplicitOrDefaultParameters(cons: ScPrimaryConstructor): Boolean =
        cons.parameters.forall(p => p.isImplicitOrContextParameter || p.isDefaultParam)

      val resolvedDirectSupers = directSupersBuilder.result()
      supers.collect {
        case (tr: ScTrait) & ScConstructorOwner.constructor(cons) =>
          val isDirectlyImplemented = resolvedDirectSupers.contains(tr)

          def isExtendedBySuperClass = superClass match {
            case Some(cls: PsiClass) => ScalaPsiUtil.isInheritorDeep(cls, tr)
            case _                   => false
          }

          val isOk = isDirectlyImplemented || hasOnlyImplicitOrDefaultParameters(cons) || isExtendedBySuperClass
          if (!isOk) {
            val anchor =
              if (tdef.is[ScNewTemplateDefinition]) tdef.getFirstChild
              else                                  tdef.nameId

            holder.createErrorAnnotation(
              anchor,
              ScalaBundle.message("parameterised.trait.is.implemented.indirectly", tr.name)
            )
          }
      }
    }
  }

  /**
   * 3.Traits must never pass arguments to parent traits.
   */
  private def annotateTraitPassingConstructorParameters(
    tdef: ScTemplateDefinition
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit =
    if (tdef.is[ScTrait])
      for {
        templateParents <- tdef.extendsBlock.templateParents.toSeq
        clause          <- templateParents.parentClauses
        if clause.args.nonEmpty
      } holder.createErrorAnnotation(
        clause,
        ScalaBundle.message("trait.may.not.call.constructor", tdef.name, clause.typeElement.getText)
      )

  private def annotateEnumClassInheritance(
    tdef: ScTemplateDefinition
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = {
    superRefs(tdef).collect {
      case (range, enum: ScEnum) =>
        tdef match {
          case cse: ScEnumCase if cse.enumParent eq enum => ()
          case _ =>
            holder.createErrorAnnotation(range, ScalaBundle.message("illegal.inheritance.extends.enum"))
        }
    }
  }

  // TODO package private
  def annotateFinalClassInheritance(element: ScTemplateDefinition)
                                   (implicit holder: ScalaAnnotationHolder): Unit = {
    val newInstance = element.is[ScNewTemplateDefinition]
    val hasBody = element.extendsBlock.templateBody.isDefined

    if (newInstance && !hasBody) return

    superRefs(element).collect {
      case (range, clazz) if clazz.hasFinalModifier =>
        (range, NlsString(ScalaBundle.message("illegal.inheritance.from.final.kind", kindOf(clazz, toLowerCase = true), clazz.name)))
      case (range, clazz) if ValueClassType.extendsAnyVal(clazz) =>
        (range, NlsString(ScalaBundle.message("illegal.inheritance.from.value.class", clazz.name)))
    }.foreach {
      case (range, message) =>
        holder.createErrorAnnotation(range, message.nls)
    }
  }

  def annotateIllegalInheritance(element: ScTemplateDefinition)
                                (implicit holder: ScalaAnnotationHolder): Unit = {
    implicit val tpc: TypePresentationContext = TypePresentationContext(element)
    element.selfTypeElement.flatMap(_.`type`().toOption).
      orElse(element.`type`().toOption)
      .foreach { ownType =>

        collectSuperRefs(element) {
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

  def annotateEnumCaseCreationImpossible(element: ScTemplateDefinition)
                                        (implicit holder: ScalaAnnotationHolder): Unit = {
    val enumCase = element.asOptionOf[ScEnumCase].getOrElse(return)
    val membersToImplement = ScalaOIUtil.getMembersToImplement(enumCase)
      .filter {
        case m: ScalaTypedMember => m.name != "ordinal" // See SCL-2887
        case _                   => false
      }

    if (membersToImplement.isEmpty) return

    val enumParents = enumCase.enumParent.getSupers.toSet

    val (canBeImplementedInEnum, cannotBeImplemented) = membersToImplement.partition {
      case ScMethodMember(signature, _) =>
        enumParents.contains(signature.method.containingClass)
      case _ => false
    }

    val range = highlightRange(enumCase)

    if (canBeImplementedInEnum.nonEmpty) {
      holder.createErrorAnnotation(
        range,
        objectCreationImpossibleMessage(canBeImplementedInEnum.map(formatForObjectCreationImpossibleMessage): _*),
        new ImplementMembersQuickFix(enumCase.enumParent)
      )
    }

    if (cannotBeImplemented.nonEmpty) {
      holder.createErrorAnnotation(
        range,
        objectCreationImpossibleMessage(cannotBeImplemented.map(formatForObjectCreationImpossibleMessage): _*),
        None
      )
    }
  }

  // TODO package private
  def annotateObjectCreationImpossible(element: ScTemplateDefinition)
                                      (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!element.is[ScNewTemplateDefinition, ScObject]) return

    val refs = superRefs(element)

    val hasAbstract = refs.exists {
      case (_, clazz) => isAbstract(clazz)
    }

    if (hasAbstract) {
      refs match {
        case (defaultRange, _) :: _ =>
          val undefined = for {
            member <- ScalaOIUtil.getMembersToImplement(element)
            if member.isInstanceOf[ScalaTypedMember] // See SCL-2887
          } yield formatForObjectCreationImpossibleMessage(member)

          if (undefined.nonEmpty) {
            val range = element match {
              case _: ScNewTemplateDefinition => defaultRange
              case _: ScObject => highlightRange(element)
            }

            holder.createErrorAnnotation(
              range,
              objectCreationImpossibleMessage(undefined: _*),
              new ImplementMembersQuickFix(element)
            )
          }
        case _ =>
      }
    }
  }

  def annotateMultipleInheritance(element: ScTemplateDefinition)
                                 (implicit holder: ScalaAnnotationHolder): Unit = {
    superRefs(element).groupBy(_._2).flatMap {
      case (clazz, entries) if isMixable(clazz) && entries.size > 1 => entries.map {
        case (range, _) => (range, NlsString(ScalaBundle.message("illegal.inheritance.multiple", kindOf(clazz), clazz.name)))
      }
      case _ => Seq.empty
    }.foreach {
      case (range, message) =>
        holder.createErrorAnnotation(range, message.nls)
    }
  }

  def annotateNeedsToBeTrait(element: ScTemplateDefinition)
                            (implicit holder: ScalaAnnotationHolder): Unit = superRefs(element) match {
    case _ :: tail =>
      tail.collect {
        case (range, clazz) if !isMixable(clazz) =>
          (range, NlsString(ScalaBundle.message("illegal.mixin", kindOf(clazz), clazz.name)))
      }.foreach {
        case (range, message) =>
          //noinspection ReferencePassedToNls
          holder.createErrorAnnotation(range, message.nls)
      }
    case _ =>
  }

  // TODO package private
  def annotateUndefinedMember(element: ScTemplateDefinition)
                             (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!element.is[ScNewTemplateDefinition, ScObject]) return

    element.extendsBlock.members.foreach {
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
  private def annotateSealedclassInheritance(
    element: ScTemplateDefinition
  )(implicit
    holder: ScalaAnnotationHolder
  ): Unit = element.getContainingFile match {
      case file: ScalaFile if !file.isCompiled =>
        val references = element match {
          case templateDefinition: ScNewTemplateDefinition if templateDefinition.extendsBlock.templateBody.isEmpty =>
            Nil
          case _ => superRefs(element)
        }
        val fileNavigationElement = file.getNavigationElement

        def isInDifferentFile(tdef: ScTemplateDefinition): Boolean =
          tdef.getContainingFile.getNavigationElement != fileNavigationElement

        references.collect {
          case (range, definition @ ErrorAnnotationMessage(message))
              if isInDifferentFile(definition) =>
            (range, message)
        }.foreach {
          case (range, message) =>
            holder.createErrorAnnotation(range, message.nls)
        }
      case _ =>
    }

  // TODO package private
  def annotateNeedsToBeAbstract(element: ScTemplateDefinition)
                               (implicit holder: ScalaAnnotationHolder): Unit = element match {
    case _: ScNewTemplateDefinition | _: ScObject | _: ScEnumCase | _: ScEnum =>
    case _ if isAbstract(element) =>
    case _ =>
      ScalaOIUtil.getMembersToImplement(element, withOwn = true).collectFirst {
        case member: ScalaTypedMember /* SCL-2887 */ =>
          NlsString(
            ScalaBundle.message(
              "member.implementation.required",
              kindOf(element),
              element.name,
              member.getText,
              member.getParentNodeDelegate.getText
            )
          )
      }.foreach { message =>
        val nameId = element.nameId
        val fixes = {
          val maybeModifierFix = element match {
            case cls: ScClass => Some(new ModifierQuickFix.Add(cls, nameId, ScalaModifier.Abstract))
            case _ => None
          }

          val maybeImplementFix =
            Option.when(ScalaOIUtil.getMembersToImplement(element).nonEmpty)(new ImplementMembersQuickFix(element))

          maybeModifierFix ++ maybeImplementFix
        }
        holder.createErrorAnnotation(highlightRange(element), message.nls, fixes)
      }
  }

  def annotateNeedsToBeMixin(element: ScTemplateDefinition)
                            (implicit holder: ScalaAnnotationHolder): Unit = {
    if (element.is[ScTrait]) return

    val nodes = TypeDefinitionMembers.getSignatures(element).allNodes

    def isOverrideAndAbstract(definition: ScFunctionDefinition) =
      definition.hasModifierPropertyScala(PsiModifier.ABSTRACT) &&
        definition.hasModifierPropertyScala("override")

    for (node <- nodes) {
      node.info match {
        case PhysicalMethodSignature(function: ScFunctionDefinition, _) if isOverrideAndAbstract(function) =>
          val flag = node.supers.map(_.info.namedElement).forall {
            case f: ScFunctionDefinition => isOverrideAndAbstract(f)
            case _: ScBindingPattern => true
            case m: PsiMethod => m.hasModifierProperty(PsiModifier.ABSTRACT)
            case _ => true
          }

          for {
            place <- element match {
              case _ if !flag => None
              case typeDefinition: ScTypeDefinition => Some(typeDefinition.nameId)
              case templateDefinition: ScNewTemplateDefinition =>
                templateDefinition.extendsBlock.templateParents
                  .flatMap(_.typeElements.headOption)
              case _ => None
            }

            message = ScalaBundle.message(
              "mixin.required",
              kindOf(element),
              element.name,
              function.name,
              function.containingClass.name
            )
          } holder.createErrorAnnotation(place, message)
        case _ => //todo: vals?
      }
    }
  }

  @Nls
  def objectCreationImpossibleMessage(members: (String, String)*): String = {
    val reasons = members.map {
      case (first, second) => ScalaBundle.message("member.is.not.defined", first, second)
    }.mkString("; ")
    ScalaBundle.message("object.creation.impossible.since", reasons)
  }

  private def highlightRange(definition: ScTemplateDefinition): TextRange =
    TextRange.create(getHighlightingStartOffset(definition), getHighlightingEndOffset(definition))

  private def getHighlightingStartOffset(definition: ScTemplateDefinition): Int =
    definition match {
      case enumCase: ScEnumCase => enumCase.nameId.startOffset
      case _ =>
        definition.getModifierList
          .pipe { modifierList =>
            if (modifierList == null) definition.nameId.startOffset
            else stripAnnotationsFromModifierList(modifierList)
          }
    }

  private def getHighlightingEndOffset(definition: ScTemplateDefinition): Int = {
    val extendsBlock = definition.extendsBlock
    extendsBlock.templateBody
      .flatMap(_.prevElementNotWhitespace)
      .getOrElse(extendsBlock)
      .endOffset
  }

  private def stripAnnotationsFromModifierList(list: PsiModifierList): Int =
    list.getAnnotations
      .lastOption
      .flatMap(_.nextLeafs.filterNot(_.isWhitespaceOrComment).nextOption())
      .getOrElse(list)
      .startOffset

  private def formatForObjectCreationImpossibleMessage(member: overrideImplement.ClassMember): (String, String) =
    try {
      (member.getText, member.getParentNodeDelegate.getText)
    } catch {
      case iae: IllegalArgumentException =>
        throw new RuntimeException("member: " + member.getText, iae)
    }
}
