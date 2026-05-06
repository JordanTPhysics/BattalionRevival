package com.game.ui;

import javax.swing.Icon;

public class TextureOption<T> {
    private final T value;
    private final String label;
    private final Icon icon;

    public TextureOption(T value, String label, Icon icon) {
        this.value = value;
        this.label = label;
        this.icon = icon;
    }

    public T value() {
        return value;
    }

    public Icon icon() {
        return icon;
    }

    @Override
    public String toString() {
        return label;
    }
}
