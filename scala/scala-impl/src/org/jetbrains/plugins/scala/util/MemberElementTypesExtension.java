package org.jetbrains.plugins.scala.util;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
abstract public class MemberElementTypesExtension {
  public static ExtensionPointName<MemberElementTypesExtension> EP_NAME = ExtensionPointName.create("org.intellij.scala.memberElementTypesExtension");
  
  public TokenSet getMemberElementTypes() {
    return TokenSet.EMPTY;
  }
  
  public static TokenSet getExtraMemberTypes() {
    TokenSet types = TokenSet.EMPTY;
    
    for (MemberElementTypesExtension extension : EP_NAME.getExtensions()) {
      types = TokenSet.orSet(types, extension.getMemberElementTypes());
    }
    
    return types;
  }
}
