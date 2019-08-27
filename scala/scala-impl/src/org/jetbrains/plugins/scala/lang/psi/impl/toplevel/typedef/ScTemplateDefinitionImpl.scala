package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import com.intellij.lang.ASTNode
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.impl.PsiClassImplUtil
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.scope.processor.MethodsProcessor
import com.intellij.psi.util.{PsiTreeUtil, PsiUtil}
import com.intellij.psi.{PsiElement, PsiField, ResolveState}
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScValue, ScVariable}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScExtendsBlock
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScObject, ScTemplateDefinition, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types.ScalaType
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScThisType
import org.jetbrains.plugins.scala.lang.resolve.ScalaResolveState.ResolveStateExt
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor

abstract class ScTemplateDefinitionImpl[T <: ScTemplateDefinition] private[impl](stub: ScTemplateDefinitionStub[T],
                                                                                 nodeType: ScTemplateDefinitionElementType[T],
                                                                                 node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node)
    with PsiClassFake
    with ScTemplateDefinition {

  import PsiTreeUtil.isContextAncestor

  override final def getAllFields: Array[PsiField] = PsiClassImplUtil.getAllFields(this)

  override def processDeclarations(processor: PsiScopeProcessor,
                                   oldState: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean =
    processDeclarationsImpl(processor, oldState, lastParent, place)

  protected final def processDeclarationsImpl(processor: PsiScopeProcessor,
                                              oldState: ResolveState,
                                              lastParent: PsiElement,
                                              place: PsiElement): Boolean = processor match {
    case _: BaseProcessor =>
      extendsBlock.templateBody match {
        case Some(ancestor) if isContextAncestor(ancestor, place, false) && lastParent != null => true
        case _ => processDeclarationsForTemplateBody(processor, oldState, lastParent, place)
      }
    case _ =>
      val languageLevel = processor match {
        case methodProcessor: MethodsProcessor => methodProcessor.getLanguageLevel
        case _ => PsiUtil.getLanguageLevel(getProject)
      }
      PsiClassImplUtil.processDeclarationsInClass(
        this,
        processor,
        oldState,
        null,
        this.lastChildStub.orNull,
        place,
        languageLevel,
        false
      )
  }

  def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                         oldState: ResolveState,
                                         lastParent: PsiElement,
                                         place: PsiElement): Boolean = {
    if (DumbService.getInstance(getProject).isDumb) return true
    //exception cases
    this match {
      case s: ScTypeParametersOwner => s.typeParametersClause match {
        case Some(tpc) if isContextAncestor(tpc, place, false) => return true
        case _ =>
      }
      case _ =>
    }

    // Process selftype reference
    selfTypeElement match {
      case Some(se) if se.name != "_" => if (!processor.execute(se, oldState)) return false
      case _ =>
    }

    val fromType =
      if (ScalaPsiUtil.isPlaceTdAncestor(this, place)) ScThisType(this)
      else ScalaType.designator(this)

    val state = oldState.withFromType(fromType)

    val eb = extendsBlock
    eb.templateParents match {
      case Some(p) if isContextAncestor(p, place, false) =>
        eb.earlyDefinitions match {
          case Some(ed) => for (m <- ed.members) {
            ProgressManager.checkCanceled()
            m match {
              case _var: ScVariable => for (declared <- _var.declaredElements) {
                ProgressManager.checkCanceled()
                if (!processor.execute(declared, state)) return false
              }
              case _val: ScValue => for (declared <- _val.declaredElements) {
                ProgressManager.checkCanceled()
                if (!processor.execute(declared, state)) return false
              }
            }
          }
          case None =>
        }
        true
      case _ =>
        eb.earlyDefinitions match {
          case Some(ed) if isContextAncestor(ed, place, true) =>
          case _ =>
            extendsBlock match {
              case e: ScExtendsBlock if e != null =>
                if (isContextAncestor(e, place, true) ||
                  ScalaPsiUtil.isSyntheticContextAncestor(e, place) ||
                  !isContextAncestor(this, place, true)) {
                  this match {
                    case t: ScTypeDefinition if selfTypeElement.isDefined &&
                      !isContextAncestor(selfTypeElement.get, place, true) &&
                      isContextAncestor(e.templateBody.orNull, place, true) &&
                      processor.isInstanceOf[BaseProcessor] && !t.isInstanceOf[ScObject] =>
                      selfTypeElement match {
                        case Some(_) => processor.asInstanceOf[BaseProcessor].processType(ScThisType(t), place, state)
                        case _ =>
                          if (!TypeDefinitionMembers.processDeclarations(this, processor, state, lastParent, place)) {
                            return false
                          }
                      }
                    case _ =>
                      if (!TypeDefinitionMembers.processDeclarations(this, processor, state, lastParent, place)) return false
                  }
                }
              case _ =>
            }
        }
        true
    }
  }
}