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

  override def toString: String = "ScTrait"

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

  def fakeCompanionClass: PsiClass = new PsiClassWrapper(this, getQualifiedName + "$class", javaName + "$class")

  import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._

  override def getMethods: Array[PsiMethod] = {
    getAllMethods.filter(_.getContainingClass == this)
  }

  override def getAllMethods: Array[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]()
    res ++= getConstructors
    val signatures = TypeDefinitionMembers.getSignatures(this).forAll()._1.valuesIterator
    while (signatures.hasNext) {
      val signature = signatures.next()
      signature.foreach {
        case (t, node) => node.info.namedElement match {
          case Some(fun: ScFunction) if !fun.isConstructor && fun.getContainingClass.isInstanceOf[ScTrait] &&
            fun.isInstanceOf[ScFunctionDefinition] =>
            res += fun.getFunctionWrapper(false, true)
          case Some(fun: ScFunction) if !fun.isConstructor =>
            res += fun.getFunctionWrapper(false, true)
          case Some(method: PsiMethod) if !method.isConstructor =>
            res += method
          case Some(t: ScTypedDefinition) if t.isVal || t.isVar =>
            res += t.getTypedDefinitionWrapper(false, true, SIMPLE_ROLE)
            t.nameContext match {
              case s: ScAnnotationsHolder =>
                val beanProperty = s.hasAnnotation("scala.reflect.BeanProperty") != None
                val booleanBeanProperty = s.hasAnnotation("scala.reflect.BooleanBeanProperty") != None
                if (beanProperty) {
                  res += t.getTypedDefinitionWrapper(false, true, GETTER)
                  if (t.isVar) {
                    res += t.getTypedDefinitionWrapper(false, true, SETTER)
                  }
                } else if (booleanBeanProperty) {
                  res += t.getTypedDefinitionWrapper(false, true, IS_GETTER)
                  if (t.isVar) {
                    res += t.getTypedDefinitionWrapper(false, true, SETTER)
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