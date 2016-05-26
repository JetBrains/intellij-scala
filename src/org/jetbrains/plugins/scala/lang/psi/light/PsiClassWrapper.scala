package org.jetbrains.plugins.scala.lang.psi.light

import java.util
import javax.swing._

import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.{Pair, TextRange}
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi._
import com.intellij.psi.impl.PsiClassImplUtil.MemberType
import com.intellij.psi.impl.light.LightElement
import com.intellij.psi.impl.{PsiClassImplUtil, PsiSuperMethodImplUtil}
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.scope.processor.MethodsProcessor
import com.intellij.psi.search.{GlobalSearchScope, SearchScope}
import com.intellij.psi.util.PsiUtil
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.ScalaPsiUtil
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.psi.light.PsiTypedDefinitionWrapper.DefinitionRole._
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.macroAnnotations.{Cached, ModCount}

import _root_.scala.collection.mutable.ArrayBuffer

/**
 * @author Alefas
 * @since 10.02.12
 */
class PsiClassWrapper(val definition: ScTemplateDefinition,
                      private var qualName: String,
                      private var name: String) extends LightElement(definition.getManager, definition.getLanguage) with PsiClass /*with SyntheticElement*/ {
  override def hashCode(): Int = definition.hashCode()

  override def equals(obj: Any): Boolean = {
    obj match {
      case wrapper: PsiClassWrapper =>
        definition.equals(wrapper.definition) && qualName == wrapper.qualName && name == wrapper.name
      case _ => false
    }
  }

  def getQualifiedName: String = qualName

  def isInterface: Boolean = false

  def isAnnotationType: Boolean = false

  def isEnum: Boolean = false

  def getExtendsList: PsiReferenceList = null //todo:

  def getImplementsList: PsiReferenceList = null //todo: ?

  def getExtendsListTypes: Array[PsiClassType] = Array.empty

  def getImplementsListTypes: Array[PsiClassType] = Array.empty

  def getSuperClass: PsiClass = null

  def getInterfaces: Array[PsiClass] = Array.empty

  def getSupers: Array[PsiClass] = Array.empty

  def getSuperTypes: Array[PsiClassType] = Array.empty

  def getFields: Array[PsiField] = {
    definition match {
      case o: ScObject => Array.empty
      case _ => definition.getFields //todo:
    }
  }

  def getMethods: Array[PsiMethod] = {
    definition match {
      case obj: ScObject =>
        (TypeDefinitionMembers.SignatureNodes.forAllSignatureNodes(obj) flatMap {
          this.processPsiMethodsForNode(_, isStatic = true, isInterface = false).map(_._1)
        }).toArray
      case t: ScTrait =>
        val res = new ArrayBuffer[PsiMethod]()

        def addGettersAndSetters(holder: ScAnnotationsHolder, declaredElements: Seq[ScTypedDefinition]): Unit = {
          val beanProperty = ScalaPsiUtil.isBeanProperty(holder)
          val booleanBeanProperty = ScalaPsiUtil.isBooleanBeanProperty(holder)
          if (beanProperty || booleanBeanProperty) {
            for (t <- declaredElements) {
              if (beanProperty) {
                res += t.getStaticTypedDefinitionWrapper(GETTER, this)
                if (t.isVar) {
                  res += t.getStaticTypedDefinitionWrapper(SETTER, this)
                }
              } else if (booleanBeanProperty) {
                res += t.getStaticTypedDefinitionWrapper(IS_GETTER, this)
                if (t.isVar) {
                  res += t.getStaticTypedDefinitionWrapper(SETTER, this)
                }
              }
            }
          }
        }
        val members = t.members
        members foreach {
          case fun: ScFunctionDefinition => res += fun.getStaticTraitFunctionWrapper(this)
          case definition: ScPatternDefinition => //only getters and setters should be added
            addGettersAndSetters(definition, definition.declaredElements)
          case definition: ScVariableDefinition => //only getters and setters should be added
            addGettersAndSetters(definition, definition.declaredElements)
          case _ =>
        }
        res.toArray
    }
  }

  @Cached(synchronized = false, ModCount.getBlockModificationCount, this)
  private def getEmptyConstructor: PsiMethod = new EmptyPrivateConstructor(this)

  def getConstructors: Array[PsiMethod] = {
    Array(getEmptyConstructor)
  }

  def getInnerClasses: Array[PsiClass] = {
    definition match {
      case o: ScObject =>
        o.members.flatMap {
          case o: ScObject => o.fakeCompanionClass match {
            case Some(clazz) => Seq(o, clazz)
            case None => Seq(o)
          }
          case t: ScTrait => Seq(t, t.fakeCompanionClass)
          case c: ScClass => Seq(c)
          case _ => Seq.empty
        }.toArray
      case _ => definition.getInnerClasses //todo:
    }
  }

  def getInitializers: Array[PsiClassInitializer] = Array.empty

  def getAllFields: Array[PsiField] = {
    PsiClassImplUtil.getAllFields(this)
  }

  def getAllMethods: Array[PsiMethod] = {
    PsiClassImplUtil.getAllMethods(this)
  }

  def getAllInnerClasses: Array[PsiClass] = {
    PsiClassImplUtil.getAllInnerClasses(this)
  }

  def findFieldByName(name: String, checkBases: Boolean): PsiField = {
    PsiClassImplUtil.findFieldByName(this, name, checkBases)
  }

  def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = {
    PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases)
  }

  def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] = {
    PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases)
  }

  def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = {
    PsiClassImplUtil.findMethodsByName(this, name, checkBases)
  }

  def findMethodsAndTheirSubstitutorsByName(name: String, checkBases: Boolean): util.List[Pair[PsiMethod, PsiSubstitutor]] = {
    PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases)
  }

  def getAllMethodsAndTheirSubstitutors: util.List[Pair[PsiMethod, PsiSubstitutor]] = {
    PsiClassImplUtil.getAllWithSubstitutorsByMap(this, MemberType.METHOD)
  }

  def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = {
    PsiClassImplUtil.findInnerByName(this, name, checkBases)
  }

  def getLBrace: PsiElement = {
    definition.getLBrace
  }

  def getRBrace: PsiElement = {
    definition.getRBrace
  }

  def getNameIdentifier: PsiIdentifier = {
    definition.getNameIdentifier
  }

  def getScope: PsiElement = {
    definition.getScope
  }

  def isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = {
    definition match {
      case o: ScObject =>
        baseClass.getQualifiedName == "java.lang.Object" ||
                (baseClass.getQualifiedName == "scala.ScalaObject" && !baseClass.isDeprecated)
      case _ => false
    }
  }

  def isInheritorDeep(baseClass: PsiClass, classToByPass: PsiClass): Boolean = {
    definition match {
      case o: ScObject =>
        baseClass.getQualifiedName == "java.lang.Object" ||
                (baseClass.getQualifiedName == "scala.ScalaObject" && !baseClass.isDeprecated)
      case _ => false
    }
  }

  def getContainingClass: PsiClass = {
    definition.getContainingClass
  }

  def getVisibleSignatures: util.Collection[HierarchicalMethodSignature] = {
    PsiSuperMethodImplUtil.getVisibleSignatures(this)
  }

  def setName(name: String): PsiElement = {
    this.name = name
    val packageName = StringUtil.getPackageName(this.qualName)
    this.qualName = if (packageName.isEmpty) name else packageName + "." + name
    this
  }

  override def getName: String = name

  override def copy: PsiElement = {
    new PsiClassWrapper(definition.copy.asInstanceOf[ScTemplateDefinition], qualName, name)
  }

  override def processDeclarations(processor: PsiScopeProcessor, state: ResolveState, lastParent: PsiElement, place: PsiElement): Boolean = {
    if (!processor.isInstanceOf[BaseProcessor]) {
      val languageLevel: LanguageLevel =
        processor match {
          case methodProcessor: MethodsProcessor => methodProcessor.getLanguageLevel
          case _ => PsiUtil.getLanguageLevel(place)
        }
      return PsiClassImplUtil.processDeclarationsInClass(this, processor, state, null, lastParent, place, languageLevel, false)
    }
    true
  }

  override def getContainingFile: PsiFile = {
    definition.getContainingFile
  }

  override def isValid: Boolean = definition.isValid

  override def getNextSibling: PsiElement = definition.getNextSibling

  override def getPrevSibling: PsiElement = definition.getPrevSibling

  override def getContext: PsiElement = {
    definition.getContext
  }

  override def getParent: PsiElement = definition.getParent

  override def getResolveScope: GlobalSearchScope = {
    definition.getResolveScope
  }

  override def getUseScope: SearchScope = {
    definition.getUseScope
  }

  override def toString: String = {
    "PsiClassWrapper(" + definition.toString + ")"
  }

  override def getIcon(flags: Int): Icon = {
    definition.getIcon(flags)
  }

  def getModifierList: PsiModifierList = {
    definition.getModifierList
  }

  def hasModifierProperty(name: String): Boolean = {
    definition.hasModifierProperty(name)
  }

  def getDocComment: PsiDocComment = {
    definition.getDocComment
  }

  def isDeprecated: Boolean = {
    definition.isDeprecated
  }

  override def getPresentation: ItemPresentation = {
    definition.getPresentation //todo: ?
  }

  override def navigate(requestFocus: Boolean) {
    definition.navigate(requestFocus)
  }

  override def canNavigate: Boolean = {
    definition.canNavigate
  }

  override def canNavigateToSource: Boolean = {
    definition.canNavigateToSource
  }

  override def getTextRange: TextRange = definition.getTextRange

  override def getTextOffset: Int = definition.getTextOffset

  def hasTypeParameters: Boolean = false

  def getTypeParameterList: PsiTypeParameterList = null

  def getTypeParameters: Array[PsiTypeParameter] = Array.empty

  override def isEquivalentTo(another: PsiElement): Boolean = {
    PsiClassImplUtil.isClassEquivalentTo(this, another)
  }
}

