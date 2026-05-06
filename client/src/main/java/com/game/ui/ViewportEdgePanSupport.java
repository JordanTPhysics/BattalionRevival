package com.game.ui;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Dialog;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;

/**
 * Hides scrollbars and pans the {@link JScrollPane} viewport when the pointer lingers near an edge.
 * Uses {@link java.awt.MouseInfo} each tick so panning works while the cursor is over a large inner
 * component (the map), not only over empty viewport chrome.
 * <p>
 * UX: wide soft edge band, speed increases toward the border (quadratic ease), capped per tick;
 * mouse wheel scrolling on the scroll pane is disabled so the map can use the wheel for zoom.
 * Arrow keys nudge the view when focus is in the scroll pane's subtree (not on the sibling toolbar).
 */
public final class ViewportEdgePanSupport {

    private static final int DEFAULT_EDGE_PX = 44;
    private static final int DEFAULT_MAX_STEP = 16;
    private static final int KEYBOARD_STEP = 56;

    private final JScrollPane scrollPane;
    private final int edgePixels;
    private final int maxStep;
    private final Timer timer;

    private ViewportEdgePanSupport(JScrollPane scrollPane, int edgePixels, int maxStep) {
        this.scrollPane = scrollPane;
        this.edgePixels = edgePixels;
        this.maxStep = maxStep;
        this.timer = new Timer(16, this::onTimerTick);
    }

    public static ViewportEdgePanSupport install(JScrollPane scrollPane) {
        return install(scrollPane, DEFAULT_EDGE_PX, DEFAULT_MAX_STEP);
    }

    public static ViewportEdgePanSupport install(JScrollPane scrollPane, int edgePixels, int maxStep) {
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scrollPane.setWheelScrollingEnabled(false);

        ViewportEdgePanSupport support = new ViewportEdgePanSupport(scrollPane, edgePixels, maxStep);
        support.timer.start();

        installKeyboardNudges(scrollPane);

        scrollPane.addHierarchyListener(ev -> {
            if ((ev.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                && !scrollPane.isDisplayable()) {
                support.dispose();
            }
        });

        return support;
    }

    private static void installKeyboardNudges(JScrollPane scrollPane) {
        int c = JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT;
        InputMap im = scrollPane.getInputMap(c);
        ActionMap am = scrollPane.getActionMap();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, 0), "edgePanLeft");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, 0), "edgePanRight");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "edgePanUp");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "edgePanDown");
        am.put("edgePanLeft", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                panBy(scrollPane, -KEYBOARD_STEP, 0);
            }
        });
        am.put("edgePanRight", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                panBy(scrollPane, KEYBOARD_STEP, 0);
            }
        });
        am.put("edgePanUp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                panBy(scrollPane, 0, -KEYBOARD_STEP);
            }
        });
        am.put("edgePanDown", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                panBy(scrollPane, 0, KEYBOARD_STEP);
            }
        });
    }

    private void onTimerTick(ActionEvent e) {
        if (!scrollPane.isShowing()) {
            return;
        }
        Window window = SwingUtilities.getWindowAncestor(scrollPane);
        if (window == null || !window.isVisible() || !window.isActive()) {
            return;
        }
        Window focused = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusedWindow();
        if (focused instanceof Dialog d && d.isModal() && focused != window) {
            return;
        }

        java.awt.PointerInfo pointer = java.awt.MouseInfo.getPointerInfo();
        if (pointer == null) {
            return;
        }
        Point screen = pointer.getLocation();
        JViewport vp = scrollPane.getViewport();
        if (vp == null) {
            return;
        }
        Point inViewport = new Point(screen);
        SwingUtilities.convertPointFromScreen(inViewport, vp);
        if (!new Rectangle(0, 0, vp.getWidth(), vp.getHeight()).contains(inViewport)) {
            return;
        }

        int dx = edgeDelta(inViewport.x, vp.getWidth());
        int dy = edgeDelta(inViewport.y, vp.getHeight());
        if (dx != 0 || dy != 0) {
            panBy(scrollPane, dx, dy);
        }
    }

    private int edgeDelta(int coord, int extent) {
        int inner = extent - edgePixels;
        if (coord < edgePixels) {
            double depth = (edgePixels - coord) / (double) edgePixels;
            return -(int) Math.round(maxStep * ease(depth));
        }
        if (coord > inner) {
            double depth = (coord - inner) / (double) edgePixels;
            return (int) Math.round(maxStep * ease(depth));
        }
        return 0;
    }

    private static double ease(double t) {
        t = Math.min(1.0, Math.max(0.0, t));
        return t * t;
    }

    private static void panBy(JScrollPane scrollPane, int dx, int dy) {
        JViewport vp = scrollPane.getViewport();
        JComponent view = (JComponent) vp.getView();
        if (view == null) {
            return;
        }
        Point pos = vp.getViewPosition();
        int vw = vp.getWidth();
        int vh = vp.getHeight();
        int prefW = view.getWidth();
        int prefH = view.getHeight();
        int maxX = Math.max(0, prefW - vw);
        int maxY = Math.max(0, prefH - vh);
        pos.x = Math.max(0, Math.min(maxX, pos.x + dx));
        pos.y = Math.max(0, Math.min(maxY, pos.y + dy));
        vp.setViewPosition(pos);
    }

    public void dispose() {
        timer.stop();
    }

    public static void uninstall(ViewportEdgePanSupport support) {
        if (support != null) {
            support.dispose();
        }
    }
}
