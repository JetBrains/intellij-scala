package org.jetbrains.plugins.scala
package lang
package psi
package impl
package expr

import com.intellij.lang.ASTNode
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.IncorrectOperationException
import javax.swing.Icon
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.annotator.{ScExpressionAnnotator, ScNewTemplateDefinitionAnnotator}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.{JavaConstructor, ScConstructor, ScalaConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory.createExpressionWithContextFromText
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.{ScTemplateDefinitionImpl, TypeDefinitionMembers}
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.stubs.elements.ScTemplateDefinitionElementType
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.AnyRef
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result._
import org.jetbrains.plugins.scala.macroAnnotations.{CachedInUserData, ModCount}

import scala.collection.mutable

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/
final class ScNewTemplateDefinitionImpl private[psi](stub: ScTemplateDefinitionStub[ScNewTemplateDefinition],
                                                     nodeType: ScTemplateDefinitionElementType[ScNewTemplateDefinition],
                                                     node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, nodeType, node)
    with ScNewTemplateDefinition with ScTemplateDefinitionImpl with PsiClassFake with ScExpressionAnnotator with ScNewTemplateDefinitionAnnotator {

  override def toString: String = "NewTemplateDefinition"

  override def getIcon(flags: Int): Icon = Icons.CLASS

  override def constructor: Option[ScConstructor] =
    Option(extendsBlock)
      .flatMap(_.templateParents)
      .filter(_.typeElements.length == 1)
      .flatMap(_.constructor)

  override protected def updateImplicitArguments(): Unit = {
    // for regular case implicits are owned by ScConstructor
    setImplicitArguments(desugaredApply.flatMap(_.findImplicitArguments))
  }

  protected override def innerType: TypeResult = {
    desugaredApply match {
      case Some(expr) =>
        return expr.getNonValueType()
      case _ =>
    }

    val earlyHolders: Seq[ScDeclaredElementsHolder] = extendsBlock.earlyDefinitions match {
      case Some(e: ScEarlyDefinitions) => e.members.flatMap {
        case holder: ScDeclaredElementsHolder => Seq(holder)
        case _ => Seq.empty
      }
      case None => Seq.empty
    }

    val (holders, aliases) : (Seq[ScDeclaredElementsHolder], Seq[ScTypeAlias]) = extendsBlock.templateBody match {
      case Some(b: ScTemplateBody) => (b.holders ++ earlyHolders, b.aliases)
      case None => (earlyHolders, Seq.empty)
    }

    val superTypes = extendsBlock.superTypes.filter {
      case ScDesignatorType(clazz: PsiClass) => clazz.qualifiedName != "scala.ScalaObject"
      case _                                 => true
    }


    if (superTypes.length > 1 || holders.nonEmpty || aliases.nonEmpty) {
      Right(ScCompoundType.fromPsi(superTypes, holders.toList, aliases.toList))
    } else {
      extendsBlock.templateParents match {
        case Some(tp) if tp.allTypeElements.length == 1 =>
          tp.allTypeElements.head.getNonValueType()
        case _ =>
          superTypes.headOption match {
            case Some(t) => Right(t)
            case None => Right(AnyRef) //this is new {} case
          }
      }
    }
  }

  override def desugaredApply: Option[ScExpression] = {
    if (constructor.forall(_.arguments.size <= 1)) None
    else cachedDesugaredApply
  }

  //It's very rare case, when we need to desugar `.apply` first.
  @CachedInUserData(this, ModCount.getBlockModificationCount)
  private def cachedDesugaredApply: Option[ScExpression] = {
    val resolvedConstructor = constructor.flatMap(_.reference).flatMap(_.resolve().toOption)
    val constrParamLength = resolvedConstructor.map {
      case ScalaConstructor(constr)         => constr.effectiveParameterClauses.length
      case JavaConstructor(_)               => 1
      case _                                => -1
    }
    val excessArgs =
      for {
        arguments   <- constructor.map(_.arguments)
        paramLength <- constrParamLength
        if paramLength >= 0
      } yield {
        arguments.drop(paramLength)
      }

    excessArgs match {
      case Some(args) if args.nonEmpty =>
        val desugaredText = {
          val firstArgListOfApply = args.head
          val startOffsetInThis   = firstArgListOfApply.getTextRange.getStartOffset - this.getTextRange.getStartOffset

          val thisText            = getText
          val newTemplateDefText  = thisText.substring(0, startOffsetInThis)
          val applyArgsText       = thisText.substring(startOffsetInThis)

          s"($newTemplateDefText)$applyArgsText"
        }

        createExpressionWithContextFromText(desugaredText, getContext, this).toOption
      case _ => None
    }
  }

 override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor, state: ResolveState,
                                          lastParent: PsiElement, place: PsiElement): Boolean =
  extendsBlock.templateBody match {
    case Some(body) if PsiTreeUtil.isContextAncestor(body, place, false) =>
      super[ScNewTemplateDefinition].processDeclarationsForTemplateBody(processor, state, lastParent, place)
    case _ => true
  }
  def nameId: PsiElement = null
  override def setName(name: String): PsiElement = throw new IncorrectOperationException("cannot set name")
  override def name: String = "<anonymous>"

  override def getName: String = name

  override def getSupers: Array[PsiClass] = {
    extendsBlock.supers.filter { clazz =>
      clazz != this && clazz.qualifiedName != "scala.ScalaObject"
    }.toArray
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    super[ScNewTemplateDefinition].processDeclarations(processor, state, lastParent, place)
  }

  override def getExtendsListTypes: Array[PsiClassType] = innerExtendsListTypes

  override def getImplementsListTypes: Array[PsiClassType] = innerExtendsListTypes

  def getTypeWithProjections(thisProjections: Boolean = false): TypeResult = `type`() //no projections for new template definition

  override def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean =
    super[ScNewTemplateDefinition].isInheritor(baseClass, deep)

  override protected def acceptScala(visitor: ScalaElementVisitor) {
    visitor.visitNewTemplateDefinition(this)
  }

  override def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = {
    super[ScNewTemplateDefinition].findMethodBySignature(patternMethod, checkBases)
  }

  override def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] = {
    super[ScNewTemplateDefinition].findMethodsBySignature(patternMethod, checkBases)
  }

  import java.util.{Collection => JCollection, List => JList}

  import com.intellij.openapi.util.{Pair => IPair}

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = {
    super[ScNewTemplateDefinition].findMethodsByName(name, checkBases)
  }

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = {
    super[ScNewTemplateDefinition].findFieldByName(name, checkBases)
  }

  override def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = {
    super[ScNewTemplateDefinition].findInnerClassByName(name, checkBases)
  }

  override def getAllFields: Array[PsiField] = {
    super[ScNewTemplateDefinition].getAllFields
  }

  override def findMethodsAndTheirSubstitutorsByName(name: String,
                                                     checkBases: Boolean): JList[IPair[PsiMethod, PsiSubstitutor]] = {
    super[ScNewTemplateDefinition].findMethodsAndTheirSubstitutorsByName(name, checkBases)
  }

  override def getAllMethodsAndTheirSubstitutors: JList[IPair[PsiMethod, PsiSubstitutor]] = {
    super[ScNewTemplateDefinition].getAllMethodsAndTheirSubstitutors
  }

  override def getVisibleSignatures: JCollection[HierarchicalMethodSignature] = {
    super[ScNewTemplateDefinition].getVisibleSignatures
  }

  override def getAllMethods: Array[PsiMethod] = {
    val res = mutable.ArrayBuffer.empty[PsiMethod]
    TypeDefinitionMembers.SignatureNodes.forAllSignatureNodes(this) { node =>
      this.processPsiMethodsForNode(node, isStatic = false, isInterface = false)(res += _)
    }
    res.toArray
  }

  override def psiMethods: Array[PsiMethod] = getAllMethods.filter(_.containingClass == this)
}
