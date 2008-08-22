package org.jetbrains.plugins.scala.lang.parser;

import com.intellij.lang.ParserDefinition;
import com.intellij.lang.ASTNode;

public abstract class ScalaParserDefinitionWrapper implements ParserDefinition{

  public SpaceRequirements spaceExistanceTypeBetweenTokens(ASTNode astNode, ASTNode astNode1) {
    return ParserDefinition.SpaceRequirements.MAY;
  }
}
