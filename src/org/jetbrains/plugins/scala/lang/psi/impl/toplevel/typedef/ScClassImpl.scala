package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import api.base.{ScPrimaryConstructor, ScModifierList}
import api.statements.params.ScTypeParamClause
import com.intellij.psi.stubs.{StubElement, IStubElementType}
import com.intellij.util.ArrayFactory
import stubs.elements.wrappers.DummyASTNode
import stubs.ScTemplateDefinitionStub
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiMethod, PsiElement, PsiNamedElement, PsiModifierList}
import com.intellij.openapi.progress.ProgressManager
import collection.mutable.ArrayBuffer
import synthetic.ScSyntheticFunction
import types.nonvalue.Parameter
import types.result.TypingContext
import types.{ScType, ScThisType, ScSubstitutor}
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiElementImpl
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.annotations._
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner

/**
 * @author Alexander.Podkhalyuzin
 */

class ScClassImpl extends ScTypeDefinitionImpl with ScClass with ScTypeParametersOwner with ScTemplateDefinition {
 def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateDefinitionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScClass"

  override def getIconInner = Icons.CLASS

  def constructor: Option[ScPrimaryConstructor] = {
    val stub = getStub
    if (stub != null) {
      val array = stub.getChildrenByType(ScalaElementTypes.PRIMARY_CONSTRUCTOR, new ArrayFactory[ScPrimaryConstructor] {
        def create(count: Int): Array[ScPrimaryConstructor] = new Array[ScPrimaryConstructor](count)
      })
      if (array.length == 0) {
        return None
      } else {
        return Some(array.apply(0))
      }
    }
    findChild(classOf[ScPrimaryConstructor])
  }

  def parameters = constructor match {
    case Some(c) => c.parameters
    case None => Seq.empty
  }

  override def members() = constructor match {
    case Some(c) => super.members ++ Seq.singleton(c)
    case _ => super.members
  }

  import com.intellij.psi.{scope, PsiElement, ResolveState}
  import scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (!super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)) return false

    for (p <- parameters) {
      ProgressManager.checkCanceled
      if (!processor.execute(p, state)) return false
    }

    super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place)
  }

  override def isCase: Boolean = hasModifierProperty("case")

  override def getAllMethods: Array[PsiMethod] = {
    constructor match {
      case Some(c) => Array[PsiMethod](c) ++ super.getAllMethods
      case _ => super.getAllMethods
    }
  }

  override def getConstructors: Array[PsiMethod] = {
    val buffer = new ArrayBuffer[PsiMethod]
    buffer ++= functions.filter(_.isConstructor)
    constructor match {
      case Some(x) => buffer += x
      case _ =>
    }
    return buffer.toArray
  }

  @volatile
  private var syntheticMembersRes: Seq[PsiMethod] = null
  @volatile
  private var modCount: Long = 0L

  override def syntheticMembers(): scala.Seq[PsiMethod] = {
    var answer = syntheticMembersRes
    val count = getManager.getModificationTracker.getModificationCount
    if (answer != null && count == modCount) return answer
    val res = new ArrayBuffer[PsiMethod]
    res ++= super.syntheticMembers
    res ++= syntheticMembersImpl
    answer = res.toSeq
    modCount = count
    syntheticMembersRes = answer
    return answer
  }

  private def syntheticMembersImpl: Seq[PsiMethod] = {
    val buf = new ArrayBuffer[PsiMethod]
    if (isCase && parameters.length > 0) {
      constructor match {
        case Some(x: ScPrimaryConstructor) =>
          val signs = TypeDefinitionMembers.getSignatures(this)
          var hasCopy = false
          for (sign <- signs.iterator if !hasCopy) {
            if (sign._1.sig.name == "copy") hasCopy = true
          }
          val addCopy = !hasCopy && !x.parameterList.clauses.exists(_.hasRepeatedParam)
          if (addCopy) {
            try {
              val method = ScalaPsiElementFactory.createMethodWithContext(copyMethodText, this, getLastChild)
              buf += method
            } catch {
              case e: Exception =>
                //do not add methods if class has wrong signature.
            }
          }
        case None =>
      }
    }
    buf.toSeq
  }

  private def copyMethodText: String = {
    val x = constructor.getOrElse(return "")
    val paramString = x.parameterList.clauses.map{ c =>
        val start = if (c.isImplicit) "(implicit " else "("
        c.parameters.map{ p =>
          val paramType = ScType.canonicalText(p.getType(TypingContext.empty).getOrElse(lang.psi.types.Any))
          p.name + " : " + paramType + " = this." + p.name
        }.mkString(start, ", ", ")")
    }.mkString("")

    val typeParamStringRes =
      if (typeParameters.length > 0)
        typeParameters.map(_.name).mkString("[", ", ", "]")
      else ""
    "def copy" + typeParamStringRes + paramString + " : " + name + typeParamStringRes + " = throw new Error(\"\")"
  }
}