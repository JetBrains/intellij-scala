package org.jetbrains.plugins.scala
package annotator
package modifiers

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.annotations.Nls
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScAccessModifier, ScModifierList}
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScBlock
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.{ScClassParameter, ScParameter}
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaration, ScPatternDefinition, ScTypeAlias, ScValueDeclaration}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScEarlyDefinitions, ScModifierListOwner, ScPackaging}

import scala.collection.mutable

/**
  * @author Aleksander Podkhalyuzin
  * @since 25.03.2009
  */
private[annotator] object ModifierChecker {

  import HighlightSeverity.{ERROR, WARNING}
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
        } registerQuickFix(message, element, owner, modifier)

        maybeIllegalModifier.isEmpty
      }

      for (modifier <- modifierList.getNode.getChildren(null)) {
        modifier.getPsi match {
          case accessModifier: ScAccessModifier => // todo: check private with final or sealed combination.
            val maybeModifier = if (accessModifier.isPrivate) Some(Private) else if (accessModifier.isProtected) Some(Protected) else None
            maybeModifier.foreach { modifier =>
              checkDuplicates(accessModifier, modifier)
              if (owner.getContext.isInstanceOf[ScBlock]) {
                registerQuickFix(
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
                    registerQuickFix(
                      ScalaBundle.message("lazy.modifier.is.not.allowed.with.param"),
                      modifierPsi,
                      owner,
                      Lazy
                    )
                  case _: ScValueDeclaration =>
                    registerQuickFix(
                      ScalaBundle.message("lazy.values.may.not.be.abstract"),
                      modifierPsi,
                      owner,
                      Lazy
                    )
                  case _ =>
                    registerQuickFix(
                      ScalaBundle.message("lazy.modifier.is.not.allowed.here"),
                      modifierPsi,
                      owner,
                      Lazy
                    )
                }
              case FINAL =>
                owner match {
                  case _: ScDeclaration =>
                    registerQuickFix(
                      ScalaBundle.message("final.modifier.not.with.declarations"),
                      modifierPsi,
                      owner,
                      Final
                    )
                  case _: ScTrait =>
                    registerQuickFix(
                      ScalaBundle.message("final.modifier.not.with.trait"),
                      modifierPsi,
                      owner,
                      Final
                    )
                  case _: ScClass => checkDuplicates(modifierPsi, Final)
                  case e: ScObject =>
                    if (checkDuplicates(modifierPsi, Final) && e.isTopLevel) {
                      registerQuickFix(
                        ScalaBundle.message("final.modifier.is.redundant.with.object"),
                        modifierPsi,
                        owner,
                        Final,
                        WARNING
                      )
                    }
                  case e: ScMember if e.getParent.isInstanceOf[ScTemplateBody] || e.getParent.isInstanceOf[ScEarlyDefinitions] =>
                    val redundant = (e.containingClass, e) match {
                      case (_, valMember: ScPatternDefinition) if valMember.typeElement.isEmpty &&
                        valMember.pList.simplePatterns => false // constant value definition, see SCL-11500
                      case (cls, _) if cls.hasFinalModifier => true
                      case _ => false
                    }
                    if (redundant) {
                      if (checkDuplicates(modifierPsi, Final)) {
                        registerQuickFix(
                          ScalaBundle.message("final.modifier.is.redundant.with.final.parents"),
                          modifierPsi,
                          owner,
                          Final,
                          WARNING
                        )
                      }
                    } else {
                      checkDuplicates(modifierPsi, Final)
                    }
                  case e: ScClassParameter =>
                    if (PsiTreeUtil.getParentOfType(e, classOf[ScTypeDefinition]).hasFinalModifier) {
                      if (checkDuplicates(modifierPsi, Final)) {
                        registerQuickFix(
                          ScalaBundle.message("final.modifier.is.redundant.with.final.parents"),
                          modifierPsi,
                          owner,
                          Final,
                          WARNING
                        )
                      }
                    } else {
                      checkDuplicates(modifierPsi, Final)
                    }
                  case _ =>
                    registerQuickFix(
                      ScalaBundle.message("final.modifier.is.not.allowed.here"),
                      modifierPsi,
                      owner,
                      Final
                    )
                }
              case SEALED =>
                owner match {
                  case _: ScClass | _: ScTrait | _: ScClassParameter => checkDuplicates(modifierPsi, Sealed)
                  case e: ScMember if e.getParent.isInstanceOf[ScTemplateBody] => checkDuplicates(modifierPsi, Sealed)
                  case _ =>
                    registerQuickFix(
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
                    registerQuickFix(
                      ScalaBundle.message("abstract.modifier.redundant.fot.traits"),
                      modifierPsi,
                      owner,
                      Abstract,
                      WARNING
                    )
                  }
                  case member: ScMember if !member.isInstanceOf[ScTemplateBody] &&
                    member.getParent.isInstanceOf[ScTemplateBody] =>
                    // 'abstract override' modifier only allowed for members of traits
                    if (!member.containingClass.isInstanceOf[ScTrait] && owner.hasModifierProperty(OVERRIDE)) {
                      registerQuickFix(
                        ScalaBundle.message("abstract.override.modifier.is.not.allowed"),
                        modifierPsi,
                        owner,
                        Abstract
                      )
                    } else {
                      checkDuplicates(modifierPsi, Abstract)
                    }
                  case _ =>
                    registerQuickFix(
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
                    registerQuickFix(
                      ScalaBundle.message("override.modifier.is.not.allowed.for.classes"),
                      modifierPsi,
                      owner,
                      Override
                    )
                  case member: ScMember if member.getParent.isInstanceOf[ScTemplateBody] ||
                    member.getParent.isInstanceOf[ScEarlyDefinitions] =>
                    checkDuplicates(modifierPsi, Override)
                  case _: ScClassParameter => checkDuplicates(modifierPsi, Override)
                  case _ =>
                    registerQuickFix(
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
                    if (onTopLevel) {
                      registerQuickFix(
                        ScalaBundle.message("implicit.modifier.cannot.be.used.for.top.level.objects"),
                        modifierPsi,
                        owner,
                        Implicit
                      )
                    } else
                      c match {
                        case clazz: ScClass =>

                          def errorResult(): Unit = registerQuickFix(
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
                    registerQuickFix(
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

  private def registerQuickFix(@Nls message: String, element: PsiElement,
                               owner: ScModifierListOwner, modifier: ScalaModifier,
                               severity: HighlightSeverity = ERROR)
                              (implicit holder: ScalaAnnotationHolder): Unit = {
    holder.createAnnotation(severity, element.getTextRange, message)
      .registerFix(new quickfix.ModifierQuickFix.Remove(owner, null, modifier))
  }
}
