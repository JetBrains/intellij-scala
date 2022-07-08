package org.jetbrains.plugins.scala
package annotator
package modifiers

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.annotator.quickfix.ModifierQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
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

  private val IllegalCombinations = {
    val modifiers = Seq(
      (Final, Sealed),
      (Private, Protected)
    )
    modifiers ++ modifiers.map {
      case (left, right) => (right, left)
    }
  }

  def checkModifiers(modifierList: ScModifierList)
                    (implicit holder: ScalaAnnotationHolder): Unit = modifierList.getParent match {
    case owner: ScModifierListOwner =>
      val modifiers = mutable.HashSet.empty[ScalaModifier]

      def checkDuplicates(element: PsiElement, modifier: ScalaModifier): Boolean = {
        val maybeIllegalModifier = IllegalCombinations.collectFirst {
          case (`modifier`, illegalModifier) if owner.hasModifierPropertyScala(illegalModifier.text()) => illegalModifier
        }.orElse {
          if (modifiers.add(modifier)) None else Some(modifier)
        }

        for {
          illegalModifier <- maybeIllegalModifier
          message = ScalaBundle.message("illegal.modifiers.combination", modifier.text(), illegalModifier.text())
        } createErrorWithQuickFix(message, element, owner, modifier)

        maybeIllegalModifier.isEmpty
      }

      for (modifier <- modifierList.getNode.getChildren(null)) {
        modifier.getPsi match {
          case accessModifier: ScAccessModifier => // todo: check private with final or sealed combination.
            val maybeModifier = if (accessModifier.isPrivate) Some(Private) else if (accessModifier.isProtected) Some(Protected) else None
            maybeModifier.foreach { modifier =>
              checkDuplicates(accessModifier, modifier)
              if (owner.getContext.is[ScBlock]) {
                createErrorWithQuickFix(
                  ScalaBundle.message("access.modifier.is.not.allowed.here", modifier.text()),
                  accessModifier,
                  owner,
                  modifier
                )
              }
            }
          case modifierPsi =>
            modifier.getText match {
              case LAZY =>
                owner match {
                  case _: ScPatternDefinition => checkDuplicates(modifierPsi, Lazy)
                  case _: ScParameter =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("lazy.modifier.is.not.allowed.with.param"),
                      modifierPsi,
                      owner,
                      Lazy
                    )
                  case _: ScValueDeclaration =>
                    if (!modifierList.isInScala3File) {
                      createErrorWithQuickFix(
                        ScalaBundle.message("lazy.values.may.not.be.abstract"),
                        modifierPsi,
                        owner,
                        Lazy
                      )
                    }
                  case _ =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("lazy.modifier.is.not.allowed.here"),
                      modifierPsi,
                      owner,
                      Lazy
                    )
                }
              case FINAL =>
                owner match {
                  case d: ScDeclaration if !d.hasAnnotation("scala.native") =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("final.modifier.not.with.declarations"),
                      modifierPsi,
                      owner,
                      Final
                    )
                  case _: ScTrait =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("final.modifier.not.with.trait"),
                      modifierPsi,
                      owner,
                      Final
                    )
                  case _: ScClass => checkDuplicates(modifierPsi, Final)
                  case _: ScObject => checkDuplicates(modifierPsi, Final)
                  case e: ScMember if e.getParent.is[ScTemplateBody, ScEarlyDefinitions] =>
                    val redundant = (e.containingClass, e) match {
                      case (_, valMember: ScPatternDefinition) if valMember.typeElement.isEmpty &&
                        valMember.pList.simplePatterns => false // constant value definition, see SCL-11500
                      case (cls, _) if cls.hasFinalModifier => true
                      case _ => false
                    }
                    if (redundant) {
                      if (checkDuplicates(modifierPsi, Final)) {
                        createWarningWithQuickFix(
                          ScalaBundle.message("final.modifier.is.redundant.with.final.parents"),
                          modifierPsi,
                          owner,
                          Final
                        )
                      }
                    } else {
                      checkDuplicates(modifierPsi, Final)
                    }
                  case e: ScMember if e.getParent.is[ScalaFile] =>
                    checkDuplicates(modifierPsi, Final)
                  case e: ScClassParameter =>
                    if (PsiTreeUtil.getParentOfType(e, classOf[ScTypeDefinition]).hasFinalModifier) {
                      if (checkDuplicates(modifierPsi, Final)) {
                        createWarningWithQuickFix(
                          ScalaBundle.message("final.modifier.is.redundant.with.final.parents"),
                          modifierPsi,
                          owner,
                          Final
                        )
                      }
                    } else {
                      checkDuplicates(modifierPsi, Final)
                    }
                  case _ =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("final.modifier.is.not.allowed.here"),
                      modifierPsi,
                      owner,
                      Final
                    )
                }
              case SEALED =>
                owner match {
                  case _: ScClass | _: ScTrait | _: ScClassParameter => checkDuplicates(modifierPsi, Sealed)
                  case _ =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("sealed.modifier.is.not.allowed.here"),
                      modifierPsi,
                      owner,
                      Sealed
                    )
                }
              case ABSTRACT =>
                owner match {
                  case _: ScClass => checkDuplicates(modifierPsi, Abstract)
                  case _: ScTrait => if (checkDuplicates(modifierPsi, Abstract)) {
                    createWarningWithQuickFix(
                      ScalaBundle.message("abstract.modifier.redundant.fot.traits"),
                      modifierPsi,
                      owner,
                      Abstract
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
                        Abstract
                      )
                    } else {
                      checkDuplicates(modifierPsi, Abstract)
                    }
                  case _ =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("abstract.modifier.is.not.allowed"),
                      modifierPsi,
                      owner,
                      Abstract
                    )
                }
              case "override" =>
                owner match {
                  case o: ScObject if o.containingClass != null => //allowed
                  case _: ScTypeDefinition =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("override.modifier.is.not.allowed.for.classes"),
                      modifierPsi,
                      owner,
                      Override
                    )
                  case member: ScMember if member.getParent.is[ScTemplateBody, ScEarlyDefinitions, ScExtensionBody] =>
                    checkDuplicates(modifierPsi, Override)
                  case _: ScClassParameter => checkDuplicates(modifierPsi, Override)
                  case _ =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("override.modifier.is.not.allowed"),
                      modifierPsi,
                      owner,
                      Override
                    )
                }
              case IMPLICIT =>
                owner match {
                  case c@(_: ScClass | _: ScObject) =>
                    val onTopLevel = c.getContext match {
                      case file: ScalaFile if !file.isScriptFile && !file.isWorksheetFile => true
                      case _: ScPackaging => true
                      case _ => false
                    }
                    if (onTopLevel && !owner.isInScala3Module) {
                      createErrorWithQuickFix(
                        ScalaBundle.message("implicit.modifier.cannot.be.used.for.top.level.objects"),
                        modifierPsi,
                        owner,
                        Implicit
                      )
                    } else
                      c match {
                        case clazz: ScClass =>

                          def errorResult(): Unit = createErrorWithQuickFix(
                            ScalaBundle.message("implicit.class.must.have.a.primary.constructor.with.one.argument"),
                            modifierPsi,
                            owner,
                            Implicit
                          )

                          clazz.constructor match {
                            case Some(constr) =>
                              val clauses = constr.parameterList.clauses
                              if (clauses.isEmpty) errorResult()
                              else {
                                val parameters = clauses.head.parameters
                                if (parameters.length != 1) errorResult()
                                else if (parameters.head.isRepeatedParameter) errorResult()
                                else if (clauses.length > 2 || (clauses.length == 2 && !clauses(1).isImplicit)) errorResult()
                              }
                            case _ => errorResult()
                          }
                        case _ =>
                      }
                  case _: ScTrait | _: ScTypeAlias =>
                    createErrorWithQuickFix(
                      ScalaBundle.message("implicit.modifier.can.be.used.only.for"),
                      modifierPsi,
                      owner,
                      Implicit
                    )
                  case _ => checkDuplicates(modifierPsi, Implicit)
                }
              case _ =>
            }
        }
      }
    case _ =>
  }

  private def createWarningWithQuickFix(@Nls message: String, element: PsiElement,
                               owner: ScModifierListOwner, modifier: ScalaModifier)
                              (implicit holder: ScalaAnnotationHolder): Unit = {
    holder.createWarningAnnotation(element, message, new ModifierQuickFix.Remove(owner, null, modifier))
  }

  private def createErrorWithQuickFix(@Nls message: String, element: PsiElement,
                                        owner: ScModifierListOwner, modifier: ScalaModifier)
                                       (implicit holder: ScalaAnnotationHolder): Unit = {
    holder.createErrorAnnotation(element, message, new ModifierQuickFix.Remove(owner, null, modifier))
  }
}
