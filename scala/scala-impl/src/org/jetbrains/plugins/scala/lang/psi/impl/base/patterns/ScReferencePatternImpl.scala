package org.jetbrains.plugins.scala.lang.psi.impl.base
package patterns

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.TokenSets
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementType
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.base.ScPatternList
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns._
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScValueOrVariable
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScExtendsBlock, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScMember, ScTypeDefinition}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.{ScNamedElement, ScPackaging, ScTypedDefinition}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createWildcardPattern
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaStubBasedElementImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.ScBindingPatternStub
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.result._

import javax.swing.Icon

class ScReferencePatternImpl private(stub: ScBindingPatternStub[ScReferencePattern], node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementType.REFERENCE_PATTERN, node)
    with ScPatternImpl
    with ScReferencePattern
    with ContributedReferenceHost {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScBindingPatternStub[ScReferencePattern]) = this(stub, null)

  override def isIrrefutableFor(t: Option[ScType]): Boolean = true

  override def nameId: PsiElement = findChildByType[PsiElement](TokenSets.ID_SET)

  override def toString: String = "ReferencePattern: " + ifReadAllowed(name)("")

  override def `type`(): TypeResult =
    this.expectedType match {
      case Some(x) => Right(x)
      case _       => Failure(ScalaBundle.message("cannot.define.expected.type"))
    }

  override def getIcon(flags: Int): Icon =
    nameContext match {
      case v: ScValueOrVariable =>
        //When we are inside val/var declaration, get the corresponding declaration icon with proper flags
        //This can matter e.g. when we show usages dialog when ctrl-clicking on `given` imports
        v.getIcon(flags)
      case _ =>
        super.getIcon(flags)
    }

  override def getReferences: Array[PsiReference] = {
    PsiReferenceService.getService.getContributedReferences(this)
  }

  override def getNavigationElement: PsiElement =
    ScReferencePatternImpl.getNavigationElementForValOrVarId(this).getOrElse(this)

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    ScalaPsiUtil.processImportLastParent(processor, state, place, lastParent, `type`())
  }

  override def delete(): Unit = {
    getContext match {
      case pList: ScPatternList if pList.patterns == Seq(this) =>
        val context: PsiElement = pList.getContext
        context.getContext.deleteChildRange(context, context)
      case pList: ScPatternList if pList.simplePatterns && pList.patterns.startsWith(Seq(this)) =>
        val end = this.nextSiblings.find(_.getNode.getElementType == ScalaTokenTypes.tCOMMA).get.getNextSiblingNotWhitespace.getPrevSibling
        pList.deleteChildRange(this, end)
      case pList: ScPatternList if pList.simplePatterns =>
        val start = this.prevSiblings.find(_.getNode.getElementType == ScalaTokenTypes.tCOMMA).get.getPrevSiblingNotWhitespace.getNextSibling
        pList.deleteChildRange(start, this)
      case _ =>
        // val (a, b) = t
        // val (_, b) = t
        replace(createWildcardPattern)
    }
  }

  override def getOriginalElement: PsiElement = super[ScReferencePattern].getOriginalElement
}

object ScReferencePatternImpl {

  def getNavigationElementForValOrVarId(valOrVarId: ScNamedElement): Option[PsiElement] = {
    val containingFile = valOrVarId.getContainingFile
    containingFile match {
      case sf: ScalaFile if sf.isCompiled =>
        valOrVarId.nameContext match {
          case v: ScValueOrVariable =>
            val membersInContainerNavigationElement = v.getParent match {
              case (_: ScTemplateBody) & Parent((_: ScExtendsBlock) & Parent(td: ScTypeDefinition)) =>
                val navigationElement = td.getNavigationElement
                navigationElement match {
                  case typeDefinition: ScTypeDefinition => Some(typeDefinition.members)
                  case _ => None
                }
              case _: ScPackaging =>
                //handle top-level definitions in Scala 3
                //type definitions (class,trait,etc...) are handled in ScTypeDefinitionImpl.getSourceMirrorClass
                val fileNavigationElement = containingFile.getNavigationElement
                fileNavigationElement match {
                  case scalaFile: ScalaFile => Some(scalaFile.members)
                  case _ => None
                }
              case _ => None
            }

            membersInContainerNavigationElement.flatMap(findNavigationTarget(valOrVarId, _))
          case _ => None
        }
      case _ => None
    }
  }

  private def findNavigationTarget(varOrVarId: ScNamedElement, members: Seq[ScMember]): Option[ScTypedDefinition] = {
    import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil.BacktickedName.stripBackticks
    val bindings = members.collect { case v: ScValueOrVariable => v.declaredElements }.flatten
    bindings.find { b =>
      //example when it matters:
      //`scala.caps#*` is defined as "val `*`" in sources, but in the decompiled version we use "val *"
      stripBackticks(b.name) == stripBackticks(varOrVarId.name)
    }
  }
}
