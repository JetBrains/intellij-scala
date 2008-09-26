/*
 * Copyright 2000-2008 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.plugins.scala.config.ui;

import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.module.Module;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.scala.ScalaBundle;
import org.jetbrains.plugins.scala.config.ScalaConfigUtils;
import org.jetbrains.plugins.scala.config.ScalaSDK;
import org.jetbrains.plugins.scala.config.util.AbstractSDK;
import org.jetbrains.plugins.scala.config.util.ScalaSDKPointer;
import org.jetbrains.plugins.scala.icons.Icons;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author ilyas
 */
public class ScalaSDKComboBox extends JComboBox {
  private Module myModule;

  void setModule(Module module) {
    myModule = module;
  }

  public ScalaSDKComboBox(final Module module) {
    super(new ScalaSDKComboBoxModel(module));
    myModule = module;
    setRenderer(new ColoredListCellRenderer() {
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (ScalaSDKComboBox.this.isEnabled()) {
          if (value instanceof ScalaSDKPointerItem) {
            ScalaSDKPointer pointer = ((ScalaSDKPointerItem)value).getPointer();
            setIcon(pointer.getIcon());
            append(pointer.getLibraryName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
            append(pointer.getPresentation(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
          } else if (value instanceof DefaultScalaSDKComboBoxItem) {
            DefaultScalaSDKComboBoxItem item = (DefaultScalaSDKComboBoxItem)value;
            final String str = item.toString();
            ScalaSDK sdk = item.getScalaSDK();
            if (sdk != null) {
              setIcon(sdk.getIcon());
              append(sdk.getLibraryName(), SimpleTextAttributes.REGULAR_ATTRIBUTES);
              final String version = sdk.getSdkVersion() != null ? sdk.getPresentation() : " (undefined version)";
              append(version, SimpleTextAttributes.GRAYED_ATTRIBUTES);
            } else { // No Scala SDK
              setIcon(Icons.NO_SCALA_SDK);
              append(str, SimpleTextAttributes.ERROR_ATTRIBUTES);
            }
          }
        }
      }
    });
  }

  @Nullable
  public ScalaSDK getSdkAt(int i) {
    Object o = getItemAt(i);
    if (o instanceof DefaultScalaSDKComboBoxItem) {
      DefaultScalaSDKComboBoxItem item = (DefaultScalaSDKComboBoxItem)o;
      return item.getScalaSDK();
    }
    return null;
  }

  public void addSdk(@NotNull ScalaSDKPointer pointer) {
    ArrayList<ScalaSDKPointer> pointers = new ArrayList<ScalaSDKPointer>();
    for (ScalaSDKPointerItem sdkPointer : getAllPointerItems()) {
      pointers.add(sdkPointer.getPointer());
    }
    pointers.add(pointer);
    removeAllItems();
    setModel(new ScalaSDKComboBoxModel(myModule, pointers.toArray(new ScalaSDKPointer[pointers.size()])));
    insertItemAt(new NoScalaSDKComboBoxItem(), 0);
    selectPointer(pointer);
  }

  public void refresh() {
    ArrayList<ScalaSDKPointer> pointers = new ArrayList<ScalaSDKPointer>();
    for (ScalaSDKPointerItem sdkPointer : getAllPointerItems()) {
      pointers.add(sdkPointer.getPointer());
    }
    removeAllItems();
    setModel(new ScalaSDKComboBoxModel(myModule, pointers.toArray(new ScalaSDKPointer[pointers.size()])));
    insertItemAt(new NoScalaSDKComboBoxItem(), 0);
  }

  public boolean selectLibrary(@NotNull Library library) {
    for (int i = 0; i < getItemCount(); i++) {
      DefaultScalaSDKComboBoxItem item = (DefaultScalaSDKComboBoxItem)getItemAt(i);
      ScalaSDK sdk = item.getScalaSDK();
      if (sdk == null) continue;
      if (library.equals(sdk.getLibrary())) {
        setSelectedIndex(i);
        return true;
      }
    }
    return false;
  }

  private void selectPointer(@NotNull ScalaSDKPointer pointer) {
    for (int i = 0; i < getItemCount(); i++) {
      DefaultScalaSDKComboBoxItem item = (DefaultScalaSDKComboBoxItem)getItemAt(i);
      if (item instanceof ScalaSDKPointerItem) {
        final ScalaSDKPointer sdkPointer = ((ScalaSDKPointerItem)item).getPointer();
        if (sdkPointer.equals(pointer)) {
          setSelectedIndex(i);
          return;
        }
      }
    }
  }

  public String generatePointerName(String version) {
    final List<String> pointerNames = ContainerUtil.map(getAllPointerItems(), new Function<ScalaSDKPointerItem, String>() {
      public String fun(ScalaSDKPointerItem item) {
        final ScalaSDKPointer pointer = item.getPointer();
        if (pointer != null) return pointer.getLibraryName();
        return null;
      }
    });

    List<Object> libNames =
      ContainerUtil.map(ScalaConfigUtils.getAllScalaLibraries(myModule.getProject()), new Function<Library, Object>() {
        public Object fun(Library library) {
          return library.getName();
        }
      });

    String originalName = ScalaConfigUtils.SCALA_LIB_PREFIX + version;
    String newName = originalName;
    int index = 1;
    while (pointerNames.contains(newName) || libNames.contains(newName)) {
      newName = originalName + " (" + index + ")";
      index++;
    }
    return newName;
  }

  public DefaultScalaSDKComboBoxItem[] getAllItems() {
    ArrayList<DefaultScalaSDKComboBoxItem> items = new ArrayList<DefaultScalaSDKComboBoxItem>();
    for (int i = 0; i < getItemCount(); i++) {
      final Object o = getItemAt(i);
      if (o instanceof DefaultScalaSDKComboBoxItem) {
        items.add(((DefaultScalaSDKComboBoxItem)o));
      }
    }
    return items.toArray(new DefaultScalaSDKComboBoxItem[items.size()]);
  }

  public ScalaSDKPointerItem[] getAllPointerItems() {
    final List<DefaultScalaSDKComboBoxItem> list = ContainerUtil.findAll(getAllItems(), new Condition<DefaultScalaSDKComboBoxItem>() {
      public boolean value(DefaultScalaSDKComboBoxItem item) {
        return item instanceof ScalaSDKPointerItem;
      }
    });
    return ContainerUtil.map2Array(list, ScalaSDKPointerItem.class, new Function<DefaultScalaSDKComboBoxItem, ScalaSDKPointerItem>() {
      public ScalaSDKPointerItem fun(DefaultScalaSDKComboBoxItem item) {
        return ((ScalaSDKPointerItem)item);
      }
    });
  }

  static class ScalaSDKComboBoxModel extends DefaultComboBoxModel {
    private final Module myModule;

    public ScalaSDKComboBoxModel(Module module, ScalaSDKPointer... sdkPointers) {
      super();
      myModule = module;
      if (module == null) return;
      ArrayList<AbstractSDK> sdkList = new ArrayList<AbstractSDK>();
      final ScalaSDK[] sdks = ScalaConfigUtils.getScalaSDKs(myModule);
      sdkList.addAll(Arrays.asList(sdks));
      for (AbstractSDK newSDK : sdkPointers) {
        if (!sdkList.contains(newSDK)) {
          sdkList.add(newSDK);
        }
      }
      AbstractSDK[] abstractSDKs = sdkList.toArray(new AbstractSDK[sdkList.size()]);
      Arrays.sort(abstractSDKs, new Comparator<AbstractSDK>() {
        public int compare(final AbstractSDK s1, final AbstractSDK s2) {
          return -s1.getLibraryName().compareToIgnoreCase(s2.getLibraryName());
        }
      });
      for (AbstractSDK sdk : abstractSDKs) {
        if (sdk instanceof ScalaSDK) {
          addElement(new ScalaSDKComboBoxItem(((ScalaSDK)sdk)));
        } else if (sdk instanceof ScalaSDKPointer) {
          addElement(new ScalaSDKPointerItem(((ScalaSDKPointer)sdk)));
        }
      }
    }
  }

  public static abstract class DefaultScalaSDKComboBoxItem {
    public ScalaSDK getScalaSDK() {
      return null;
    }

    public String getName() {
      return null;
    }
  }

  public static class ScalaSDKPointerItem extends DefaultScalaSDKComboBoxItem {
    private final ScalaSDKPointer myPointer;

    public ScalaSDKPointerItem(ScalaSDKPointer pointer) {
      myPointer = pointer;
    }

    public String getName() {
      return myPointer.getLibraryName();
    }

    public String getPath() {
      return myPointer.getPath();
    }

    public ScalaSDKPointer getPointer() {
      return myPointer;
    }
  }

  public static class ScalaSDKComboBoxItem extends DefaultScalaSDKComboBoxItem {

    private final ScalaSDK myScalaSDK;

    public ScalaSDKComboBoxItem(ScalaSDK scalaSDK) {
      myScalaSDK = scalaSDK;
    }

    public ScalaSDK getScalaSDK() {
      return myScalaSDK;
    }

    public String getName() {
      return myScalaSDK.getLibraryName();
    }

  }

  public static class NoScalaSDKComboBoxItem extends DefaultScalaSDKComboBoxItem {
    public String toString() {
      return ScalaBundle.message("scala.sdk.combo.box.project.item");
    }
  }

}
