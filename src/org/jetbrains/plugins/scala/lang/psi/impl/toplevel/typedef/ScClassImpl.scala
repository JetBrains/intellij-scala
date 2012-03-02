package org.jetbrains.plugins.scala
package lang
package psi
package impl
package toplevel
package typedef

import api.base.ScPrimaryConstructor
import psi.stubs.ScTemplateDefinitionStub
import com.intellij.openapi.progress.ProgressManager
import com.intellij.lang.ASTNode

import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes

import org.jetbrains.plugins.scala.icons.Icons

import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import com.intellij.psi._
import api.ScalaElementVisitor
import lang.resolve.processor.BaseProcessor
import caches.CachesUtil
import util.PsiModificationTracker
import com.intellij.openapi.project.DumbServiceImpl
import types.ScType
import api.toplevel.{ScTypedDefinition, ScTypeParametersOwner}
import collection.mutable.{HashSet, ArrayBuffer}
import light.StaticPsiMethodWrapper
import api.statements._

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

  override def additionalJavaNames: Array[String] = {
    fakeCompanionModule match {
      case Some(m) => Array(m.javaName)
      case _ => Array.empty
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
  override def processDeclarationsForTemplateBody(processor: PsiScopeProcessor,
                                  state: ResolveState,
                                  lastParent: PsiElement,
                                  place: PsiElement): Boolean = {
    if (DumbServiceImpl.getInstance(getProject).isDumb) return true
    if (!super[ScTemplateDefinition].processDeclarationsForTemplateBody(processor, state, lastParent, place)) return false

    for (p <- parameters) {
      ProgressManager.checkCanceled()
      if (processor.isInstanceOf[BaseProcessor]) { // don't expose class parameters to Java.
        if (!processor.execute(p, state)) return false
      }
    }

    super[ScTypeParametersOwner].processDeclarations(processor, state, lastParent, place)
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement,
                                   place: PsiElement): Boolean = {
    super[ScTemplateDefinition].processDeclarations(processor, state, lastParent, place)
  }

  override def isCase: Boolean = hasModifierProperty("case")

  import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._

  override def getMethods: Array[PsiMethod] = {
    getAllMethods.filter(_.getContainingClass == this)
  }

  override def getAllMethods: Array[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]()
    val names = new HashSet[String]
    res ++= getConstructors
    val linearization = MixinNodes.linearization(this).flatMap(tp => ScType.extractClass(tp, Some(getProject)))
    def getClazz(t: ScTrait): PsiClass = {
      var index = linearization.indexWhere(_ == t)
      while (index >= 0) {
        val clazz = linearization(index)
        if (!clazz.isInterface) return clazz
        index -= 1
      }
      this
    }
    val signatures = TypeDefinitionMembers.getSignatures(this).forAll()._1.valuesIterator
    while (signatures.hasNext) {
      val signature = signatures.next()
      signature.foreach {
        case (t, node) => node.info.namedElement match {
          case Some(fun: ScFunction) if !fun.isConstructor && fun.getContainingClass.isInstanceOf[ScTrait] &&
            fun.isInstanceOf[ScFunctionDefinition] =>
            res += fun.getFunctionWrapper(false, false, Some(getClazz(fun.getContainingClass.asInstanceOf[ScTrait])))
            names += fun.getName
          case Some(fun: ScFunction) if !fun.isConstructor => 
            res += fun.getFunctionWrapper(false, fun.isInstanceOf[ScFunctionDeclaration])
            names += fun.getName
          case Some(method: PsiMethod) if !method.isConstructor => 
            res += method
            names += method.getName
          case Some(t: ScTypedDefinition) if t.isVal || t.isVar =>
            val (isInterface, cClass) = t.nameContext match {
              case m: ScMember =>
                val b = m.isInstanceOf[ScPatternDefinition] || m.isInstanceOf[ScVariableDefinition]
                (b, m.getContainingClass match {
                  case t: ScTrait =>
                    if (b) {
                      Some(getClazz(t))
                    } else None
                  case _ => None
                })
              case _ => (false, None)
            }
            res += t.getTypedDefinitionWrapper(false, isInterface, SIMPLE_ROLE, cClass)
            names += t.getName
            t.nameContext match {
              case s: ScAnnotationsHolder =>
                val beanProperty = s.hasAnnotation("scala.reflect.BeanProperty") != None
                val booleanBeanProperty = s.hasAnnotation("scala.reflect.BooleanBeanProperty") != None
                if (beanProperty) {
                  res += t.getTypedDefinitionWrapper(false, isInterface, GETTER, cClass)
                  names += "get" + t.getName.capitalize
                  if (t.isVar) {
                    res += t.getTypedDefinitionWrapper(false, isInterface, SETTER, cClass)
                    names += "set" + t.getName.capitalize
                  }
                } else if (booleanBeanProperty) {
                  res += t.getTypedDefinitionWrapper(false, isInterface, IS_GETTER, cClass)
                  names += "is" + t.getName.capitalize
                  if (t.isVar) {
                    res += t.getTypedDefinitionWrapper(false, isInterface, SETTER, cClass)
                    names += "set" + t.getName.capitalize
                  }
                }
              case _ =>
            }
          case _ =>
        }
      }
    }
    
    ScalaPsiUtil.getCompanionModule(this) match {
      case Some(o: ScObject) =>
        def add(method: PsiMethod) {
          if (!names.contains(method.getName)) {
            res += method
          }
        }
        val signatures = TypeDefinitionMembers.getSignatures(o).forAll()._1.valuesIterator
        while (signatures.hasNext) {
          val signature = signatures.next()
          signature.foreach {
            case (t, node) =>
              node.info.namedElement match {
                case Some(fun: ScFunction) if !fun.isConstructor => add(fun.getFunctionWrapper(true, false))
                case Some(method: PsiMethod) if !method.isConstructor => {
                  if (method.getContainingClass != null && method.getContainingClass.getQualifiedName != "java.lang.Object") {
                    add(StaticPsiMethodWrapper.getWrapper(method, this))
                  }
                }
                case Some(t: ScTypedDefinition) if t.isVal || t.isVar =>
                  add(t.getTypedDefinitionWrapper(true, false, SIMPLE_ROLE))
                  t.nameContext match {
                    case s: ScAnnotationsHolder =>
                      val beanProperty = s.hasAnnotation("scala.reflect.BeanProperty") != None
                      val booleanBeanProperty = s.hasAnnotation("scala.reflect.BooleanBeanProperty") != None
                      if (beanProperty) {
                        add(t.getTypedDefinitionWrapper(true, false, GETTER))
                        if (t.isVar) {
                          add(t.getTypedDefinitionWrapper(true, false, SETTER))
                        }
                      } else if (booleanBeanProperty) {
                        add(t.getTypedDefinitionWrapper(true, false, IS_GETTER))
                        if (t.isVar) {
                          add(t.getTypedDefinitionWrapper(true, false, SETTER))
                        }
                      }
                    case _ =>
                  }
                case _ =>
              }
          }
        }
      case _ =>
    }
    res.toArray
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

    val returnType = name + typeParameters.map(_.name).mkString("[", ",", "]")
    "def copy" + typeParamString + paramString + " : " + returnType + " = throw new Error(\"\")"
  }

  override def getTypeParameterList: PsiTypeParameterList = typeParametersClause.getOrElse(null)

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }
}