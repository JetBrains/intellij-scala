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

import com.intellij.facet.Facet;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.scala.ScalaBundle;

import javax.swing.*;

/**
 * @author ilyas
 */
public class ScalaFacetTab extends FacetEditorTab {

  public static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.scala.config.ui.ScalaFacetTab");

  private Module myModule;
  private JPanel myPanel;
  private FacetEditorContext myEditorContext;
  private FacetValidatorsManager myValidatorsManager;

  private LibraryTable.Listener myLibraryListener;

  public ScalaFacetTab(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    myModule = editorContext.getModule();
    myEditorContext = editorContext;
    myValidatorsManager = validatorsManager;
    reset();
    myLibraryListener = new MyLibraryTableListener();
    LibraryTablesRegistrar.getInstance().getLibraryTable().addListener(myLibraryListener);
    ProjectLibraryTable.getInstance(myModule.getProject()).addListener(myLibraryListener);
  }

  @Nls
  public String getDisplayName() {
    return ScalaBundle.message("scala.sdk.configuration");
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    //todo
    return false;
  }

  @Override
  public String getHelpTopic() {
    return super.getHelpTopic();
  }

  public void onFacetInitialized(@NotNull Facet facet) {
    fireRootsChangedEvent();
  }

  private void fireRootsChangedEvent() {
/*    final ScalaSDKComboBox.DefaultScalaSDKComboBoxItem selectedItem =
      (ScalaSDKComboBox.DefaultScalaSDKComboBoxItem)myComboBox.getSelectedItem();
    final Module module = myEditorContext.getModule();
    if (module != null) {
      final Project project = module.getProject();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          ScalaSDK sdk = null;
          if (selectedItem instanceof ScalaSDKComboBox.ScalaSDKPointerItem) {
            ScalaSDKComboBox.ScalaSDKPointerItem pointerItem = (ScalaSDKComboBox.ScalaSDKPointerItem)selectedItem;
            final ScalaSDKPointer pointer = pointerItem.getPointer();
            String name = pointerItem.getName();
            String path = pointerItem.getPath();
            Library library = ScalaConfigUtils.createScalaLibrary(path, name, project, true, pointer.isProjectLib());
            if (library != null) {
              sdk = new ScalaSDK(library, myModule, pointer.isProjectLib());
            }
          } else {
            sdk = selectedItem.getScalaSDK();
          }
          ScalaConfigUtils.updateScalaLibInModule(module, sdk);

          // create other libraries by their pointers
          for (int i = 0; i < myComboBox.getItemCount(); i++) {
            Object item = myComboBox.getItemAt(i);
            if (item != selectedItem && item instanceof ScalaSDKComboBox.ScalaSDKPointerItem) {
              ScalaSDKComboBox.ScalaSDKPointerItem pointerItem = (ScalaSDKComboBox.ScalaSDKPointerItem)item;
              final ScalaSDKPointer pointer = pointerItem.getPointer();
              String name = pointerItem.getName();
              String path = pointerItem.getPath();
              ScalaConfigUtils.createScalaLibrary(path, name, project, true, pointer.isProjectLib());
            }
          }
        }
      });
    }*/
  }

  public void apply() throws ConfigurationException {
  }

  public void reset() {
/*
    Module module = myEditorContext.getModule();
    if (module != null && module.isDisposed()) return;
    if (module != null && FacetManager.getInstance(module).getFacetByType(ScalaFacet.ID) != null) {
      Library[] libraries = ScalaConfigUtils.getScalaCompilerLibrariesByModule(myEditorContext.getModule());
      if (libraries.length == 0) {
        myComboBox.setSelectedIndex(0);
        oldScalaLibName = newScalaLibName;
      } else {
        Library library = libraries[0];
        if (library != null && library.getName() != null) {
          myComboBox.selectLibrary(library);
          oldScalaLibName = newScalaLibName;
        }
      }
    } else {
      Library library = LibrariesUtil.getLibraryByName(ScalaApplicationSettings.getInstance().DEFAULT_SCALA_LIB_NAME);
      if (library != null) {
        myComboBox.selectLibrary(library);
      }
    }
*/
  }

  public void disposeUIResources() {
    if (myLibraryListener != null) {
      LibraryTablesRegistrar.getInstance().getLibraryTable().removeListener(myLibraryListener);
    }
  }

  private void createUIComponents() {
  }

  private void setUpComponents() {

/*
    if (myEditorContext != null && myEditorContext.getProject() != null) {
      final Project project = myEditorContext.getProject();
      myNewButton.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
            public boolean isFileSelectable(VirtualFile file) {
              return super.isFileSelectable(file) && ScalaConfigUtils.isScalaSdkHome(file);
            }
          };
          final FileChooserDialog fileChooserDialog = FileChooserFactory.getInstance().createFileChooser(descriptor, project);
          final VirtualFile[] files = fileChooserDialog.choose(null, project);
          if (files.length > 0) {
            String path = files[0].getPath();
            if (ValidationResult.OK == ScalaConfigUtils.isScalaSdkHome(path)) {
              Collection<String> versions = ScalaConfigUtils.getScalaVersions(myModule);
              String version = ScalaConfigUtils.getScalaSDKVersion(path);
              boolean addVersion = !versions.contains(version) ||
                                   Messages.showOkCancelDialog(ScalaBundle.message("duplicate.scala.lib.version.add", version),
                                                               ScalaBundle.message("duplicate.scala.lib.version"),
                                                               Icons.BIG_ICON) == 0;

              if (addVersion && !ScalaConfigUtils.UNDEFINED_VERSION.equals(version)) {
                String name = myComboBox.generatePointerName(version);
                final CreateLibraryDialog dialog = new CreateLibraryDialog(project, name);
                dialog.show();
                if (dialog.isOK()) {
                  myComboBox.addSdk(new ScalaSDKPointer(name, path, version, dialog.isInProject()));
                  newScalaLibName = name;
                }
              }
            } else {
              Messages.showErrorDialog(ScalaBundle.message("invalid.scala.sdk.path.message"),
                                       ScalaBundle.message("invalid.scala.sdk.path.text"));
            }
          }
        }
      });
    }
*/
  }

  private class MyLibraryTableListener implements LibraryTable.Listener {

    public void afterLibraryAdded(Library newLibrary) {
    }

    public void afterLibraryRenamed(Library library) {
    }

    public void afterLibraryRemoved(Library library) {
    }

    public void beforeLibraryRemoved(Library library) {
    }

  }


}
