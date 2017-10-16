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
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScConstructor, ScPrimaryConstructor}
import org.jetbrains.plugins.scala.lang.psi.api.expr._
import org.jetbrains.plugins.scala.lang.psi.api.statements.{ScDeclaredElementsHolder, ScFunction, ScTypeAlias}
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScEarlyDefinitions
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.{ScClassParents, ScTemplateBody}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.PsiClassFake
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.stubs.ScTemplateDefinitionStub
import org.jetbrains.plugins.scala.lang.psi.types._
import org.jetbrains.plugins.scala.lang.psi.types.api.AnyRef
import org.jetbrains.plugins.scala.lang.psi.types.api.designator.ScDesignatorType
import org.jetbrains.plugins.scala.lang.psi.types.result.{Success, TypeResult}

import scala.collection.mutable.ArrayBuffer

/**
* @author Alexander Podkhalyuzin
* Date: 06.03.2008
*/

class ScNewTemplateDefinitionImpl private (stub: ScTemplateDefinitionStub, node: ASTNode)
  extends ScalaStubBasedElementImpl(stub, ScalaElementTypes.NEW_TEMPLATE, node) with ScNewTemplateDefinition with PsiClassFake {

  def this(node: ASTNode) = this(null, node)

  def this(stub: ScTemplateDefinitionStub) = this(stub, null)

  override def toString: String = "NewTemplateDefinition"

  override def getIcon(flags: Int) = Icons.CLASS

  override def constructor: Option[ScConstructor] =
    Option(extendsBlock).flatMap(_.templateParents).collect {
      case parents: ScClassParents if parents.typeElements.length == 1 => parents
    }.flatMap(_.constructor)

  protected override def innerType: TypeResult[ScType] = {
    constructor match {
      case Some(constructor) =>
        constructor.reference match {
          case Some(reference) =>
            val len = reference.resolve() match {
              case prim: ScPrimaryConstructor => prim.effectiveParameterClauses.length
              case f: ScFunction if f.isConstructor => f.effectiveParameterClauses.length
              case m: PsiMethod if m.isConstructor => 1
              case _ => -1
            }
            val argLen = constructor.arguments.length
            if (len >= 0 && len < argLen) {
              //It's very rare case, when we need to desugar apply first.
              //So let's create new PSI and call innerType once again.
              def calcOffset(num: Int, element: PsiElement, res: Int = 0): Int = {
                if (num <= 0) return res
                calcOffset(num - 1, element.getParent, res + element.getStartOffsetInParent)
              }

              val text = "(" + getText.substring(0, calcOffset(4, constructor.arguments(len))) + ")" +
                constructor.arguments.takeRight(argLen - len).map(_.getText).mkString
              val newExpr = ScalaPsiElementFactory.createExpressionWithContextFromText(text, getContext, this)
              return newExpr.getNonValueType(fromUnderscore = true)
            }
          case _ =>
        }
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
      Success(ScCompoundType.fromPsi(superTypes, holders.toList, aliases.toList), Some(this))
    } else {
      extendsBlock.templateParents match {
        case Some(tp) if tp.allTypeElements.length == 1 =>
          tp.allTypeElements.head.getNonValueType()
        case _ =>
          superTypes.headOption match {
            case Some(t) => Success(t, Some(this))
            case None => Success(AnyRef, Some(this)) //this is new {} case
          }
      }
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

  def getTypeWithProjections(thisProjections: Boolean = false): TypeResult[ScType] = getType() //no projections for new template definition

  override def isInheritor(baseClass: PsiClass, deep: Boolean): Boolean =
    super[ScNewTemplateDefinition].isInheritor(baseClass, deep)

  override def accept(visitor: ScalaElementVisitor) {
    visitor.visitNewTemplateDefinition(this)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => visitor.visitNewTemplateDefinition(this)
      case _ => super.accept(visitor)
    }
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
    val res = new ArrayBuffer[PsiMethod]()
    TypeDefinitionMembers.SignatureNodes.forAllSignatureNodes(this) { node =>
      this.processPsiMethodsForNode(node, isStatic = false, isInterface = false)(res += _)
    }
    res.toArray
  }

  override def psiMethods: Array[PsiMethod] = getAllMethods.filter(_.containingClass == this)
}
