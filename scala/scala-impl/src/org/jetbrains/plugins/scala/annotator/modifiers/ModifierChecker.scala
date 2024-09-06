package org.jetbrains.plugins.scala.annotator.modifiers

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.quickfix.ModifierQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.{ScalaModifier, ScalaModifierTokenType}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScModifierList}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaration, ScExtensionBody, ScPatternDefinition, ScTypeAlias, ScValueDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner, ScPackaging}

import scala.collection.mutable

private[annotator] object ModifierChecker {

  import ScalaModifier._

  private val IllegalCombinations: Seq[(ScalaModifier, ScalaModifier)] = {
    val modifiers = Seq(
      (Final, Sealed),
      (Private, Protected),
      (Final, Open),
      (Sealed, Open),
      (Lazy, Inline),
      //NOTE: there are other illegal combinations,
      //which is expressed with more complex logic and which provide more detailed messages
      //Such illegal combinations are not checked by this class
    )
    val modifiersSwapped = modifiers.map { case (left, right) => (right, left) }
    modifiers ++ modifiersSwapped
  }

  def checkModifiers(modifierList: ScModifierList)
                    (implicit holder: ScalaAnnotationHolder): Unit = modifierList.getParent match {
    case owner: ScModifierListOwner =>
      val modifiers = mutable.HashSet.empty[ScalaModifier]

      def checkIllegalCombinations(modifierElement: PsiElement, modifier: ScalaModifier): Boolean = {
        val maybeIllegalModifier = IllegalCombinations.collectFirst {
          case (`modifier`, illegalModifier) if owner.hasModifierPropertyScala(illegalModifier.text()) => illegalModifier
        }.orElse {
          if (modifiers.add(modifier)) None else Some(modifier)
        }

        for {
          illegalModifier <- maybeIllegalModifier
        } {
          modifiers.add(illegalModifier)
          val message = ScalaBundle.message("illegal.modifiers.combination", modifier.text(), illegalModifier.text())
          createErrorWithQuickFix(message, modifierElement, owner)
        }

        maybeIllegalModifier.isEmpty
      }

      val modifierNodes = modifierList.getNode.getChildren(null)
      for (modifierNode <- modifierNodes) {
        modifierNode.getPsi match {
          case accessModifier: ScAccessModifier => // todo: check private with final or sealed combination.
            val maybeModifier = if (accessModifier.isPrivate) Some(Private) else if (accessModifier.isProtected) Some(Protected) else None
            maybeModifier.foreach { modifier =>
              checkIllegalCombinations(accessModifier, modifier)
              if (owner.getContext.is[ScBlock]) {
                createErrorWithQuickFix(
                  ScalaBundle.message("access.modifier.is.not.allowed.here", modifier.text()),
                  accessModifier,
                  owner,
                )
              }
            }
          case modifierPsi if modifierPsi.getNode.getElementType.is[ScalaModifierTokenType] =>
            modifierNode.getText match {
              case LAZY =>
                owner match {
                  case _: ScPatternDefinition => checkIllegalCombinations(modifierPsi, Lazy)
                  case _: ScParameter =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("lazy.modifier.is.not.allowed.with.param"),
                      modifierPsi,
                      owner,
                    )
                  case _: ScValueDeclaration =>
                    if (!modifierList.isInScala3File) {
                      createErrorWithQuickFix(
                        ScalaBundle.message("lazy.values.may.not.be.abstract"),
                        modifierPsi,
                        owner,
                      )
                    }
                  case _ =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("lazy.modifier.is.not.allowed.here"),
                      modifierPsi,
                      owner,
                    )
                }
              case FINAL =>
                owner match {
                  case d: ScDeclaration if !d.hasAnnotation("scala.native") =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("final.modifier.not.with.declarations"),
                      modifierPsi,
                      owner,
                    )
                  case _: ScTrait =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("final.modifier.not.with.trait"),
                      modifierPsi,
                      owner,
                    )
                  case _: ScClass => checkIllegalCombinations(modifierPsi, Final)
                  case _: ScObject => checkIllegalCombinations(modifierPsi, Final)
                  case e: ScMember if e.getParent.is[ScTemplateBody, ScEarlyDefinitions] =>
                    val redundant = (e.containingClass, e) match {
                      case (_, valMember: ScPatternDefinition) if valMember.typeElement.isEmpty &&
                        valMember.pList.simplePatterns => false // constant value definition, see SCL-11500
                      case (cls, _) if cls.hasFinalModifier => true
                      case _ => false
                    }
                    if (redundant) {
                      if (checkIllegalCombinations(modifierPsi, Final)) {
                        createWarningWithQuickFix(
                          ScalaBundle.message("final.modifier.is.redundant.with.final.parents"),
                          modifierPsi,
                          owner,
                        )
                      }
                    } else {
                      checkIllegalCombinations(modifierPsi, Final)
                    }
                  case e: ScMember if e.getParent.is[ScalaFile] =>
                    checkIllegalCombinations(modifierPsi, Final)
                  case e: ScClassParameter =>
                    if (PsiTreeUtil.getParentOfType(e, classOf[ScTypeDefinition]).hasFinalModifier) {
                      if (checkIllegalCombinations(modifierPsi, Final)) {
                        createWarningWithQuickFix(
                          ScalaBundle.message("final.modifier.is.redundant.with.final.parents"),
                          modifierPsi,
                          owner,
                        )
                      }
                    } else {
                      checkIllegalCombinations(modifierPsi, Final)
                    }
                  case _ =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("final.modifier.is.not.allowed.here"),
                      modifierPsi,
                      owner,
                    )
                }
              case SEALED =>
                owner match {
                  case _: ScClass | _: ScTrait | _: ScClassParameter => checkIllegalCombinations(modifierPsi, Sealed)
                  case _ =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("sealed.modifier.is.not.allowed.here"),
                      modifierPsi,
                      owner,
                    )
                }
              case ABSTRACT =>
                owner match {
                  case _: ScClass => checkIllegalCombinations(modifierPsi, Abstract)
                  case _: ScTrait => if (checkIllegalCombinations(modifierPsi, Abstract)) {
                    createWarningWithQuickFix(
                      ScalaBundle.message("abstract.modifier.redundant.fot.traits"),
                      modifierPsi,
                      owner,
                    )
                  }
                  case member: ScMember if !member.isInstanceOf[ScTemplateBody] &&
                    member.getParent.is[ScTemplateBody] && owner.hasModifierPropertyScala(OVERRIDE) =>
                    // 'abstract override' modifier only allowed for members of traits
                    if (!member.containingClass.is[ScTrait]) {
                      createErrorWithQuickFix(
                        ScalaBundle.message("abstract.override.modifier.is.not.allowed"),
                        modifierPsi,
                        owner,
                      )
                    } else {
                      checkIllegalCombinations(modifierPsi, Abstract)
                    }
                  case _ =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("abstract.modifier.is.not.allowed"),
                      modifierPsi,
                      owner,
                    )
                }
              case OVERRIDE =>
                owner match {
                  case o: ScObject if o.containingClass != null => //allowed
                  case _: ScTypeDefinition =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("override.modifier.is.not.allowed.for.classes"),
                      modifierPsi,
                      owner,
                    )
                  case member: ScMember if member.getParent.is[ScTemplateBody, ScEarlyDefinitions, ScExtensionBody] =>
                    checkIllegalCombinations(modifierPsi, Override)
                  case _: ScClassParameter =>
                    checkIllegalCombinations(modifierPsi, Override)
                  case _ =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("override.modifier.is.not.allowed"),
                      modifierPsi,
                      owner,
                    )
                }
              case IMPLICIT =>
                owner match {
                  case c@(_: ScClass | _: ScObject) =>
                    val onTopLevel = c.getContext match {
                      case file: ScalaFile if !file.isWorksheetFile => true
                      case _: ScPackaging => true
                      case _ => false
                    }
                    if (onTopLevel && !owner.isInScala3File) {
                      createErrorWithQuickFix(
                        ScalaBundle.message("implicit.modifier.cannot.be.used.for.top.level.objects"),
                        modifierPsi,
                        owner,
                      )
                    } else
                      c match {
                        case clazz: ScClass =>
                          val error = !hasPrimaryConstructorWithExactlyOneParameterInFirstClause(clazz)
                          if (error) {
                            createErrorWithQuickFix(
                              ScalaBundle.message("implicit.class.must.have.a.primary.constructor.with.one.argument"),
                              modifierPsi,
                              owner,
                            )
                          }
                          if (clazz.hasModifierPropertyScala(ABSTRACT)) {
                            createErrorWithQuickFix(
                              ScalaBundle.message("class.is.abstract.it.cannot.be.instantiated", clazz.name),
                              modifierPsi,
                              owner,
                            )
                          }
                        case _ =>
                      }
                  case _: ScTrait | _: ScTypeAlias =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("implicit.modifier.can.be.used.only.for"),
                      modifierPsi,
                      owner,
                    )
                  case _ => checkIllegalCombinations(modifierPsi, Implicit)
                }
              case OPEN =>
                val (isRedundant, isValidUsage) = owner match {
                  case c: ScClass => (c.hasModifierPropertyScala(ABSTRACT), true)
                  case _: ScTrait => (true, true)
                  //note: compiler doesn't show warning for `open object` but it's probably a bug, cause it doesn't make any sense
                  case _: ScObject => (true, true)
                  case _ => (false, false)
                }
                if (isRedundant) {
                  createWarningWithQuickFix(
                    ScalaBundle.message("modifier.is.redundant.for.this.definition", OPEN),
                    modifierPsi,
                    owner,
                  )
                }
                else if (!isValidUsage) {
                  createErrorWithQuickFix(
                    ScalaBundle.message("only.classes.can.be.open"),
                    modifierPsi,
                    owner,
                  )
                }
                else {
                  checkIllegalCombinations(modifierPsi, Open)
                }
              case OPAQUE =>
                owner match {
                  case _: ScTypeAlias => //ok
                  case _ =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("opaque.modifier.allowed.only.for.type.aliases"),
                      modifierPsi,
                      owner,
                    )
                }
              case other =>
                val otherModifier = ScalaModifier.byText(other)
                if (otherModifier != null) {
                  checkIllegalCombinations(modifierPsi, otherModifier)
                }
            }
          case _ => //e.g. whitespace
        }
      }
    case _ =>
  }

  private def hasPrimaryConstructorWithExactlyOneParameterInFirstClause(clazz: ScClass): Boolean =
    clazz.constructor.exists { constr =>
      val clauses = constr.parameterList.clauses
      if (clauses.isEmpty) false
      else {
        val firstClauseParameters = clauses.head.parameters
        val firstClauseHasSingleParameter =
          firstClauseParameters.length == 1 && !firstClauseParameters.head.isRepeatedParameter

        firstClauseHasSingleParameter
      }
    }

  private def createWarningWithQuickFix(
    @Nls message: String,
    modifierElement: PsiElement,
    owner: ScModifierListOwner,
  )(implicit holder: ScalaAnnotationHolder): Unit = {
    holder.createWarningAnnotation(modifierElement, message, new ModifierQuickFix.RemoveSpecific(owner, modifierElement))
  }

  private def createErrorWithQuickFix(
    @Nls message: String,
    modifierElement: PsiElement,
    owner: ScModifierListOwner,
  )(implicit holder: ScalaAnnotationHolder): Unit = {
    holder.createErrorAnnotation(modifierElement, message, new ModifierQuickFix.RemoveSpecific(owner, modifierElement))
  }
}
