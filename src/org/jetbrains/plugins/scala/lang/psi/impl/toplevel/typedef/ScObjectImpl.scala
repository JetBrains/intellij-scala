package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import java.lang.String
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import psi.stubs.ScTemplateDefinitionStub
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import collection.mutable.ArrayBuffer
import api.ScalaElementVisitor
import caches.CachesUtil
import util.PsiModificationTracker
import lang.resolve.ResolveUtils
import com.intellij.openapi.project.DumbServiceImpl

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */

class ScObjectImpl extends ScTypeDefinitionImpl with ScObject with ScTemplateDefinition {
  override def accept(visitor: PsiElementVisitor): Unit = {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScTemplateDefinitionStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = if (isPackageObject) "ScPackageObject" else "ScObject"

  override def getIconInner = if (isPackageObject) Icons.PACKAGE_OBJECT else Icons.OBJECT

  override def hasModifierProperty(name: String): Boolean = {
    if (name == "final") return true
    super[ScTypeDefinitionImpl].hasModifierProperty(name)
  }

  override def isObject : Boolean = true

  override def isPackageObject: Boolean = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTemplateDefinitionStub].isPackageObject
    } else findChildByType(ScalaTokenTypes.kPACKAGE) != null || getName == "`package`"
  }

  def hasPackageKeyword: Boolean = findChildByType(ScalaTokenTypes.kPACKAGE) != null

  override def isCase = hasModifierProperty("case")

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    if (DumbServiceImpl.getInstance(getProject).isDumb) return true
    if (!super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)) return false
    if (isPackageObject && name != "`package`") {
      val qual = getQualifiedName
      val facade = JavaPsiFacade.getInstance(getProject)
      val pack = facade.findPackage(qual) //do not wrap into ScPackage to avoid SOE
      if (pack != null && !ResolveUtils.packageProcessDeclarations(pack, processor, state, lastParent, place))
        return false
    }
    true
  }

  def objectSyntheticMembers: Seq[PsiMethod] = {
    import CachesUtil._
    get(this, OBJECT_SYNTHETIC_MEMBERS_KEY, new MyProvider[ScObjectImpl, Seq[PsiMethod]](this, _ => {
      objectSyntheticMembersImpl
    })(PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
  }

  private def objectSyntheticMembersImpl: Seq[PsiMethod] = {
    if (isSyntheticObject) return Seq.empty
    ScalaPsiUtil.getCompanionModule(this) match {
      case Some(c: ScClass) if c.isCase =>
        val res = new ArrayBuffer[PsiMethod]
        val texts = c.getSyntheticMethodsText
        Seq(texts._1, texts._2).foreach(s => {
          try {
            val method = ScalaPsiElementFactory.createMethodWithContext(s, c.getContext, c)
            method.setSynthetic()
            res += method
          }
          catch {
            case e: Exception => //do not add methods with wrong signature
          }
        })
        res.toSeq
      case _ => Seq.empty
    }
  }
}
