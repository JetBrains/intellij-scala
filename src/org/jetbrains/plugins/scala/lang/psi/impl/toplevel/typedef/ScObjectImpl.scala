package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import api.toplevel.ScTypedDefinition
import java.lang.String
import com.intellij.psi._
import com.intellij.psi.scope.PsiScopeProcessor
import psi.stubs.ScTemplateDefinitionStub
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import types.{ScDesignatorType, ScSubstitutor, ScType}
import collection.mutable.ArrayBuffer
import api.base.ScPrimaryConstructor
import refactoring.util.ScTypeUtil
import types.result.TypingContext

/**
 * @author Alexander Podkhalyuzin
 * Date: 20.02.2008
 */

class ScObjectImpl extends ScTypeDefinitionImpl with ScObject with ScTemplateDefinition {
  def this(node: ASTNode) = {this (); setNode(node)}

  def this(stub: ScTemplateDefinitionStub) = {this (); setStub(stub); setNode(null)}

  override def toString: String = if (isPackageObject) "ScPackageObject" else "ScObject"

  override def getIconInner = if (isPackageObject) Icons.PACKAGE_OBJECT else Icons.OBJECT

  override def hasModifierProperty(name: String): Boolean = {
    if (name == "final") return true
    super[ScTypeDefinitionImpl].hasModifierProperty(name)
  }


  override def isPackageObject: Boolean = {
    val stub = getStub
    if (stub != null) {
      stub.asInstanceOf[ScTemplateDefinitionStub].isPackageObject
    } else findChildByType(ScalaTokenTypes.kPACKAGE) != null
  }

  override def isCase = hasModifierProperty("case")

  override def getContainingClass() = null

  override def processDeclarations(processor: PsiScopeProcessor,
                                   state: ResolveState,
                                   lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
  }

  @volatile
  private var syntheticMembersRes: Seq[PsiMethod] = null
  @volatile
  private var modCount: Long = 0L

  override def syntheticMembers(): Seq[PsiMethod] = {
    if (isSyntheticObject) return super.syntheticMembers
    ScalaPsiUtil.getCompanionModule(this) match {
      case Some(c: ScClass) if c.isCase =>
        var answer = syntheticMembersRes
        val count = getManager.getModificationTracker.getModificationCount
        if (answer != null && count == modCount) return answer
        val res = new ArrayBuffer[PsiMethod]
        res ++= super.syntheticMembers
        val texts = c.getSyntheticMethodsText
        Seq(texts._1, texts._2).foreach(s => {
          try {
            val method = ScalaPsiElementFactory.createMethodWithContext(s, c.getContext, c)
            res += method
          }
          catch {
            case e: Exception => //do not add methods with wrong signature
          }
        })
        answer = res.toSeq
        modCount = count
        syntheticMembersRes = answer
        return answer
      case _ => return super.syntheticMembers
    }
  }
}
