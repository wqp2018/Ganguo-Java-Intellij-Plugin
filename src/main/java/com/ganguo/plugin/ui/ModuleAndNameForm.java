package com.ganguo.plugin.ui;

import javax.swing.*;

public class ModuleAndNameForm implements BaseForm {
    private JTextField mModuleField;
    private JTextField mNameField;
    private JPanel mainPanel;

    public JTextField getModuleField() {
        return mModuleField;
    }

    public JTextField getNameField() {
        return mNameField;
    }

    @Override
    public JPanel getMainPanel() {
        return mainPanel;
    }
}
