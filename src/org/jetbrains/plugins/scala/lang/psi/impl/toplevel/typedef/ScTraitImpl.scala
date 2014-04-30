package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import stubs.ScTemplateDefinitionStub
import api.ScalaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.lang.ASTNode
import org.jetbrains.plugins.scala.icons.Icons
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import api.toplevel.{ScTypedDefinition, ScTypeParametersOwner}
import api.statements._
import collection.mutable.ArrayBuffer
import light.PsiClassWrapper
import extensions.toPsiMemberExt

/**
* @author Alexander Podkhalyuzin
* @since 20.02.2008
*/
class ScTraitImpl extends ScTypeDefinitionImpl with ScTrait with ScTypeParametersOwner with ScTemplateDefinition {
  override def additionalJavaNames: Array[String] = {
    Array(fakeCompanionClass.getName)
  }

  override def accept(visitor: PsiElementVisitor) {
    visitor match {
      case visitor: ScalaElementVisitor => super.accept(visitor)
      case _ => super.accept(visitor)
    }
  }

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateDefinitionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScTrait: " + name

  override def getIconInner = Icons.TRAIT

  import com.intellij.psi._
  import com.intellij.psi.scope.PsiScopeProcessor
  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place) &&
    super[ScTemplateDefinition].processDeclarationsForTemplateBody(processor, state, lastParent, place)
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
  }


  override def isInterface: Boolean = true

  def fakeCompanionClass: PsiClass = new PsiClassWrapper(this, getQualifiedName + "$class", getName + "$class")

  import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._

  override def getMethods: Array[PsiMethod] = {
    getAllMethods.filter(_.containingClass == this)
  }

  override def getAllMethods: Array[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]()
    res ++= getConstructors
    val signatures = TypeDefinitionMembers.getSignatures(this).allFirstSeq().iterator
    while (signatures.hasNext) {
      val signature = signatures.next()
      signature.foreach {
        case (t, node) => node.info.namedElement match {
          case fun: ScFunction if !fun.isConstructor && fun.containingClass.isInstanceOf[ScTrait] &&
            fun.isInstanceOf[ScFunctionDefinition] =>
            res ++= fun.getFunctionWrappers(isStatic = false, isInterface = true)
          case fun: ScFunction if !fun.isConstructor =>
            res ++= fun.getFunctionWrappers(isStatic = false, isInterface = true)
          case method: PsiMethod if !method.isConstructor =>
            res += method
          case t: ScTypedDefinition if t.isVal || t.isVar =>
            val nodeName = node.info.name
            if (nodeName == t.name) {
              res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = true, role = SIMPLE_ROLE)
              if (t.isVar) {
                res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = true, role = EQ)
              }
            }
            t.nameContext match {
              case s: ScAnnotationsHolder =>
                val beanProperty = ScalaPsiUtil.isBeanProperty(s)
                val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s)
                if (beanProperty) {
                  if (nodeName == "get" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = true, role = GETTER)
                  }
                  if (t.isVar && nodeName == "set" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = true, role = SETTER)
                  }
                } else if (booleanBeanProperty) {
                  if (nodeName == "is" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = true, role = IS_GETTER)
                  }
                  if (t.isVar && nodeName == "set" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = true, role = SETTER)
                  }
                }
              case _ =>
            }
          case _ =>
        }
      }
    }
    res.toArray
  }

  override def getTypeParameterList: PsiTypeParameterList = typeParametersClause.getOrElse(null)

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }
}