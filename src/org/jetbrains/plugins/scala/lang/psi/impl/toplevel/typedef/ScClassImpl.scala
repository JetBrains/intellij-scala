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
import collection.mutable.ArrayBuffer
import light.StaticPsiMethodWrapper
import api.statements._
import extensions.toPsiMemberExt
import params.{ScParameter, ScParameterClause, ScClassParameter}
import collection.mutable

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
      case Some(m) => Array(m.getName)
      case _ => Array.empty
    }
  }

  def this(node: ASTNode) = {this(); setNode(node)}
  def this(stub: ScTemplateDefinitionStub) = {this(); setStub(stub); setNode(null)}

  override def toString: String = "ScClass: " + name

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
    getAllMethods.filter(_.containingClass == this)
  }

  override def getAllMethods: Array[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]()
    val names = new mutable.HashSet[String]
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
    val signatures = TypeDefinitionMembers.getSignatures(this).allFirstSeq().iterator
    while (signatures.hasNext) {
      val signature = signatures.next()
      signature.foreach {
        case (t, node) => node.info.namedElement match {
          case Some(fun: ScFunction) if !fun.isConstructor && fun.containingClass.isInstanceOf[ScTrait] &&
            fun.isInstanceOf[ScFunctionDefinition] =>
            res ++= fun.getFunctionWrappers(isStatic = false, isInterface = false,
              cClass = Some(getClazz(fun.containingClass.asInstanceOf[ScTrait])))
            names += fun.getName
          case Some(fun: ScFunction) if !fun.isConstructor => 
            res ++= fun.getFunctionWrappers(isStatic = false, isInterface = fun.isInstanceOf[ScFunctionDeclaration])
            names += fun.getName
          case Some(method: PsiMethod) if !method.isConstructor => 
            res += method
            names += method.getName
          case Some(t: ScTypedDefinition) if t.isVal || t.isVar ||
            (t.isInstanceOf[ScClassParameter] && t.asInstanceOf[ScClassParameter].isCaseClassVal) =>
            val (isInterface, cClass) = t.nameContext match {
              case m: ScMember =>
                val isConcrete = m.isInstanceOf[ScPatternDefinition] || m.isInstanceOf[ScVariableDefinition] ||
                   m.isInstanceOf[ScClassParameter]
                (!isConcrete, m.containingClass match {
                  case t: ScTrait =>
                    if (isConcrete) {
                      Some(getClazz(t))
                    } else None
                  case _ => None
                })
              case _ => (false, None)
            }
            val nodeName = node.info.name
            if (t.name == nodeName) {
              res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = SIMPLE_ROLE, cClass = cClass)
              if (t.isVar) {
                res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = EQ, cClass = cClass)
              }
            }
            names += t.getName
            t.nameContext match {
              case s: ScAnnotationsHolder =>
                val beanProperty = ScalaPsiUtil.isBeanProperty(s)
                val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s)
                if (beanProperty) {
                  if (nodeName == "get" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = GETTER, cClass = cClass)
                    names += "get" + t.getName.capitalize
                  }
                  if (t.isVar && nodeName == "set" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = SETTER, cClass = cClass)
                    names += "set" + t.getName.capitalize
                  }
                } else if (booleanBeanProperty) {
                  if (nodeName == "is" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = IS_GETTER, cClass = cClass)
                    names += "is" + t.getName.capitalize
                  }
                  if (t.isVar && nodeName == "set" + t.name.capitalize) {
                    res += t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = SETTER, cClass = cClass)
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
        val signatures = TypeDefinitionMembers.getSignatures(o).allFirstSeq().iterator
        while (signatures.hasNext) {
          val signature = signatures.next()
          signature.foreach {
            case (t, node) =>
              node.info.namedElement match {
                case Some(fun: ScFunction) if !fun.isConstructor =>
                  fun.getFunctionWrappers(isStatic = true, isInterface = false, cClass = Some(this)).foreach(add)
                case Some(method: PsiMethod) if !method.isConstructor => {
                  if (method.containingClass != null && method.containingClass.getQualifiedName != "java.lang.Object") {
                    add(StaticPsiMethodWrapper.getWrapper(method, this))
                  }
                }
                case Some(t: ScTypedDefinition) if t.isVal || t.isVar =>
                  val nodeName = node.info.name
                  if (nodeName == t.name) {
                    add(t.getTypedDefinitionWrapper(isStatic = true, isInterface = false, role = SIMPLE_ROLE))
                    if (t.isVar) {
                      add(t.getTypedDefinitionWrapper(isStatic = false, isInterface = isInterface, role = EQ))
                    }
                  }
                  t.nameContext match {
                    case s: ScAnnotationsHolder =>
                      val beanProperty = ScalaPsiUtil.isBeanProperty(s)
                      val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(s)
                      if (beanProperty) {
                        if (nodeName == "get" + t.name.capitalize) {
                          add(t.getTypedDefinitionWrapper(isStatic = true, isInterface = false, role = GETTER))
                        }
                        if (t.isVar && nodeName == "set" + t.name.capitalize) {
                          add(t.getTypedDefinitionWrapper(isStatic = true, isInterface = false, role = SETTER))
                        }
                      } else if (booleanBeanProperty) {
                        if (nodeName == "is" + t.name.capitalize) {
                          add(t.getTypedDefinitionWrapper(isStatic = true, isInterface = false, role = IS_GETTER))
                        }
                        if (t.isVar && nodeName == "set" + t.name.capitalize) {
                          add(t.getTypedDefinitionWrapper(isStatic = true, isInterface = false, role = SETTER))
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
    buffer ++= functions.filter(_.isConstructor).flatMap(_.getFunctionWrappers(isStatic = false, isInterface = false, Some(this)))
    constructor match {
      case Some(x) => buffer ++= x.getFunctionWrappers
      case _ =>
    }
    buffer.toArray
  }

  override def syntheticMethodsNoOverride: scala.Seq[PsiMethod] = {
    CachesUtil.get(this, CachesUtil.SYNTHETIC_MEMBERS_KEY,
      new CachesUtil.MyProvider[ScClassImpl, Seq[PsiMethod]](this, clazz => clazz.innerSyntheticMembers)
        (PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT))
  }

  private def innerSyntheticMembers: Seq[PsiMethod] = {
    val res = new ArrayBuffer[PsiMethod]
    res ++= super.syntheticMethodsNoOverride
    res ++= syntheticMembersImpl
    res.toSeq
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
              method.setSynthetic(this)
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

  private def implicitMethodText: String = {
    val constr = constructor.getOrElse(return "")
    val returnType = name + typeParametersClause.map(clause => typeParameters.map(_.name).
      mkString("[", ",", "]")).getOrElse("")
    val typeParametersText = typeParametersClause.map(tp => {
      tp.typeParameters.map(tp => {
        val baseText = tp.typeParameterText
        if (tp.isContravariant) {
          val i = baseText.indexOf('-')
          baseText.substring(i + 1)
        } else if (tp.isCovariant) {
          val i = baseText.indexOf('+')
          baseText.substring(i + 1)
        } else baseText
      }).mkString("[", ", ", "]")
    }).getOrElse("")
    val parametersText = constr.parameterList.clauses.map {
      case clause: ScParameterClause => clause.parameters.map {
        case parameter: ScParameter => s"${parameter.name} : ${parameter.typeElement.map(_.getText).getOrElse("Nothing")}"
      }.mkString(if (clause.isImplicit) "(implicit " else "(", ", ", ")")
    }.mkString
    getModifierList.accessModifier.map(am => am.getText + " ").getOrElse("") + "implicit def " + name +
      typeParametersText + parametersText + " : " + returnType +
      " = throw new Error(\"\")"
  }

  @volatile
  private var syntheticImplicitMethod: Option[ScFunction] = null
  @volatile
  private var syntheticImplicitMethodModificationCount: Long = 0

  def getSyntheticImplicitMethod: Option[ScFunction] = {
    val count = getManager.getModificationTracker.getOutOfCodeBlockModificationCount
    if (count == syntheticImplicitMethodModificationCount && syntheticImplicitMethod != null) {
      return syntheticImplicitMethod
    }
    val res: Option[ScFunction] =
      if (hasModifierProperty("implicit")) {
        constructor match {
          case Some(x: ScPrimaryConstructor) =>
            try {
              val method = ScalaPsiElementFactory.createMethodWithContext(implicitMethodText, this.getContext, this)
              method.setSynthetic(this)
              Some(method)
            } catch {
              case e: Exception => None
            }
          case None => None
        }
      } else None
    syntheticImplicitMethod = res
    syntheticImplicitMethodModificationCount = count
    res
  }

  override def getTypeParameterList: PsiTypeParameterList = typeParametersClause.getOrElse(null)

  override def getInterfaces: Array[PsiClass] = {
    getSupers.filter(_.isInterface)
  }
}