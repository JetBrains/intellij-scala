package org.jetbrains.plugins.scala.project.template;

import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
* @author Pavel Fatin
*/
public class SelectionDialog<T> extends DialogWrapper {
    private JLabel myLabel;
    private JComboBox<T> myComboBox;

    public SelectionDialog(JComponent parent, String title, String name, T[] values) {
        super(parent, false);
        setTitle(title);
        myLabel = new JLabel(name);
        myComboBox = new JComboBox<T>(new DefaultComboBoxModel<T>(values));
        myLabel.setLabelFor(myComboBox);
        init();
    }

    protected JComponent createCenterPanel() {
        JPanel panel = new JPanel(new FlowLayout());
        panel.add(myLabel);
        panel.add(myComboBox);
        return panel;
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return myComboBox;
    }

    public T getSelectedValue() {
        return (T) myComboBox.getSelectedItem();
    }
}
