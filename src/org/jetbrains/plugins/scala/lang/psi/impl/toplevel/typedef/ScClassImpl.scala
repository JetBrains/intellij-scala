package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import api.base.ScPrimaryConstructor
import psi.stubs.ScTemplateDefinitionStub
import com.intellij.openapi.progress.ProgressManager
import collection.mutable.ArrayBuffer
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypeParametersOwner
import com.intellij.psi._
import api.ScalaElementVisitor
import lang.resolve.processor.BaseProcessor
import caches.CachesUtil
import util.PsiModificationTracker
import com.intellij.openapi.project.DumbServiceImpl

/**
 * @author Alexander.Podkhalyuzin
 */

class ScClassImpl extends ScTypeDefinitionImpl with ScClass with ScTypeParametersOwner with ScTemplateDefinition {
  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateDefinitionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScClass"

  override def getIconInner = Icons.CLASS

  def constructor: Option[ScPrimaryConstructor] = {
    val stub = getStub
    if (stub != null) {
      val array =
        stub.getChildrenByType(ScalaElementTypes.PRIMARY_CONSTRUCTOR, JavaArrayFactoryUtil.ScPrimaryConstructorFactory)
      return array.headOption
    }
    findChild(classOf[ScPrimaryConstructor])
  }

  def parameters = constructor match {
    case Some(c) => c.effectiveParameterClauses.flatMap(_.unsafeClassParameters)
    case None => Seq.empty
  }

  override def members = constructor match {
    case Some(c) => super.members ++ Seq(c)
    case _ => super.members
  }

  import com.intellij.psi.{PsiElement, ResolveState}
  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarations(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (DumbServiceImpl.getInstance(getProject).isDumb) return true
    if (!super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)) return false

    for (p <- parameters) {
      ProgressManager.checkCanceled()
      if (processor.isInstanceOf[BaseProcessor]) { // don't expose class parameters to Java.
        if (!processor.execute(p, state)) return false
      }
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
    buffer.toArray
  }

  override def syntheticMembers: scala.Seq[PsiMethod] = {
    CachesUtil.get(this, CachesUtil.SYNTHETIC_MEMBERS_KEY,
      new CachesUtil.MyProvider[ScClassImpl, Seq[PsiMethod]](this, _ => {
        val res = new ArrayBuffer[PsiMethod]
        res ++= super.syntheticMembers
        res ++= syntheticMembersImpl
        res.toSeq
      })(PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
  }

  private def syntheticMembersImpl: Seq[PsiMethod] = {
    val buf = new ArrayBuffer[PsiMethod]
    if (isCase && parameters.length > 0) {
      constructor match {
        case Some(x: ScPrimaryConstructor) =>
          val hasCopy = !TypeDefinitionMembers.getSignatures(this).forName("copy")._1.isEmpty
          val addCopy = !hasCopy && !x.parameterList.clauses.exists(_.hasRepeatedParam)
          if (addCopy) {
            try {
              val method = ScalaPsiElementFactory.createMethodWithContext(copyMethodText, this, this)
              method.setSynthetic()
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
    val paramString = (if (x.parameterList.clauses.length == 1 &&
            x.parameterList.clauses.apply(0).isImplicit) "()" else "") + x.parameterList.clauses.map{ c =>
        val start = if (c.isImplicit) "(implicit " else "("
        c.parameters.map{ p =>
          val paramType = p.typeElement match {
            case Some(te) => te.getText
            case None => "Any"
          }
          p.name + " : " + paramType + " = this." + p.name
        }.mkString(start, ", ", ")")
    }.mkString("")

    val returnType = name + typeParameters.map(_.getName).mkString("[", ",", "]")
    "def copy" + typeParamString + paramString + " : " + returnType + " = throw new Error(\"\")"
  }
}