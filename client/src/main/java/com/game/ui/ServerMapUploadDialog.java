package com.game.ui;

import com.game.model.map.GameMap;
import com.game.network.client.MapCatalogClient;
import com.game.persistence.MapJsonPersistence;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Uploads the current builder map to the server via {@code POST /api/maps}.
 */
final class ServerMapUploadDialog {

    private static final Pattern SLUG_SAFE = Pattern.compile("^[a-z0-9][a-z0-9-]{1,62}$");

    private ServerMapUploadDialog() {
    }

    static void open(JFrame owner, GameMap map, String suggestedNameStem) {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(4, 4, 4, 4);
        gc.anchor = GridBagConstraints.WEST;

        gc.gridx = 0;
        gc.gridy = 0;
        panel.add(new JLabel("Server URL"), gc);
        gc.gridx = 1;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.weightx = 1;
        JTextField serverField = new JTextField("http://localhost:8080", 28);
        panel.add(serverField, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.weightx = 0;
        gc.fill = GridBagConstraints.NONE;
        panel.add(new JLabel("Slug"), gc);
        gc.gridx = 1;
        JTextField slugField = new JTextField(slugFromStem(suggestedNameStem), 20);
        panel.add(slugField, gc);

        gc.gridy++;
        gc.gridx = 0;
        panel.add(new JLabel("Owner label"), gc);
        gc.gridx = 1;
        JTextField ownerField = new JTextField(System.getProperty("user.name", "anonymous"), 20);
        panel.add(ownerField, gc);

        gc.gridy++;
        gc.gridx = 0;
        gc.gridwidth = 2;
        JLabel hint = MilitaryComponents.mutedLabel(
            "Slug: lowercase letters, digits, hyphens only (2–63 chars). Must be unique on the server."
        );
        panel.add(hint, gc);

        int result = JOptionPane.showConfirmDialog(
            owner,
            panel,
            "Upload map to server",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        String slug = slugField.getText().trim().toLowerCase(Locale.ROOT);
        if (!SLUG_SAFE.matcher(slug).matches()) {
            JOptionPane.showMessageDialog(
                owner,
                "Invalid slug. Use 2–63 characters: start with a letter or digit, then letters, digits, or hyphens.",
                "Upload",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }

        final String serverUrl = serverField.getText().trim();
        final String ownerLabel = ownerField.getText().trim();

        CompletableFuture.supplyAsync(() -> {
            try {
                String mapJson = MapJsonPersistence.serialize(map);
                return MapCatalogClient.uploadMap(serverUrl, slug, ownerLabel, mapJson);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).whenComplete((msg, err) -> SwingUtilities.invokeLater(() -> {
            if (err != null) {
                Throwable c = err.getCause() != null ? err.getCause() : err;
                String detail = c.getMessage() != null ? c.getMessage() : c.toString();
                JOptionPane.showMessageDialog(owner, detail, "Upload failed", JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(owner, msg, "Upload complete", JOptionPane.INFORMATION_MESSAGE);
            }
        }));
    }

    /** Best-effort slug from map name field or file stem (server may still reject). */
    private static String slugFromStem(String stem) {
        if (stem == null || stem.isBlank()) {
            return "my-map";
        }
        String s = stem.toLowerCase(Locale.ROOT).trim()
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
        if (s.isEmpty()) {
            return "my-map";
        }
        if (s.length() > 63) {
            s = s.substring(0, 63).replaceAll("-+$", "");
        }
        if (s.length() < 2) {
            return "map-" + s;
        }
        return s;
    }
}
