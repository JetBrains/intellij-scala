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

public class MethodDefinition implements AstNode {
  
  private final String className;
  private final AstNode parent;
  private final List<AstNode> children;
  private final String name;
  private final String[] paramTypes;
    
  public MethodDefinition(String className, AstNode parent, AstNode[] children, String name, String... paramTypes) {
    this.className = className;
    this.parent = parent;
    if (parent != null)
      parent.addChild(this);
    this.children = new ArrayList<>();
    this.children.addAll(Arrays.asList(children));
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
