package com.ganguo.plugin.ui;

import javax.swing.*;

public class AddMsgForm implements BaseForm {

    private JPanel mainPanel;
    private JTextField mKeyField;
    private JTextField mValueField;

    @Override
    public JPanel getMainPanel() {
        return mainPanel;
    }

    public JTextField getKeyField() {
        return mKeyField;
    }

    public JTextField getValueField() {
        return mValueField;
    }
}
