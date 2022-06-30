package org.scalatest.finders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClassDefinition implements AstNode {

  private final String className;
  private final AstNode parent;
  private final List<AstNode> children;
  private final String name;
  private final String[] paramTypes;
  
  public ClassDefinition(String className, AstNode parent, AstNode[] childrenArr, String name, String... paramTypes) {
    this.className = className;
    this.parent = parent;
    if (parent != null)
      parent.addChild(this);
    children = new ArrayList<>();
    children.addAll(Arrays.asList(childrenArr));
    this.name = name;
    this.paramTypes = paramTypes;
  }
  
  @Override
  public String className() {
    return className;
  }
    
  @Override
  public AstNode parent() {
    return parent;
  }
    
  @Override
  public AstNode[] children() {
    return children.toArray(new AstNode[0]);
  }
    
  @Override
  public String name() {
    return name;
  }
    
  @Override
  public void addChild(AstNode node) {
    if (!children.contains(node)) 
      children.add(node);
  }

  @Override
  public boolean canBePartOfTestName() {
    return true;
  }

  public String[] paramTypes() {
    return paramTypes;
  }
}
