package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.tree.TokenSet;

/**
 * User: Dmitry Naydanov
 * Date: 3/25/13
 */
abstract public class MemberElementTypesExtension {
  public static ExtensionPointName<MemberElementTypesExtension> EP_NAME = ExtensionPointName.create("org.intellij.scala.memberElementTypesExtension");
  
  public TokenSet getMemberElementTypes() {
    return TokenSet.EMPTY;
  }
  
  public static TokenSet getAllElementTypes() {
    TokenSet types = TokenSet.EMPTY;
    
    for (MemberElementTypesExtension extension : EP_NAME.getExtensions()) {
      types = TokenSet.orSet(types, extension.getMemberElementTypes());
    }
    
    return types;
  }
}
