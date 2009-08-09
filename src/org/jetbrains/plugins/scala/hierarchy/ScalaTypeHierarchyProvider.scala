package org.jetbrains.plugins.scala
package hierarchy


import com.intellij.codeInsight.{TargetElementUtilBase, TargetElementUtil}
import com.intellij.openapi.actionSystem.{DataContext, PlatformDataKeys, LangDataKeys}
import com.intellij.psi.{PsiElement, PsiDocumentManager}
import com.intellij.ide.hierarchy.`type`.JavaTypeHierarchyProvider
import lang.psi.api.toplevel.typedef.ScTypeDefinition
/**
 * User: Alexander Podkhalyuzin
 * Date: 09.06.2009
 */

class ScalaTypeHierarchyProvider extends JavaTypeHierarchyProvider