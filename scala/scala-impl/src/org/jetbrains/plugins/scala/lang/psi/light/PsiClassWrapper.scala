package org.jetbrains.plugins.scala.lang.psi.light

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

import javax.swing._
import org.jetbrains.plugins.scala.caches.BlockModificationTracker
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.lexer.ScalaModifier
import org.jetbrains.plugins.scala.lang.psi.adapters.PsiClassAdapter
import org.jetbrains.plugins.scala.lang.psi.api.PropertyMethods._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotationsHolder
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScTypedDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.{ScClass, ScObject, ScTemplateDefinition, ScTrait}
import org.jetbrains.plugins.scala.lang.psi.impl.toplevel.typedef.TypeDefinitionMembers
import org.jetbrains.plugins.scala.lang.resolve.processor.BaseProcessor
import org.jetbrains.plugins.scala.macroAnnotations.Cached

import java.util
import javax.swing._
import _root_.scala.collection.mutable.ArrayBuffer

class PsiClassWrapper(val definition: ScTemplateDefinition,
                      private var qualName: String,
                      private var name: String) extends LightElement(definition.getManager, definition.getLanguage) with PsiClassAdapter /*with SyntheticElement*/ {
  override def hashCode(): Int = definition.hashCode()

  override def equals(obj: Any): Boolean = {
    obj match {
      case wrapper: PsiClassWrapper =>
        definition.equals(wrapper.definition) && qualName == wrapper.qualName && name == wrapper.name
      case _ => false
    }
  }

  override def getQualifiedName: String = qualName

  override def isInterface: Boolean = false

  override def isAnnotationType: Boolean = false

  override def isEnum: Boolean = false

  override def getExtendsList: PsiReferenceList = null //todo:

  override def getImplementsList: PsiReferenceList = null //todo: ?

  override def getExtendsListTypes: Array[PsiClassType] = Array.empty

  override def getImplementsListTypes: Array[PsiClassType] = Array.empty

  override def getSuperClass: PsiClass = null

  override def getInterfaces: Array[PsiClass] = Array.empty

  override def getSupers: Array[PsiClass] = Array.empty

  override def getSuperTypes: Array[PsiClassType] = Array.empty

  override def psiFields: Array[PsiField] = {
    definition match {
      case _: ScObject => Array.empty
      case _ => definition.getFields //todo:
    }
  }

  override def psiMethods: Array[PsiMethod] = {
    definition match {
      case obj: ScObject =>
        val res = new ArrayBuffer[PsiMethod]()
        TypeDefinitionMembers.getSignatures(obj).allSignatures.foreach {
          this.processWrappersForSignature(_, isStatic = true, isInterface = false)(res += _)
        }
        res.toArray

      case t: ScTrait =>
        val res = new ArrayBuffer[PsiMethod]()

        def addGettersAndSetters(holder: ScAnnotationsHolder, declaredElements: Iterable[ScTypedDefinition]): Unit = {
          val beanProperty = isBeanProperty(holder)
          val booleanBeanProperty = isBooleanBeanProperty(holder)
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

        val members = t.membersWithSynthetic.filterNot(_.hasModifierPropertyScala(ScalaModifier.INLINE))

        members.foreach {
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

  @Cached(BlockModificationTracker(this), this)
  private def getEmptyConstructor: PsiMethod = new EmptyPrivateConstructor(this)

  override def getConstructors: Array[PsiMethod] = {
    Array(getEmptyConstructor)
  }

  override def psiInnerClasses: Array[PsiClass] = {
    definition match {
      case o: ScObject =>
        o.membersWithSynthetic.flatMap {
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

  override def getInitializers: Array[PsiClassInitializer] = Array.empty

  override def getAllFields: Array[PsiField] = {
    PsiClassImplUtil.getAllFields(this)
  }

  override def getAllMethods: Array[PsiMethod] = {
    PsiClassImplUtil.getAllMethods(this)
  }

  override def getAllInnerClasses: Array[PsiClass] = {
    PsiClassImplUtil.getAllInnerClasses(this)
  }

  override def findFieldByName(name: String, checkBases: Boolean): PsiField = {
    PsiClassImplUtil.findFieldByName(this, name, checkBases)
  }

  override def findMethodBySignature(patternMethod: PsiMethod, checkBases: Boolean): PsiMethod = {
    PsiClassImplUtil.findMethodBySignature(this, patternMethod, checkBases)
  }

  override def findMethodsBySignature(patternMethod: PsiMethod, checkBases: Boolean): Array[PsiMethod] = {
    PsiClassImplUtil.findMethodsBySignature(this, patternMethod, checkBases)
  }

  override def findMethodsByName(name: String, checkBases: Boolean): Array[PsiMethod] = {
    PsiClassImplUtil.findMethodsByName(this, name, checkBases)
  }

  override def findMethodsAndTheirSubstitutorsByName(name: String, checkBases: Boolean): util.List[Pair[PsiMethod, PsiSubstitutor]] = {
    PsiClassImplUtil.findMethodsAndTheirSubstitutorsByName(this, name, checkBases)
  }

  override def getAllMethodsAndTheirSubstitutors: util.List[Pair[PsiMethod, PsiSubstitutor]] = {
    PsiClassImplUtil.getAllWithSubstitutorsByMap(this, MemberType.METHOD)
  }

  override def findInnerClassByName(name: String, checkBases: Boolean): PsiClass = {
    PsiClassImplUtil.findInnerByName(this, name, checkBases)
  }

  override def getLBrace: PsiElement = {
    definition.getLBrace
  }

  override def getRBrace: PsiElement = {
    definition.getRBrace
  }

  override def getNameIdentifier: PsiIdentifier = {
    definition.getNameIdentifier
  }

  override def getScope: PsiElement = {
    definition.getScope
  }

  override def isInheritor(baseClass: PsiClass, checkDeep: Boolean): Boolean = {
    definition match {
      case _: ScObject => baseClass.getQualifiedName == "java.lang.Object"
      case _ => false
    }
  }

  override def isInheritorDeep(baseClass: PsiClass, classToByPass: PsiClass): Boolean = isInheritor(baseClass, checkDeep = true)

  override def getContainingClass: PsiClass = {
    definition.getContainingClass
  }

  override def getVisibleSignatures: util.Collection[HierarchicalMethodSignature] = {
    PsiSuperMethodImplUtil.getVisibleSignatures(this)
  }

  override def setName(name: String): PsiElement = {
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
          case _ => PsiUtil.getLanguageLevel(getProject)
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
    definition.resolveScope
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

  override def getModifierList: PsiModifierList = {
    definition.getModifierList
  }

  override def hasModifierProperty(name: String): Boolean = {
    definition.hasModifierProperty(name)
  }

  override def getDocComment: PsiDocComment = {
    definition.getDocComment
  }

  override def isDeprecated: Boolean = {
    definition.isDeprecated
  }

  override def getPresentation: ItemPresentation = {
    definition.getPresentation //todo: ?
  }

  override def navigate(requestFocus: Boolean): Unit = {
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

  override def hasTypeParameters: Boolean = false

  override def getTypeParameterList: PsiTypeParameterList = null

  override def psiTypeParameters: Array[PsiTypeParameter] = Array.empty

  override def isEquivalentTo(another: PsiElement): Boolean = {
    PsiClassImplUtil.isClassEquivalentTo(this, another)
  }
}

object PsiClassWrapper {

  def unapply(wrapper: PsiClassWrapper): Option[ScTemplateDefinition] = Some(wrapper.definition)
}

