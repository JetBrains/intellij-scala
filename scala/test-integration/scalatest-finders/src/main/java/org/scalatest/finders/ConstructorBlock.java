/*
 * Copyright 2001-2008 Artima, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.scalatest.finders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConstructorBlock implements AstNode {
  
  private final String className;
  private final AstNode parent;
  private final List<AstNode> children;
    
  public ConstructorBlock(String className, AstNode parent, AstNode[] childrenArr) {
    this.className = className;
    this.parent = parent;
    if (parent != null)
      parent.addChild(this);
    children = new ArrayList<>();
    children.addAll(Arrays.asList(childrenArr));
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
    return "constructor";
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
}
