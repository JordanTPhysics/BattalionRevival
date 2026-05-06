package com.game.ui;

import com.game.audio.ClasspathWavPlayer;
import com.game.audio.UnitSoundEffects;
import com.game.audio.UnitSoundPaths;
import com.game.engine.PlayableGameSession;
import com.game.engine.ai.AiAction;
import com.game.engine.ai.AiEngine;
import com.game.engine.ai.AiStepRunner;
import com.game.engine.ai.AiTurnExecutor;
import com.game.pathfinding.UnitMovementPaths;
import com.game.model.Player;
import com.game.model.map.GameMap;
import com.game.model.map.TerrainType;
import com.game.model.map.Tile;
import com.game.model.structures.Structure;
import com.game.model.structures.StructureType;
import com.game.model.units.EngagementRules;
import com.game.model.units.FacingDirection;
import com.game.model.units.Unit;
import com.game.model.units.UnitAbilities;
import com.game.model.units.UnitType;
import com.game.network.protocol.MatchSnapshot;
import com.game.systems.CombatTerrain;
import com.game.systems.MovementReach;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;

import com.game.network.client.OnlineMatchCoordinator;
import java.util.function.Consumer;

public class GameWindow extends JFrame {
    /** Pacing between AI actions (ms), after each step finishes presenting. */
    private static final int AI_STEP_DELAY_MS = 100;
    /** Extra pause when one AI seat ends and the next active seat is also AI (lets SFX drain). */
    private static final int AI_TO_AI_HANDOFF_MS = 2000;

    private final String levelName;
    private PlayableGameSession session;
    private final GameMapPanel gamePanel;
    private final GameInfoPanel infoPanel;
    private final AssetManager gameAssetManager;
    private final AiEngine aiEngine;
    private final AiTurnExecutor aiExecutor;
    private OnlineMatchCoordinator onlineCoordinator;
    /** While {@code true}, an AI player's turn is mid-execution and human input is locked out. */
    private boolean aiTurnInProgress;
    /** When non-null, waits before {@link AiTurnExecutor#start()} after an AI-to-AI turn change. */
    private Timer deferredAiStartTimer;

    private JLabel topMissionLabel;
    private JLabel topTurnLabel;
    private JPanel topFactionPillSlot;
    private JLabel topMoneyLabel;
    private JLabel topZoomLabel;
    private MilitaryButton endTurnButton;
    /** Prevents stacking victory/draw dialogs on repeated HUD refresh. */
    private boolean postMatchDialogShown;

    public GameWindow(GameMap map, Runnable onBackToMenu) {
        this(map, onBackToMenu, "Untitled Level");
    }

    public GameWindow(GameMap map, Runnable onBackToMenu, String levelName) {
        this(map, onBackToMenu, levelName, null);
    }

    public GameWindow(GameMap map, Runnable onBackToMenu, String levelName, Set<Integer> configuredAiPlayerIndices) {
        this(new PlayableGameSession(map), onBackToMenu, levelName, configuredAiPlayerIndices, null);
    }

    public GameWindow(
        PlayableGameSession session,
        Runnable onBackToMenu,
        String levelName,
        OnlineMatchCoordinator onlineCoordinator
    ) {
        this(session, onBackToMenu, levelName, Collections.emptySet(), onlineCoordinator);
    }

    private GameWindow(
        PlayableGameSession session,
        Runnable onBackToMenu,
        String levelName,
        Set<Integer> configuredAiPlayerIndices,
        OnlineMatchCoordinator onlineCoordinator
    ) {
        super("Battalion Revival - Game");
        this.levelName = levelName;
        this.session = session;
        this.onlineCoordinator = onlineCoordinator;

        AssetManager assetManager = new AssetManager();
        this.gameAssetManager = assetManager;
        ClasspathWavPlayer wavPlayer = new ClasspathWavPlayer();
        wavPlayer.prewarm(UnitSoundPaths.allKnownClasspaths());
        UnitSoundEffects unitSoundEffects = new UnitSoundEffects(wavPlayer);
        GameMap map = session.getMap();
        this.infoPanel = new GameInfoPanel(map, session);
        this.gamePanel = new GameMapPanel(
            map,
            assetManager,
            sel -> {
                infoPanel.updateSelection(sel);
                onMapTileSelectedForFactory(sel);
            },
            session,
            unitSoundEffects
        );
        JScrollPane scroller = new JScrollPane(gamePanel);
        scroller.setBorder(BorderFactory.createEmptyBorder());
        scroller.getViewport().setBackground(Theme.BACKGROUND);
        ViewportEdgePanSupport.install(scroller);

        getContentPane().setBackground(Theme.BACKGROUND);
        setLayout(new BorderLayout());
        add(scroller, BorderLayout.CENTER);
        add(buildTopBar(onBackToMenu, gamePanel), BorderLayout.NORTH);
        add(infoPanel, BorderLayout.SOUTH);

        Set<Integer> aiPlayerIndices;
        if (onlineCoordinator != null) {
            aiPlayerIndices = Collections.emptySet();
        } else if (configuredAiPlayerIndices != null) {
            aiPlayerIndices = new LinkedHashSet<>(configuredAiPlayerIndices);
        } else {
            aiPlayerIndices = new LinkedHashSet<>();
            int teamCount = session.getPlayers().size();
            for (int i = 1; i < teamCount; i++) {
                aiPlayerIndices.add(i);
            }
        }
        this.aiEngine = new AiEngine(session, aiPlayerIndices);
        this.aiExecutor = new AiTurnExecutor(
            aiEngine,
            session,
            this::presentAiStep,
            AI_STEP_DELAY_MS,
            this::onAiStepApplied,
            this::performEndTurn
        );

        refreshHud();

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(960, 720));
        setLocationRelativeTo(null);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cancelDeferredAiStart();
                if (onlineCoordinator != null) {
                    onlineCoordinator.close();
                }
                if (aiExecutor != null) {
                    aiExecutor.stop();
                }
            }
        });

        // If the very first turn belongs to an AI seat (e.g. a custom map config), kick the
        // executor as soon as the EDT is idle so the UI is visible before actions land.
        SwingUtilities.invokeLater(() -> maybeStartAiTurn(false));
    }

    /**
     * Presents one AI step with the same move glide, combat animation, and SFX as human play;
     * {@code whenComplete} runs on the EDT when the step is finished (including after combat).
     */
    private void presentAiStep(AiAction action, Runnable whenComplete) {
        gamePanel.presentAiStep(action, whenComplete);
    }

    /** Repaint hooks invoked between AI steps so the human can see each action's result. */
    private void onAiStepApplied() {
        refreshHud();
        gamePanel.clearSelectionExternal();
        gamePanel.repaint();
    }

    /**
     * Single end-of-turn entry point shared by the End Turn button and the AI executor: flushes
     * any in-flight combat animation, advances the session (resolving captures + applying the
     * next player's income), refreshes UI, then hands control to whoever's next (AI or human).
     */
    private void performEndTurn() {
        if (session.matchFinished()) {
            cancelDeferredAiStart();
            aiTurnInProgress = false;
            if (aiExecutor != null) {
                aiExecutor.stop();
            }
            refreshHud();
            return;
        }
        gamePanel.forceFlushCombatAnimationIfRunning();
        if (onlineCoordinator != null && !session.matchFinished()) {
            onlineCoordinator.requestEndTurn();
            return;
        }
        boolean previousSeatWasAi = aiEngine != null && aiEngine.controlsActivePlayer();
        session.endTurn();
        refreshHud();
        gamePanel.clearSelectionExternal();
        gamePanel.repaint();
        maybeStartAiTurn(previousSeatWasAi);
    }

    void bindAuthoritativeSession(MatchSnapshot snapshot) {
        PlayableGameSession next = PlayableGameSession.fromAuthoritativeSnapshot(snapshot);
        this.session = next;
        gamePanel.bindAuthoritativeSession(next);
        infoPanel.bindAuthoritativeSession(next);
        refreshHud();
        gamePanel.repaint();
        SwingUtilities.invokeLater(() -> maybeStartAiTurn(false));
    }

    private void cancelDeferredAiStart() {
        if (deferredAiStartTimer != null) {
            deferredAiStartTimer.stop();
            deferredAiStartTimer = null;
        }
    }

    /**
     * If the now-active player is AI, lock input and start the executor; otherwise unlock.
     *
     * @param previousSeatWasAi {@code true} when the faction that just ended its turn was AI-driven
     *                          (used to insert a longer handoff pause before the next AI runs).
     */
    private void maybeStartAiTurn(boolean previousSeatWasAi) {
        cancelDeferredAiStart();
        if (session.matchFinished()) {
            if (aiExecutor != null) {
                aiExecutor.stop();
            }
            aiTurnInProgress = false;
            updateEndTurnButtonEnabled();
            refreshHud();
            return;
        }
        if (aiEngine != null && aiEngine.controlsActivePlayer()) {
            aiTurnInProgress = true;
            updateEndTurnButtonEnabled();
            if (previousSeatWasAi) {
                deferredAiStartTimer = new Timer(AI_TO_AI_HANDOFF_MS, ev -> {
                    deferredAiStartTimer = null;
                    if (aiTurnInProgress
                        && aiEngine != null
                        && aiEngine.controlsActivePlayer()
                        && !session.matchFinished()
                        && aiExecutor != null) {
                        aiExecutor.start();
                    }
                });
                deferredAiStartTimer.setRepeats(false);
                deferredAiStartTimer.start();
            } else if (aiExecutor != null) {
                aiExecutor.start();
            }
        } else {
            aiTurnInProgress = false;
            updateEndTurnButtonEnabled();
        }
    }

    private void updateEndTurnButtonEnabled() {
        if (endTurnButton != null) {
            endTurnButton.setEnabled(!aiTurnInProgress && !session.matchFinished());
        }
    }

    /** Whether human input on the map / HUD should be ignored right now. */
    boolean isAiTurnInProgress() {
        return aiTurnInProgress;
    }

    /** Refresh both the top-bar HUD widgets and the bottom info panel after any turn change. */
    private void refreshHud() {
        Optional<Player> victorOpt = session.getWinnerIfAny();
        Player hudPlayer = victorOpt.orElse(session.getActivePlayer());

        if (topMissionLabel != null) {
            topMissionLabel.setText(levelName == null ? "" : levelName.toUpperCase());
        }
        if (topTurnLabel != null) {
            topTurnLabel.setText("TURN " + session.getRoundNumber());
        }
        if (topMoneyLabel != null) {
            topMoneyLabel.setText(hudPlayer == null ? "\u2014" : "$" + hudPlayer.getMoney());
        }
        if (topFactionPillSlot != null) {
            topFactionPillSlot.removeAll();
            if (session.matchFinished()) {
                if (victorOpt.isPresent()) {
                    Player w = victorOpt.get();
                    topFactionPillSlot.add(MilitaryComponents.pill(
                        w.getName().toUpperCase() + " \u00B7 WINNER",
                        factionAccent(w)
                    ));
                } else {
                    topFactionPillSlot.add(MilitaryComponents.pill(
                        "NO SURVIVORS",
                        Theme.WARNING
                    ));
                }
            } else if (hudPlayer != null) {
                topFactionPillSlot.add(MilitaryComponents.pill(
                    hudPlayer.getName().toUpperCase(),
                    factionAccent(hudPlayer)
                ));
            }
            topFactionPillSlot.revalidate();
            topFactionPillSlot.repaint();
        }
        infoPanel.updateTurn(levelName, session.getRoundNumber());

        maybeShowPostMatchDialog();
        updateEndTurnButtonEnabled();
    }

    /**
     * One-shot UX when the battle ends — either sole survivor wins or mutual elimination leaves no faction.
     */
    private void maybeShowPostMatchDialog() {
        if (!session.matchFinished() || postMatchDialogShown) {
            return;
        }
        postMatchDialogShown = true;
        Optional<Player> w = session.getWinnerIfAny();
        if (w.isPresent()) {
            JOptionPane.showMessageDialog(this, w.get().getName() + " wins \u2014 last team standing.");
        } else {
            JOptionPane.showMessageDialog(this, "All factions have been eliminated. No winner.");
        }
    }

    private static Color factionAccent(Player owner) {
        if (owner == null) {
            return Theme.ACCENT;
        }
        return switch (owner.getName()) {
            case "Red" -> new Color(220, 90, 80);
            case "Blue" -> new Color(80, 150, 220);
            case "Green" -> Theme.ACCENT;
            case "Yellow" -> Theme.WARNING;
            default -> new Color(180, 160, 220);
        };
    }

    private void onMapTileSelectedForFactory(SelectionInfo sel) {
        if (aiTurnInProgress || session.matchFinished()) {
            return;
        }
        Tile t = sel.tile();
        if (t == null) {
            return;
        }
        Structure st = t.getStructure();
        if (st == null || st.getType() != StructureType.Factory) {
            return;
        }
        if (st.getOwner() != session.getActivePlayer()) {
            return;
        }
        Unit occ = t.getUnit();
        // Factory production UI should only open when the factory tile itself is vacant.
        if (occ != null) {
            return;
        }
        FactoryBuildDialog.show(this, session, sel.x(), sel.y(), gameAssetManager, () -> {
            refreshHud();
            gamePanel.repaint();
        });
    }

    private JPanel buildTopBar(Runnable onBackToMenu, GameMapPanel gamePanel) {
        JPanel topBar = new JPanel(new BorderLayout(Theme.SPACING_MD, 0));
        topBar.setBackground(Theme.PANEL);
        topBar.setBorder(BorderFactory.createCompoundBorder(
            Theme.bottomDivider(),
            Theme.padding(Theme.SPACING_SM, Theme.SPACING_MD)
        ));
        topBar.add(buildMissionStrip(), BorderLayout.WEST);
        topBar.add(buildZoomGroup(gamePanel), BorderLayout.CENTER);
        topBar.add(buildActionGroup(onBackToMenu, gamePanel), BorderLayout.EAST);
        return topBar;
    }

    private JComponent buildMissionStrip() {
        JPanel strip = new JPanel();
        strip.setOpaque(false);
        strip.setLayout(new BoxLayout(strip, BoxLayout.X_AXIS));

        topMissionLabel = new JLabel("");
        topMissionLabel.setFont(Theme.fontSectionLabel().deriveFont(11f));
        topMissionLabel.setForeground(Theme.TEXT_SECONDARY);
        strip.add(topMissionLabel);
        strip.add(MilitaryComponents.horizontalGap(Theme.SPACING_MD));
        strip.add(MilitaryComponents.verticalRule());
        strip.add(MilitaryComponents.horizontalGap(Theme.SPACING_MD));

        topTurnLabel = new JLabel("");
        topTurnLabel.setFont(Theme.fontHudBold());
        topTurnLabel.setForeground(Theme.TEXT_PRIMARY);
        strip.add(topTurnLabel);
        strip.add(MilitaryComponents.horizontalGap(Theme.SPACING_SM));

        topFactionPillSlot = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        topFactionPillSlot.setOpaque(false);
        strip.add(topFactionPillSlot);
        strip.add(MilitaryComponents.horizontalGap(Theme.SPACING_MD));
        strip.add(MilitaryComponents.verticalRule());
        strip.add(MilitaryComponents.horizontalGap(Theme.SPACING_MD));

        JLabel moneyKicker = new JLabel("FUNDS");
        moneyKicker.setFont(Theme.fontSectionLabel());
        moneyKicker.setForeground(Theme.TEXT_SECONDARY);
        strip.add(moneyKicker);
        strip.add(MilitaryComponents.horizontalGap(Theme.SPACING_SM));

        topMoneyLabel = new JLabel("$0");
        topMoneyLabel.setFont(Theme.fontHudBold());
        topMoneyLabel.setForeground(Theme.ACCENT);
        strip.add(topMoneyLabel);
        return strip;
    }

    private JComponent buildZoomGroup(GameMapPanel gamePanel) {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.CENTER, Theme.SPACING_XS, 0));
        group.setOpaque(false);

        JLabel kicker = new JLabel("ZOOM");
        kicker.setFont(Theme.fontSectionLabel());
        kicker.setForeground(Theme.TEXT_SECONDARY);
        group.add(kicker);

        topZoomLabel = new JLabel(gamePanel.getZoomPercent() + "%");
        topZoomLabel.setFont(Theme.fontHud());
        topZoomLabel.setForeground(Theme.TEXT_PRIMARY);
        topZoomLabel.setPreferredSize(new Dimension(48, topZoomLabel.getPreferredSize().height));
        topZoomLabel.setHorizontalAlignment(JLabel.CENTER);

        MilitaryButton zoomOut = new MilitaryButton("\u2212", MilitaryButton.Style.GHOST);
        zoomOut.setFont(zoomOut.getFont().deriveFont(Font.BOLD, 14f));
        zoomOut.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        zoomOut.addActionListener(e -> {
            gamePanel.zoomOut();
            topZoomLabel.setText(gamePanel.getZoomPercent() + "%");
        });

        MilitaryButton zoomIn = new MilitaryButton("+", MilitaryButton.Style.GHOST);
        zoomIn.setFont(zoomIn.getFont().deriveFont(Font.BOLD, 14f));
        zoomIn.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        zoomIn.addActionListener(e -> {
            gamePanel.zoomIn();
            topZoomLabel.setText(gamePanel.getZoomPercent() + "%");
        });

        group.add(zoomOut);
        group.add(topZoomLabel);
        group.add(zoomIn);

        group.add(MilitaryComponents.horizontalGap(Theme.SPACING_MD));
        group.add(MilitaryComponents.verticalRule());
        group.add(MilitaryComponents.horizontalGap(Theme.SPACING_MD));

        JLabel volKicker = new JLabel("VOL");
        volKicker.setFont(Theme.fontSectionLabel());
        volKicker.setForeground(Theme.TEXT_SECONDARY);
        group.add(volKicker);
        group.add(MilitaryComponents.horizontalGap(Theme.SPACING_XS));

        int volPct = Math.round(ClasspathWavPlayer.getMasterVolume() * 100f);
        JSlider volumeSlider = new JSlider(0, 100, Math.max(0, Math.min(100, volPct)));
        volumeSlider.setOpaque(false);
        volumeSlider.setPreferredSize(new Dimension(120, volumeSlider.getPreferredSize().height));
        volumeSlider.setToolTipText("Master game volume");
        volumeSlider.addChangeListener(e ->
            ClasspathWavPlayer.setMasterVolume(volumeSlider.getValue() / 100f));
        group.add(volumeSlider);

        return group;
    }

    private JComponent buildActionGroup(Runnable onBackToMenu, GameMapPanel gamePanel) {
        JPanel group = new JPanel(new FlowLayout(FlowLayout.RIGHT, Theme.SPACING_SM, 0));
        group.setOpaque(false);

        endTurnButton = new MilitaryButton("End Turn", MilitaryButton.Style.PRIMARY);
        endTurnButton.setToolTipText("Confirms turn — passes control to the next faction.");
        endTurnButton.addActionListener(e -> {
            if (aiTurnInProgress || session.matchFinished()) {
                return;
            }
            performEndTurn();
        });

        MilitaryButton back = new MilitaryButton("Exit Mission", MilitaryButton.Style.GHOST);
        back.setToolTipText("Return to the command menu.");
        back.addActionListener(e -> {
            cancelDeferredAiStart();
            if (aiExecutor != null) {
                aiExecutor.stop();
            }
            dispose();
            onBackToMenu.run();
        });

        group.add(endTurnButton);
        group.add(back);
        return group;
    }

    private class GameMapPanel extends JPanel {
        private static final int BASE_TILE_SIZE = 28;
        private static final int MIN_TILE_SIZE = 14;
        private static final int MAX_TILE_SIZE = 84;
        private GameMap map;
        private final AssetManager assetManager;
        private final Consumer<SelectionInfo> selectionConsumer;
        private PlayableGameSession session;
        private final UnitSoundEffects unitSoundEffects;
        private int tileSize = BASE_TILE_SIZE;
        private Point selectedCell;
        private Set<Point> reachableCells = Collections.emptySet();
        /** Stationary ranged band or one-tile melee reach preview (red tint). */
        private Set<Point> attackRangePreviewCells = Collections.emptySet();
        private Set<Point> attackableCells = Collections.emptySet();
        /** Planned move: always starts with the selected unit's cell. */
        private final List<Point> movementPath = new ArrayList<>();
        private Point lastHoverGrid;
        private Timer moveAnimTimer;
        private List<Point> animPath;
        private Unit animUnit;
        /** If non-null, after the animated move finishes, combat presentation runs against this target (melee only). */
        private Unit pendingMeleeAttackTarget;
        /**
         * While the AI plays an animated move, invoked when the move (and any chained melee or
         * cloak-discovery combat) has fully finished — so the turn executor can advance.
         */
        private Runnable moveAnimAiWhenFullyDone;
        private boolean moveAnimationRunning;
        /** Index of segment {@code animPath[i] -> animPath[i+1]} currently gliding along. */
        private int glideSegIndex;
        /** 0..1 progress within the current segment (time-based). */
        private float glideProgress;
        private long glideLastNanos;
        private static final float GLIDE_SECONDS_PER_TILE = 0.14f;
        private static final int GLIDE_TIMER_MS = 12;
        /**
         * Time the movement SFX gets to start playing before the glide animation actually begins,
         * so the audio is audibly ahead of (or at least synced with) the visible movement.
         */
        private static final int MOVE_SOUND_LEAD_MS = 400;
        /** One-shot Swing timer that fires {@link #beginGlideAfterSoundLead} after {@link #MOVE_SOUND_LEAD_MS}. */
        private Timer moveAnimStartDelayTimer;

        private static final float COMBAT_HOP_MS = 220f;
        /**
         * Quiet beat between the attacker's hop (which fires the outgoing-attack SFX) and the
         * defender's counter hop (which fires the counter SFX). Sized so the outgoing SFX (and any
         * explosion-on-defender-death SFX) finish before the counter SFX starts, instead of
         * stacking on top of each other. Total spacing between the two SFX events ≈
         * {@code COMBAT_PAUSE_BEFORE_COUNTER_MS + COMBAT_HOP_MS}.
         */
        private static final float COMBAT_PAUSE_BEFORE_COUNTER_MS = 800f;
        private static final int COMBAT_PHASE_ATTACK_HOP = 0;
        private static final int COMBAT_PHASE_PAUSE_BEFORE_COUNTER = 1;
        private static final int COMBAT_PHASE_COUNTER_HOP = 2;
        private Timer combatAnimTimer;
        private boolean combatAnimRunning;
        private Unit combatAnimAttacker;
        private Unit combatAnimDefender;
        /** One of {@code COMBAT_PHASE_*}. */
        private int combatAnimPhase;
        private long combatAnimPhaseStartNanos;
        private Runnable combatAnimOnComplete;
        private boolean combatAnimOutgoingApplied;
        private boolean combatAnimCounterPending;
        private boolean combatAnimCounterApplied;
        private boolean combatAnimHousekeepingDone;
        /**
         * When true, the outgoing strike fires through {@link PlayableGameSession#applyDiscoveryStrike}
         * (Tracker surprise bonus). Set by {@link #beginCombatPresentation(Unit, Unit, boolean, Runnable)}
         * for movement-discovery auto-attacks.
         */
        private boolean combatAnimIsDiscovery;

        /**
         * Active procedural explosion effects (death visuals). Iterated and pruned each effects-timer
         * tick. Independent of combat presentation so explosions can outlive the combat phase.
         */
        private final List<ExplosionEffect> activeExplosions = new ArrayList<>();
        private Timer effectsTimer;
        private static final int EFFECTS_TIMER_MS = 16;

        private GameMapPanel(
            GameMap map,
            AssetManager assetManager,
            Consumer<SelectionInfo> selectionConsumer,
            PlayableGameSession session,
            UnitSoundEffects unitSoundEffects
        ) {
            this.map = map;
            this.assetManager = assetManager;
            this.selectionConsumer = selectionConsumer;
            this.session = session;
            this.unitSoundEffects = unitSoundEffects;
            updatePreferredSize();

            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    // Popup trigger fires on press on Linux/Mac; release on Windows. Cover both
                    // so the context menu opens consistently across platforms.
                    if (e.isPopupTrigger()) {
                        showUnitContextMenu(e);
                        return;
                    }
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        onTileClicked(e.getX(), e.getY());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showUnitContextMenu(e);
                    }
                }
            });
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    onTileHover(e.getX(), e.getY());
                }
            });

            addMouseWheelListener(event -> {
                if (event.getWheelRotation() < 0) {
                    zoomIn();
                } else {
                    zoomOut();
                }
            });
        }

        void bindAuthoritativeSession(PlayableGameSession nextSession) {
            this.session = Objects.requireNonNull(nextSession);
            this.map = nextSession.getMap();
            clearSelectionExternal();
            reachableCells = Collections.emptySet();
            attackRangePreviewCells = Collections.emptySet();
            attackableCells = Collections.emptySet();
            movementPath.clear();
            updatePreferredSize();
            revalidate();
        }

        /**
         * Resolves in-progress combat immediately (no animation callback). Use when ending the turn
         * or other global actions so game state stays consistent.
         */
        public boolean forceFlushCombatAnimationIfRunning() {
            if (!combatAnimRunning || combatAnimAttacker == null || combatAnimDefender == null) {
                return false;
            }
            Unit a = combatAnimAttacker;
            Unit d = combatAnimDefender;
            if (combatAnimTimer != null) {
                combatAnimTimer.stop();
                combatAnimTimer = null;
            }
            if (!combatAnimOutgoingApplied) {
                unitSoundEffects.playOutgoingAttackIfArmed(a);
                if (combatAnimIsDiscovery) {
                    session.applyDiscoveryStrike(a, d);
                } else {
                    session.applyOutgoingStrike(a, d);
                }
                unitSoundEffects.playExplosionIfDead(d);
                enqueueDeathExplosion(d);
                combatAnimOutgoingApplied = true;
                combatAnimCounterPending = session.defenderEligibleForCounterattack(d, a);
            }
            if (combatAnimCounterPending && !combatAnimCounterApplied) {
                if (session.defenderEligibleForCounterattack(d, a)) {
                    session.orientUnitTowardTarget(d, a);
                    unitSoundEffects.playCounterAttackIfArmed(d);
                    session.applyCounterStrike(d, a);
                    unitSoundEffects.playExplosionIfDead(a);
                    enqueueDeathExplosion(a);
                }
                combatAnimCounterApplied = true;
            }
            if (!combatAnimHousekeepingDone) {
                session.completeAttackAfterCombat(a, d);
                combatAnimHousekeepingDone = true;
            }
            stopCombatAnimTimerSilently();
            GameWindow.this.refreshHud();
            return true;
        }

        private void clearSelection() {
            stopMoveAnim();
            selectedCell = null;
            reachableCells = Collections.emptySet();
            attackRangePreviewCells = Collections.emptySet();
            attackableCells = Collections.emptySet();
            movementPath.clear();
            lastHoverGrid = null;
        }

        /** Outer-class accessor so {@link GameWindow} can reset selection on turn transitions. */
        void clearSelectionExternal() {
            clearSelection();
        }

        private Unit getSelectedUnit() {
            if (selectedCell == null) {
                return null;
            }
            Tile t = map.getTile(selectedCell.x, selectedCell.y);
            return t != null ? t.getUnit() : null;
        }

        /**
         * After combat presentation: if the attacker still has its action (e.g. {@link UnitAbilities#SCAVENGER}),
         * keep it selected with fresh reach/attack highlights; otherwise clear and focus {@code fallbackTile}.
         */
        private void finishHumanCombatSelection(Unit attacker, Point fallbackTile) {
            if (attacker != null && attacker.isAlive() && !attacker.hasMoved()) {
                int x = attacker.getPosition().getX();
                int y = attacker.getPosition().getY();
                selectedCell = new Point(x, y);
                recomputeHighlightsForUnit(attacker);
                selectionConsumer.accept(new SelectionInfo(x, y, map.getTile(x, y)));
            } else {
                clearSelection();
                if (fallbackTile != null) {
                    selectionConsumer.accept(
                        new SelectionInfo(fallbackTile.x, fallbackTile.y, map.getTile(fallbackTile.x, fallbackTile.y))
                    );
                }
            }
        }

        private void ensureMovementPathStartsWith(Unit unit) {
            Point start = new Point(unit.getPosition().getX(), unit.getPosition().getY());
            if (movementPath.isEmpty() || !movementPath.get(0).equals(start)) {
                movementPath.clear();
                movementPath.add(start);
            }
        }

        private void onTileHover(int pixelX, int pixelY) {
            if (session.matchFinished()
                || moveAnimationRunning || combatAnimRunning || isAiTurnInProgress()) {
                return;
            }
            Point grid = new Point(pixelX / tileSize, pixelY / tileSize);
            if (map.getTile(grid.x, grid.y) == null) {
                return;
            }
            if (lastHoverGrid != null && lastHoverGrid.equals(grid)) {
                return;
            }
            lastHoverGrid = new Point(grid.x, grid.y);
            Unit u = getSelectedUnit();
            if (u == null || !session.canUnitMove(u)) {
                return;
            }
            ensureMovementPathStartsWith(u);
            updatePlannedPathFromHover(grid);
            repaint();
        }

        private void updatePlannedPathFromHover(Point hover) {
            Unit u = getSelectedUnit();
            if (u == null || !session.canUnitMove(u)) {
                return;
            }
            ensureMovementPathStartsWith(u);
            Point last = movementPath.get(movementPath.size() - 1);
            if (hover.equals(last)) {
                return;
            }
            if (movementPath.size() >= 2 && hover.equals(movementPath.get(movementPath.size() - 2))) {
                movementPath.remove(movementPath.size() - 1);
                return;
            }
            for (int i = 0; i < movementPath.size(); i++) {
                if (movementPath.get(i).equals(hover)) {
                    while (movementPath.size() > i + 1) {
                        movementPath.remove(movementPath.size() - 1);
                    }
                    return;
                }
            }
            int manhattan = Math.abs(hover.x - last.x) + Math.abs(hover.y - last.y);
            if (manhattan == 1) {
                ArrayList<Point> trial = new ArrayList<>(movementPath);
                trial.add(hover);
                if (UnitMovementPaths.isValidMovementPath(map, u, trial)) {
                    movementPath.add(hover);
                    return;
                }
            }
            List<Point> snap = UnitMovementPaths.shortestLegalPath(map, u, hover.x, hover.y);
            if (snap.size() >= 2) {
                movementPath.clear();
                movementPath.addAll(snap);
            }
        }

        /**
         * Opens the unit context menu for the active player's unit on the right-clicked tile.
         * No-op (no menu shown) when the tile is empty, the unit isn't ours, no transport
         * actions apply, or any animation / AI turn is in flight.
         */
        private void showUnitContextMenu(MouseEvent e) {
            if (session.matchFinished()
                || moveAnimationRunning || combatAnimRunning || isAiTurnInProgress()) {
                return;
            }
            int gx = e.getX() / tileSize;
            int gy = e.getY() / tileSize;
            Tile tile = map.getTile(gx, gy);
            if (tile == null) {
                return;
            }
            Unit unit = tile.getUnit();
            if (unit == null
                || !unit.isAlive()
                || unit.getOwner() != session.getActivePlayer()) {
                return;
            }
            JPopupMenu menu = buildUnitContextMenu(unit);
            if (menu == null || menu.getComponentCount() == 0) {
                return;
            }
            menu.show(this, e.getX(), e.getY());
        }

        /**
         * Builds the context-menu options offered for {@code unit}. Currently exposes the
         * transport conversions: foot units may morph into Albatross; ground units on a coastal
         * shore tile may morph into Leviathan; converted transports may revert when the terrain
         * under them is traversable for the original land-unit type.
         */
        private JPopupMenu buildUnitContextMenu(Unit unit) {
            JPopupMenu menu = new JPopupMenu();
            if (unit.getUnitType() == UnitType.Warmachine && session.canUnitMove(unit)) {
                JMenuItem fab = new JMenuItem("Fabricate unit\u2026");
                fab.addActionListener(ev -> {
                    Frame f = (Frame) SwingUtilities.getWindowAncestor(this);
                    WarmachineBuildDialog.show(f == null ? GameWindow.this : f, session, unit, assetManager, () -> {
                        int ux = unit.getPosition().getX();
                        int uy = unit.getPosition().getY();
                        if (selectedCell != null && selectedCell.x == ux && selectedCell.y == uy) {
                            recomputeHighlightsForUnit(unit);
                        }
                        selectionConsumer.accept(new SelectionInfo(ux, uy, map.getTile(ux, uy)));
                        repaint();
                    });
                });
                menu.add(fab);
            }
            if (session.canWarmachineDrill(unit)) {
                JMenuItem drill = new JMenuItem(
                    "Drill ore deposit (+$" + PlayableGameSession.WARMACHINE_DRILL_INCOME + ")");
                drill.addActionListener(ev -> {
                    if (session.tryWarmachineDrill(unit)) {
                        int ux = unit.getPosition().getX();
                        int uy = unit.getPosition().getY();
                        if (selectedCell != null && selectedCell.x == ux && selectedCell.y == uy) {
                            recomputeHighlightsForUnit(unit);
                        }
                        selectionConsumer.accept(new SelectionInfo(ux, uy, map.getTile(ux, uy)));
                        repaint();
                    }
                });
                menu.add(drill);
            }
            if (session.canConvertUnitToAlbatross(unit)) {
                JMenuItem item = new JMenuItem("Convert to Albatross (sky transport)");
                item.addActionListener(ev -> {
                    if (session.convertUnitToAlbatross(unit)) {
                        refreshAfterTransportConversion(unit);
                    }
                });
                menu.add(item);
            }
            if (session.canConvertUnitToLeviathan(unit)) {
                JMenuItem item = new JMenuItem("Convert to Leviathan (sea transport)");
                item.addActionListener(ev -> {
                    if (session.convertUnitToLeviathan(unit)) {
                        refreshAfterTransportConversion(unit);
                    }
                });
                menu.add(item);
            }
            if (session.canRevertTransport(unit)) {
                UnitType origin = unit.getOriginalUnitType();
                JMenuItem item = new JMenuItem("Disembark to " + prettyTypeName(origin));
                item.addActionListener(ev -> {
                    if (session.revertTransport(unit)) {
                        refreshAfterTransportConversion(unit);
                    }
                });
                menu.add(item);
            }
            return menu;
        }

        private void refreshAfterTransportConversion(Unit unit) {
            // If the converted unit is currently selected, refresh its movement / attack overlays
            // so the highlighted reach matches the new movement kind without a fresh selection.
            if (selectedCell != null) {
                Tile selTile = map.getTile(selectedCell.x, selectedCell.y);
                if (selTile != null && selTile.getUnit() == unit) {
                    recomputeHighlightsForUnit(unit);
                }
            }
            int ux = unit.getPosition().getX();
            int uy = unit.getPosition().getY();
            selectionConsumer.accept(new SelectionInfo(ux, uy, map.getTile(ux, uy)));
            repaint();
        }

        private static String prettyTypeName(UnitType type) {
            if (type == null) {
                return "original";
            }
            String n = type.name();
            return n.substring(0, 1).toUpperCase() + n.substring(1).toLowerCase();
        }

        private void onTileClicked(int pixelX, int pixelY) {
            if (session.matchFinished()) {
                return;
            }
            boolean interactionAllowed = !isAiTurnInProgress()
                && !moveAnimationRunning
                && !combatAnimRunning;
            Point grid = new Point(pixelX / tileSize, pixelY / tileSize);
            Tile tile = map.getTile(grid.x, grid.y);
            if (tile == null) {
                return;
            }

            if (!interactionAllowed) {
                // During AI/animation lockouts, clicking should still inspect tile occupants.
                selectionConsumer.accept(new SelectionInfo(grid.x, grid.y, tile));
                repaint();
                return;
            }

            Player active = session.getActivePlayer();
            Unit clickedUnit = tile.getUnit();

            if (selectedCell != null) {
                Tile selTile = map.getTile(selectedCell.x, selectedCell.y);
                Unit selected = selTile != null ? selTile.getUnit() : null;
                if (selected != null
                    && selected.isAlive()
                    && session.isOwnedByActivePlayer(selected)
                    && selectedCell.x == selected.getPosition().getX()
                    && selectedCell.y == selected.getPosition().getY()) {

                    if (clickedUnit != null
                        && clickedUnit.isAlive()
                        && clickedUnit.getOwner() != active
                        && session.canUnitMove(selected)
                        && session.canUnitAttack(selected)
                        && selected.getAttackRange() == 1
                        && movementPath.size() >= 2) {
                        Point pathStart = movementPath.get(0);
                        Point pathEnd = movementPath.get(movementPath.size() - 1);
                        int destToEnemy = Math.abs(pathEnd.x - grid.x) + Math.abs(pathEnd.y - grid.y);
                        if (!pathEnd.equals(pathStart)
                            && destToEnemy == 1
                            && session.validateMovementPath(selected, movementPath)) {
                            startMoveAnimation(selected, new ArrayList<>(movementPath), clickedUnit);
                            selectionConsumer.accept(
                                new SelectionInfo(
                                    selected.getPosition().getX(),
                                    selected.getPosition().getY(),
                                    map.getTile(selected.getPosition().getX(), selected.getPosition().getY())
                                )
                            );
                            repaint();
                            return;
                        }
                    }

                    if (clickedUnit != null
                        && clickedUnit.isAlive()
                        && clickedUnit.getOwner() != active
                        && attackableCells.contains(grid)) {
                        if (session.canExecuteAttack(selected, clickedUnit)) {
                            beginCombatPresentation(
                                selected,
                                clickedUnit,
                                () -> {
                                    finishHumanCombatSelection(
                                        selected,
                                        new Point(grid.x, grid.y)
                                    );
                                    repaint();
                                }
                            );
                            repaint();
                            return;
                        }
                    }

                    if (session.canUnitMove(selected) && reachableCells.contains(grid)) {
                        if (movementPath.size() > 1 && grid.equals(movementPath.get(movementPath.size() - 1))) {
                            if (session.validateMovementPath(selected, movementPath)) {
                                startMoveAnimation(selected, new ArrayList<>(movementPath), null);
                                selectionConsumer.accept(
                                    new SelectionInfo(
                                        selected.getPosition().getX(),
                                        selected.getPosition().getY(),
                                        map.getTile(selected.getPosition().getX(), selected.getPosition().getY())
                                    )
                                );
                                repaint();
                                return;
                            }
                        }
                        for (int i = 0; i < movementPath.size(); i++) {
                            if (movementPath.get(i).equals(grid)) {
                                while (movementPath.size() > i + 1) {
                                    movementPath.remove(movementPath.size() - 1);
                                }
                                selectionConsumer.accept(new SelectionInfo(grid.x, grid.y, tile));
                                repaint();
                                return;
                            }
                        }
                        List<Point> snap = UnitMovementPaths.shortestLegalPath(map, selected, grid.x, grid.y);
                        if (snap.size() >= 2) {
                            movementPath.clear();
                            movementPath.addAll(snap);
                            selectionConsumer.accept(new SelectionInfo(grid.x, grid.y, tile));
                            repaint();
                            return;
                        }
                    }
                }
            }

            if (clickedUnit != null && clickedUnit.isAlive()) {
                selectedCell = grid;
                if (interactionAllowed && clickedUnit.getOwner() == active) {
                    recomputeHighlightsForUnit(clickedUnit);
                } else {
                    reachableCells = Collections.emptySet();
                    attackRangePreviewCells = Collections.emptySet();
                    attackableCells = Collections.emptySet();
                    movementPath.clear();
                    lastHoverGrid = null;
                }
            } else {
                clearSelection();
            }

            selectionConsumer.accept(new SelectionInfo(grid.x, grid.y, tile));
            repaint();
        }

        private void startMoveAnimation(Unit unit, List<Point> fullPath, Unit meleeAttackAfterMove) {
            startMoveAnimationInternal(unit, fullPath, meleeAttackAfterMove, null);
        }

        /**
         * Same glide + SFX as human play; {@code whenAiMoveChainDone} runs after the move and any
         * chained melee combat (or immediately if the animation cannot start).
         */
        private void startMoveAnimationForAi(
            Unit unit, List<Point> fullPath, Unit meleeAttackAfterMove, Runnable whenAiMoveChainDone
        ) {
            startMoveAnimationInternal(unit, fullPath, meleeAttackAfterMove, whenAiMoveChainDone);
        }

        private void startMoveAnimationInternal(
            Unit unit, List<Point> fullPath, Unit meleeAttackAfterMove, Runnable moveAiWhenFullyDone
        ) {
            if (moveAnimationRunning) {
                return;
            }
            if (moveAnimTimer != null && moveAnimTimer.isRunning()) {
                return;
            }
            if (fullPath.size() < 2) {
                return;
            }
            moveAnimAiWhenFullyDone = moveAiWhenFullyDone;
            unitSoundEffects.playMove(unit);
            session.clearMoveAnimationDisplacementStack();
            pendingMeleeAttackTarget = meleeAttackAfterMove;
            animPath = fullPath;
            animUnit = unit;
            glideSegIndex = 0;
            glideProgress = 0f;
            // Set in beginGlideAfterSoundLead so dt on the first tick stays small.
            glideLastNanos = 0L;
            moveAnimationRunning = true;
            session.resetCaptureBeforeMove(unit);
            Point p0 = fullPath.get(0);
            Point p1 = fullPath.get(1);
            Tile startTile = map.getTile(p0.x, p0.y);
            if (startTile != null) {
                startTile.setUnitFacing(UnitMovementPaths.facingForStep(p0, p1));
            }
            moveAnimStartDelayTimer = new Timer(MOVE_SOUND_LEAD_MS, e -> beginGlideAfterSoundLead());
            moveAnimStartDelayTimer.setRepeats(false);
            moveAnimStartDelayTimer.start();
        }

        void presentAiStep(AiAction action, Runnable whenComplete) {
            if (action instanceof AiAction.EndTurn) {
                whenComplete.run();
                return;
            }
            if (action instanceof AiAction.PassUnit pu) {
                session.markUnitActionConsumed(pu.unit());
                whenComplete.run();
                return;
            }
            if (action instanceof AiAction.MoveUnit mu) {
                Unit u = mu.unit();
                // Even on failure mark consumed: prevents infinite loops if the planned path is no
                // longer valid (e.g. another AI unit ate the destination this turn).
                if (!session.canUnitMove(u) || !session.validateMovementPath(u, mu.path())) {
                    session.markUnitActionConsumed(u);
                    whenComplete.run();
                    return;
                }
                ArrayList<Point> path = new ArrayList<>(mu.path());
                startMoveAnimationForAi(u, path, null, () -> {
                    session.markUnitActionConsumed(u);
                    whenComplete.run();
                });
                if (!moveAnimationRunning) {
                    session.markUnitActionConsumed(u);
                    whenComplete.run();
                }
                return;
            }
            if (action instanceof AiAction.MoveAndAttack ma) {
                Unit u = ma.unit();
                if (!session.canUnitMove(u) || !session.validateMovementPath(u, ma.path())) {
                    session.markUnitActionConsumed(u);
                    whenComplete.run();
                    return;
                }
                startMoveAnimationForAi(u, new ArrayList<>(ma.path()), ma.target(), () -> {
                    Unit mover = u;
                    Unit target = ma.target();
                    if (mover != null && mover.isAlive() && target != null && target.isAlive()
                        && session.canExecuteAttack(mover, target)) {
                        beginCombatPresentation(mover, target, () -> whenComplete.run());
                    } else {
                        session.markUnitActionConsumed(mover);
                        whenComplete.run();
                    }
                });
                if (!moveAnimationRunning) {
                    session.markUnitActionConsumed(u);
                    whenComplete.run();
                }
                return;
            }
            if (action instanceof AiAction.Attack a) {
                if (!session.canExecuteAttack(a.attacker(), a.target())) {
                    session.markUnitActionConsumed(a.attacker());
                    whenComplete.run();
                    return;
                }
                beginCombatPresentation(a.attacker(), a.target(), () -> whenComplete.run());
                return;
            }
            if (action instanceof AiAction.BuildUnit b) {
                session.tryFactoryBuildUnit(b.factoryX(), b.factoryY(), b.type());
                whenComplete.run();
                return;
            }
            whenComplete.run();
        }

        /**
         * Runs on the EDT after {@link #MOVE_SOUND_LEAD_MS} so the movement SFX has a head start
         * over the glide animation. {@link #stopMoveAnim} may have cleared state in the meantime
         * (turn ended, selection cleared), in which case this is a no-op.
         */
        private void beginGlideAfterSoundLead() {
            moveAnimStartDelayTimer = null;
            if (!moveAnimationRunning || animPath == null || animUnit == null) {
                return;
            }
            glideLastNanos = System.nanoTime();
            moveAnimTimer = new Timer(GLIDE_TIMER_MS, this::onMoveAnimTick);
            moveAnimTimer.setRepeats(true);
            moveAnimTimer.start();
        }

        private void onMoveAnimTick(ActionEvent e) {
            if (animPath == null || animUnit == null) {
                stopMoveAnim();
                return;
            }
            long now = System.nanoTime();
            float dt = (now - glideLastNanos) / 1_000_000_000f;
            glideLastNanos = now;
            glideProgress += dt / GLIDE_SECONDS_PER_TILE;

            while (glideProgress >= 1f) {
                glideProgress -= 1f;
                Point from = animPath.get(glideSegIndex);
                Point to = animPath.get(glideSegIndex + 1);
                Unit cloakedAtNext = session.cloakedEnemyAtStep(animUnit, to.x, to.y);
                if (cloakedAtNext != null) {
                    handleCloakedDiscoveryInterrupt(animUnit, cloakedAtNext);
                    return;
                }
                session.applyMovementStepWithFacing(animUnit, from, to);
                selectedCell = new Point(to.x, to.y);
                glideSegIndex++;
                if (glideSegIndex >= animPath.size() - 1) {
                    Unit mover = animUnit;
                    Runnable aiStepDone = moveAnimAiWhenFullyDone;
                    moveAnimAiWhenFullyDone = null;
                    session.completeAnimatedMove(mover);
                    Unit meleeTarget = pendingMeleeAttackTarget;
                    pendingMeleeAttackTarget = null;
                    stopMoveAnim();

                    if (aiStepDone != null) {
                        if (meleeTarget != null && mover != null && mover.isAlive() && meleeTarget.isAlive()
                            && session.canExecuteAttack(mover, meleeTarget)) {
                            Unit def = meleeTarget;
                            beginCombatPresentation(
                                mover,
                                def,
                                () -> {
                                    aiStepDone.run();
                                    repaint();
                                }
                            );
                        } else {
                            aiStepDone.run();
                        }
                        repaint();
                        return;
                    }

                    boolean combatStarted = false;
                    if (meleeTarget != null && mover != null && mover.isAlive() && meleeTarget.isAlive()
                        && session.canExecuteAttack(mover, meleeTarget)) {
                        Unit def = meleeTarget;
                        beginCombatPresentation(
                            mover,
                            def,
                            () -> {
                                finishHumanCombatSelection(
                                    mover,
                                    new Point(def.getPosition().getX(), def.getPosition().getY())
                                );
                                repaint();
                            }
                        );
                        combatStarted = true;
                    }

                    if (!combatStarted) {
                        session.markUnitActionConsumed(mover);
                        Unit u = getSelectedUnit();
                        if (u != null) {
                            selectedCell = new Point(u.getPosition().getX(), u.getPosition().getY());
                            recomputeHighlightsForUnit(u);
                        }
                    }
                    repaint();
                    return;
                }
            }
            repaint();
        }

        /**
         * Mover stepped onto (well, would have stepped onto) a tile occupied by a cloaked enemy.
         * Per cloak rules:
         * <ul>
         *   <li>The cloaked enemy is uncloaked.</li>
         *   <li>The mover's movement is interrupted on the previous tile (the cloaked tile is never entered).</li>
         *   <li>If the mover has {@link UnitAbilities#TRACKER}, an auto-attack against the now-revealed
         *       enemy fires immediately with the Tracker surprise bonus.</li>
         *   <li>Without TRACKER, movement just stops and the unit's action is consumed.</li>
         * </ul>
         */
        private void handleCloakedDiscoveryInterrupt(Unit mover, Unit cloakedEnemy) {
            cloakedEnemy.setCloaked(false);
            if (animPath != null && mover != null && glideSegIndex > 0) {
                session.rewindMovementSteps(mover, animPath, glideSegIndex);
                glideSegIndex = 0;
            }
            session.completeAnimatedMove(mover);
            pendingMeleeAttackTarget = null;
            Runnable aiStepDone = moveAnimAiWhenFullyDone;
            moveAnimAiWhenFullyDone = null;
            stopMoveAnim();

            boolean autoAttackFired = false;
            if (mover.hasAbility(UnitAbilities.TRACKER)
                && mover.isAlive()
                && cloakedEnemy.isAlive()
                && session.canExecuteAttack(mover, cloakedEnemy)) {
                Unit def = cloakedEnemy;
                beginCombatPresentation(
                    mover,
                    def,
                    true,
                    () -> {
                        if (aiStepDone != null) {
                            aiStepDone.run();
                        } else {
                            finishHumanCombatSelection(
                                mover,
                                new Point(def.getPosition().getX(), def.getPosition().getY())
                            );
                        }
                        repaint();
                    }
                );
                autoAttackFired = true;
            }

            if (!autoAttackFired) {
                session.markUnitActionConsumed(mover);
                if (aiStepDone != null) {
                    aiStepDone.run();
                } else {
                    Unit u = getSelectedUnit();
                    if (u != null) {
                        selectedCell = new Point(u.getPosition().getX(), u.getPosition().getY());
                        recomputeHighlightsForUnit(u);
                    }
                }
                repaint();
            }
        }

        private void beginCombatPresentation(Unit attacker, Unit defender, Runnable onComplete) {
            beginCombatPresentation(attacker, defender, false, onComplete);
        }

        /**
         * @param isDiscoveryAttack when {@code true}, the outgoing strike applies the Tracker
         *                          surprise bonus via {@link PlayableGameSession#applyDiscoveryStrike}
         *                          (used after movement-interrupt discovery on a cloaked enemy).
         */
        private void beginCombatPresentation(
            Unit attacker, Unit defender, boolean isDiscoveryAttack, Runnable onComplete
        ) {
            if (combatAnimRunning || moveAnimationRunning) {
                return;
            }
            if (!session.canExecuteAttack(attacker, defender)) {
                return;
            }
            session.orientUnitTowardTarget(attacker, defender);
            combatAnimAttacker = attacker;
            combatAnimDefender = defender;
            combatAnimPhase = COMBAT_PHASE_ATTACK_HOP;
            combatAnimPhaseStartNanos = System.nanoTime();
            combatAnimOnComplete = onComplete;
            combatAnimRunning = true;
            combatAnimOutgoingApplied = false;
            combatAnimCounterPending = false;
            combatAnimCounterApplied = false;
            combatAnimHousekeepingDone = false;
            combatAnimIsDiscovery = isDiscoveryAttack;
            combatAnimTimer = new Timer(16, this::onCombatAnimTick);
            combatAnimTimer.setRepeats(true);
            combatAnimTimer.start();
        }

        private void onCombatAnimTick(ActionEvent e) {
            if (!combatAnimRunning || combatAnimAttacker == null || combatAnimDefender == null) {
                stopCombatAnimTimerSilently();
                return;
            }
            long hopNanos = (long) (COMBAT_HOP_MS * 1_000_000L);
            long pauseNanos = (long) (COMBAT_PAUSE_BEFORE_COUNTER_MS * 1_000_000L);
            long elapsed = System.nanoTime() - combatAnimPhaseStartNanos;
            Unit a = combatAnimAttacker;
            Unit d = combatAnimDefender;

            if (combatAnimPhase == COMBAT_PHASE_ATTACK_HOP) {
                if (elapsed >= hopNanos) {
                    unitSoundEffects.playOutgoingAttackIfArmed(a);
                    if (combatAnimIsDiscovery) {
                        session.applyDiscoveryStrike(a, d);
                    } else {
                        session.applyOutgoingStrike(a, d);
                    }
                    unitSoundEffects.playExplosionIfDead(d);
                    enqueueDeathExplosion(d);
                    combatAnimOutgoingApplied = true;
                    combatAnimCounterPending = session.defenderEligibleForCounterattack(d, a);
                    if (combatAnimCounterPending) {
                        session.orientUnitTowardTarget(d, a);
                        combatAnimPhase = COMBAT_PHASE_PAUSE_BEFORE_COUNTER;
                        combatAnimPhaseStartNanos = System.nanoTime();
                    } else {
                        finishCombatPresentation(a, d);
                    }
                }
                repaint();
                return;
            }
            if (combatAnimPhase == COMBAT_PHASE_PAUSE_BEFORE_COUNTER) {
                if (elapsed >= pauseNanos) {
                    combatAnimPhase = COMBAT_PHASE_COUNTER_HOP;
                    combatAnimPhaseStartNanos = System.nanoTime();
                }
                repaint();
                return;
            }
            if (combatAnimPhase == COMBAT_PHASE_COUNTER_HOP) {
                if (elapsed >= hopNanos) {
                    unitSoundEffects.playCounterAttackIfArmed(d);
                    session.applyCounterStrike(d, a);
                    unitSoundEffects.playExplosionIfDead(a);
                    enqueueDeathExplosion(a);
                    combatAnimCounterApplied = true;
                    finishCombatPresentation(a, d);
                }
                repaint();
            }
        }

        private void finishCombatPresentation(Unit attacker, Unit defender) {
            if (!combatAnimHousekeepingDone) {
                session.completeAttackAfterCombat(attacker, defender);
                combatAnimHousekeepingDone = true;
            }
            Runnable done = combatAnimOnComplete;
            stopCombatAnimTimerSilently();
            GameWindow.this.refreshHud();
            if (done != null) {
                done.run();
            }
        }

        private void stopCombatAnimTimerSilently() {
            if (combatAnimTimer != null) {
                combatAnimTimer.stop();
                combatAnimTimer = null;
            }
            combatAnimRunning = false;
            combatAnimAttacker = null;
            combatAnimDefender = null;
            combatAnimOnComplete = null;
            combatAnimPhase = COMBAT_PHASE_ATTACK_HOP;
            combatAnimOutgoingApplied = false;
            combatAnimCounterPending = false;
            combatAnimCounterApplied = false;
            combatAnimHousekeepingDone = false;
            combatAnimIsDiscovery = false;
        }

        /**
         * Spawns a procedural shockwave on {@code unit}'s tile if (and only if) the unit was just
         * destroyed. Captures the position immediately, so it's safe to invoke regardless of
         * whether subsequent cleanup detaches {@code unit} from the map.
         */
        private void enqueueDeathExplosion(Unit unit) {
            if (unit == null || unit.isAlive()) {
                return;
            }
            activeExplosions.add(ExplosionEffect.forUnit(unit, System.nanoTime()));
            ensureEffectsTimerRunning();
        }

        private void ensureEffectsTimerRunning() {
            if (effectsTimer != null && effectsTimer.isRunning()) {
                return;
            }
            effectsTimer = new Timer(EFFECTS_TIMER_MS, this::onEffectsTick);
            effectsTimer.setRepeats(true);
            effectsTimer.start();
        }

        private void onEffectsTick(ActionEvent e) {
            long now = System.nanoTime();
            activeExplosions.removeIf(fx -> !fx.isAlive(now));
            if (activeExplosions.isEmpty()) {
                if (effectsTimer != null) {
                    effectsTimer.stop();
                    effectsTimer = null;
                }
            }
            repaint();
        }

        private void drawExplosionsOverlay(Graphics2D g2) {
            if (activeExplosions.isEmpty()) {
                return;
            }
            long now = System.nanoTime();
            for (ExplosionEffect fx : activeExplosions) {
                fx.paint(g2, tileSize, now);
            }
        }

        private void stopMoveAnim() {
            if (animPath != null && animUnit != null && animPath.size() >= 2) {
                int destIndex = animPath.size() - 1;
                if (glideSegIndex > 0 && glideSegIndex < destIndex) {
                    session.rewindMovementSteps(animUnit, animPath, glideSegIndex);
                }
            }
            moveAnimationRunning = false;
            session.clearMoveAnimationDisplacementStack();
            pendingMeleeAttackTarget = null;
            if (moveAnimStartDelayTimer != null) {
                moveAnimStartDelayTimer.stop();
                moveAnimStartDelayTimer = null;
            }
            if (moveAnimTimer != null) {
                moveAnimTimer.stop();
                moveAnimTimer = null;
            }
            animPath = null;
            animUnit = null;
            glideSegIndex = 0;
            glideProgress = 0f;
            glideLastNanos = 0L;
        }

        private boolean isGlidingUnitDrawnInOverlay(Tile tile) {
            if (!moveAnimationRunning || animUnit == null || animPath == null) {
                return false;
            }
            Unit u = tile.getUnit();
            if (u != animUnit) {
                return false;
            }
            // Omit glideProgress check: at segment end (progress >= 1) the overlay must keep drawing until the
            // tick applies the step; otherwise the tile draw is skipped and the glide overlay can miss one frame.
            return glideSegIndex < animPath.size() - 1;
        }

        /** Linear 0..1 — constant speed along a segment so collinear steps do not ease to a stop every tile. */
        private static float clamp01(float v) {
            return Math.min(1f, Math.max(0f, v));
        }

        private void drawGlidingUnitOverlay(Graphics2D g2) {
            if (!moveAnimationRunning || animUnit == null || animPath == null) {
                return;
            }
            if (!session.canActivePlayerPerceiveUnit(animUnit)) {
                return;
            }
            if (glideSegIndex >= animPath.size() - 1) {
                return;
            }
            Point from = animPath.get(glideSegIndex);
            Point to = animPath.get(glideSegIndex + 1);
            float t = clamp01(glideProgress);
            float drawX = from.x * tileSize + (to.x - from.x) * tileSize * t;
            float drawY = from.y * tileSize + (to.y - from.y) * tileSize * t
                + combatHopOffsetPixels(animUnit);

            Tile sourceTile = map.getTile(animUnit.getPosition().getX(), animUnit.getPosition().getY());
            if (sourceTile == null) {
                return;
            }
            String spriteId = sourceTile.getUnitSpriteId();
            FacingDirection facing = UnitMovementPaths.facingForStep(from, to);
            if (spriteId != null) {
                Image frame = unitFrameForOwner(spriteId, facing, animUnit.getOwner());
                if (frame != null) {
                    runWithOwnedCloakedSpriteOpacity(g2, animUnit, () ->
                        AssetManager.drawUnitFrameOnTile(g2, frame, drawX, drawY, tileSize));
                    drawUnitHealthBar(g2, animUnit, Math.round(drawX), Math.round(drawY), tileSize);
                    return;
                }
            }
            Color c = ownerColor(animUnit.getOwner());
            runWithOwnedCloakedSpriteOpacity(g2, animUnit, () -> {
                g2.setColor(c);
                int inset = Math.max(4, tileSize / 6);
                g2.fillOval(
                    Math.round(drawX) + inset,
                    Math.round(drawY) + inset,
                    tileSize - inset * 2,
                    tileSize - inset * 2
                );
                g2.setColor(Color.WHITE);
                g2.drawOval(
                    Math.round(drawX) + inset,
                    Math.round(drawY) + inset,
                    tileSize - inset * 2,
                    tileSize - inset * 2
                );
            });
            drawUnitHealthBar(g2, animUnit, Math.round(drawX), Math.round(drawY), tileSize);
        }

        private void recomputeHighlightsForUnit(Unit unit) {
            if (unit == null || !unit.isAlive()) {
                reachableCells = Collections.emptySet();
                attackRangePreviewCells = Collections.emptySet();
                attackableCells = Collections.emptySet();
                movementPath.clear();
                return;
            }
            if (session.canUnitMove(unit)) {
                reachableCells = MovementReach.reachableDestinations(map, unit);
            } else {
                reachableCells = Collections.emptySet();
            }
            attackRangePreviewCells = computeAttackRangePreviewTiles(unit);
            attackableCells = computeAttackableEnemyCells(unit);
            movementPath.clear();
            movementPath.add(new Point(unit.getPosition().getX(), unit.getPosition().getY()));
            lastHoverGrid = null;
        }

        /** Ranged attackers (range &gt; 1) preview a stationary firing band; melee shows tiles one step beyond move reach. */
        private static boolean isIndirectFireUnit(Unit unit) {
            return unit != null && unit.getAttackRange() > 1;
        }

        private Set<Point> computeAttackRangePreviewTiles(Unit unit) {
            if (unit == null || !session.canUnitMove(unit) || !session.canUnitAttack(unit)) {
                return Collections.emptySet();
            }
            if (isIndirectFireUnit(unit)) {
                return stationaryAttackBand(unit);
            }
            return oneTileBeyondReachable(reachableCells, unit);
        }

        private Set<Point> stationaryAttackBand(Unit unit) {
            int ux = unit.getPosition().getX();
            int uy = unit.getPosition().getY();
            int minR = unit.getMinAttackRange();
            int maxR = CombatTerrain.effectiveMaxAttackRange(map, unit);
            Set<Point> out = new HashSet<>();
            int w = map.getWidth();
            int h = map.getHeight();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int d = Math.abs(x - ux) + Math.abs(y - uy);
                    if (d >= minR && d <= maxR) {
                        out.add(new Point(x, y));
                    }
                }
            }
            return out;
        }

        private Set<Point> oneTileBeyondReachable(Set<Point> reachable, Unit unit) {
            Point start = new Point(unit.getPosition().getX(), unit.getPosition().getY());
            Set<Point> expandFrom = new HashSet<>(reachable);
            expandFrom.add(start);
            Set<Point> out = new HashSet<>();
            int w = map.getWidth();
            int h = map.getHeight();
            int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (Point p : expandFrom) {
                for (int[] d : dirs) {
                    int nx = p.x + d[0];
                    int ny = p.y + d[1];
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                        continue;
                    }
                    Point n = new Point(nx, ny);
                    if (n.equals(start)) {
                        continue;
                    }
                    if (!reachable.contains(n)) {
                        out.add(n);
                    }
                }
            }
            return out;
        }

        private Set<Point> computeAttackableEnemyCells(Unit attacker) {
            if (!session.canUnitAttack(attacker)) {
                return Collections.emptySet();
            }
            Set<Point> out = new HashSet<>();
            for (int y = 0; y < map.getHeight(); y++) {
                for (int x = 0; x < map.getWidth(); x++) {
                    Tile t = map.getTile(x, y);
                    if (t == null) {
                        continue;
                    }
                    Unit other = t.getUnit();
                    if (other == null || !other.isAlive() || other.getOwner() == attacker.getOwner()) {
                        continue;
                    }
                    int dist = attacker.getPosition().manhattanDistance(other.getPosition());
                    if (dist <= CombatTerrain.effectiveMaxAttackRange(map, attacker)
                        && dist >= attacker.getMinAttackRange()
                        && EngagementRules.attackerCanTargetDefender(attacker, other, map)) {
                        out.add(new Point(x, y));
                    }
                }
            }
            return out;
        }

        private int getZoomPercent() {
            return Math.round(tileSize * 100f / BASE_TILE_SIZE);
        }

        private void zoomIn() {
            setTileSize(tileSize + 4);
        }

        private void zoomOut() {
            setTileSize(tileSize - 4);
        }

        private void setTileSize(int candidate) {
            int clamped = Math.max(MIN_TILE_SIZE, Math.min(MAX_TILE_SIZE, candidate));
            if (tileSize == clamped) {
                return;
            }
            tileSize = clamped;
            updatePreferredSize();
            revalidate();
            repaint();
        }

        private void updatePreferredSize() {
            setPreferredSize(new Dimension(map.getWidth() * tileSize, map.getHeight() * tileSize));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            for (int y = 0; y < map.getHeight(); y++) {
                for (int x = 0; x < map.getWidth(); x++) {
                    Tile tile = map.getTile(x, y);
                    if (tile == null) {
                        continue;
                    }
                    int px = x * tileSize;
                    int py = y * tileSize;
                    drawTerrain(g, tile.getTerrainType(), px, py);
                    drawStructure(g, tile, px, py);
                    if (tile.isOreDeposit()) {
                        int r = Math.max(4, tileSize / 6);
                        g.setColor(new Color(255, 200, 60, 200));
                        g.fillOval(px + tileSize - r - 3, py + tileSize - r - 3, r, r);
                        g.setColor(new Color(90, 60, 10, 180));
                        g.drawOval(px + tileSize - r - 3, py + tileSize - r - 3, r, r);
                    }
                }
            }
            drawReachOverlay(g);
            drawAttackRangePreviewOverlay(g);
            drawAttackOverlay(g);
            drawPlannedMovementPath((Graphics2D) g.create());
            // Ascending y: units at larger grid y (drawn later) overlap those above for a simple depth cue.
            for (int y = 0; y < map.getHeight(); y++) {
                for (int x = 0; x < map.getWidth(); x++) {
                    Tile tile = map.getTile(x, y);
                    if (tile == null) {
                        continue;
                    }
                    int px = x * tileSize;
                    int py = y * tileSize;
                    drawPlacedUnit(g, tile, px, py);
                    g.setColor(new Color(0, 0, 0, 55));
                    g.drawRect(px, py, tileSize, tileSize);
                }
            }
            Graphics2D g2o = (Graphics2D) g.create();
            try {
                drawGlidingUnitOverlay(g2o);
            } finally {
                g2o.dispose();
            }
            Graphics2D g2fx = (Graphics2D) g.create();
            try {
                drawExplosionsOverlay(g2fx);
            } finally {
                g2fx.dispose();
            }
            Graphics2D gIdle = (Graphics2D) g.create();
            try {
                drawIdleActionCrosshairs(gIdle);
            } finally {
                gIdle.dispose();
            }
            drawCursor(g);
        }

        private void drawReachOverlay(Graphics g) {
            if (reachableCells == null || reachableCells.isEmpty()) {
                return;
            }
            g.setColor(new Color(255, 255, 255, 72));
            for (Point cell : reachableCells) {
                int px = cell.x * tileSize;
                int py = cell.y * tileSize;
                g.fillRect(px, py, tileSize, tileSize);
            }
        }

        private void drawAttackRangePreviewOverlay(Graphics g) {
            if (attackRangePreviewCells == null || attackRangePreviewCells.isEmpty()) {
                return;
            }
            g.setColor(new Color(255, 75, 75, 72));
            for (Point cell : attackRangePreviewCells) {
                int px = cell.x * tileSize;
                int py = cell.y * tileSize;
                g.fillRect(px, py, tileSize, tileSize);
            }
        }

        /**
         * Bracket corners (gaps mid-edge), for units/factories that may still spend their turn action.
         */
        private static void drawIdleActionCrosshair(Graphics2D g2, int tilePx, int tilePy, int ts) {
            float inset = Math.max(2f, ts * 0.08f);
            float leg = Math.max(5f, ts * 0.26f);
            float stroke = Math.max(1.35f, ts / 20f);
            float x0 = tilePx + inset;
            float y0 = tilePy + inset;
            float x1 = tilePx + ts - inset;
            float y1 = tilePy + ts - inset;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setStroke(new BasicStroke(stroke, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            Color line = new Color(245, 245, 245, 235);
            Color shadow = new Color(10, 10, 10, 120);
            g2.setColor(shadow);
            drawCornerBracketsOffset(g2, x0 + 0.75f, y0 + 0.75f, x1 + 0.75f, y1 + 0.75f, leg);
            g2.setColor(line);
            drawCornerBracketsOffset(g2, x0, y0, x1, y1, leg);
        }

        private static void drawCornerBracketsOffset(Graphics2D g2, float x0, float y0, float x1, float y1, float leg) {
            // Top-left
            g2.drawLine(Math.round(x0), Math.round(y0), Math.round(x0 + leg), Math.round(y0));
            g2.drawLine(Math.round(x0), Math.round(y0), Math.round(x0), Math.round(y0 + leg));
            // Top-right
            g2.drawLine(Math.round(x1), Math.round(y0), Math.round(x1 - leg), Math.round(y0));
            g2.drawLine(Math.round(x1), Math.round(y0), Math.round(x1), Math.round(y0 + leg));
            // Bottom-left
            g2.drawLine(Math.round(x0), Math.round(y1), Math.round(x0 + leg), Math.round(y1));
            g2.drawLine(Math.round(x0), Math.round(y1), Math.round(x0), Math.round(y1 - leg));
            // Bottom-right
            g2.drawLine(Math.round(x1), Math.round(y1), Math.round(x1 - leg), Math.round(y1));
            g2.drawLine(Math.round(x1), Math.round(y1), Math.round(x1), Math.round(y1 - leg));
        }

        private void drawIdleActionCrosshairs(Graphics2D g2) {
            if (session.matchFinished()) {
                return;
            }
            for (int y = 0; y < map.getHeight(); y++) {
                for (int x = 0; x < map.getWidth(); x++) {
                    Tile t = map.getTile(x, y);
                    if (t == null) {
                        continue;
                    }
                    boolean markUnit = false;
                    Unit u = t.getUnit();
                    if (u != null
                        && u.isAlive()
                        && !u.hasMoved()
                        && session.isOwnedByActivePlayer(u)
                        && session.canActivePlayerPerceiveUnit(u)) {
                        markUnit = true;
                    }
                    boolean markFactory = session.isFactoryEligibleForIdleCrosshair(x, y);
                    if (markUnit || markFactory) {
                        drawIdleActionCrosshair(g2, x * tileSize, y * tileSize, tileSize);
                    }
                }
            }
        }

        private void drawAttackOverlay(Graphics g) {
            if (attackableCells == null || attackableCells.isEmpty()) {
                return;
            }
            g.setColor(new Color(255, 60, 60, 100));
            for (Point cell : attackableCells) {
                int px = cell.x * tileSize;
                int py = cell.y * tileSize;
                g.fillRect(px, py, tileSize, tileSize);
            }
        }

        private void drawPlannedMovementPath(Graphics2D g2) {
            List<Point> path;
            if (moveAnimationRunning && animPath != null && animPath.size() > 1) {
                path = animPath;
            } else {
                path = movementPath;
            }
            boolean hoverFriendlyNoStop = shouldDrawFriendlyOccupiedHoverMarker();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (path.size() >= 2) {
                    float strokeW = Math.max(2.2f, tileSize / 12f);
                    g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(new Color(255, 210, 80, 220));
                    Path2D.Float line = new Path2D.Float(Path2D.WIND_NON_ZERO);
                    Point p0 = path.get(0);
                    line.moveTo(p0.x * tileSize + tileSize * 0.5f, p0.y * tileSize + tileSize * 0.5f);
                    for (int i = 1; i < path.size(); i++) {
                        Point p = path.get(i);
                        line.lineTo(p.x * tileSize + tileSize * 0.5f, p.y * tileSize + tileSize * 0.5f);
                    }
                    g2.draw(line);
                }
                if (hoverFriendlyNoStop) {
                    drawFriendlyOccupiedHoverX(g2);
                } else if (path.size() >= 2) {
                    Point prev = path.get(path.size() - 2);
                    Point last = path.get(path.size() - 1);
                    drawArrowHead(g2, prev, last, tileSize);
                }
            } finally {
                g2.dispose();
            }
        }

        /**
         * Hovering a tile with another friendly unit: cannot end movement there — show an X instead of the path arrow.
         */
        private boolean shouldDrawFriendlyOccupiedHoverMarker() {
            if (lastHoverGrid == null || moveAnimationRunning) {
                return false;
            }
            Unit mover = getSelectedUnit();
            if (mover == null || !session.canUnitMove(mover)) {
                return false;
            }
            Tile t = map.getTile(lastHoverGrid.x, lastHoverGrid.y);
            if (t == null) {
                return false;
            }
            Unit occ = t.getUnit();
            if (occ == null || occ == mover) {
                return false;
            }
            return occ.getOwner() == mover.getOwner();
        }

        private void drawFriendlyOccupiedHoverX(Graphics2D g2) {
            if (lastHoverGrid == null) {
                return;
            }
            float inset = Math.max(6f, tileSize * 0.22f);
            float x0 = lastHoverGrid.x * tileSize + inset;
            float y0 = lastHoverGrid.y * tileSize + inset;
            float x1 = lastHoverGrid.x * tileSize + tileSize - inset;
            float y1 = lastHoverGrid.y * tileSize + tileSize - inset;
            float strokeW = Math.max(2.5f, tileSize / 10f);
            g2.setStroke(new BasicStroke(strokeW, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(255, 120, 120, 230));
            g2.drawLine(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1));
            g2.drawLine(Math.round(x1), Math.round(y0), Math.round(x0), Math.round(y1));
            g2.setColor(new Color(90, 35, 35, 200));
            float o = 0.6f;
            g2.drawLine(Math.round(x0 - o), Math.round(y0 - o), Math.round(x1 + o), Math.round(y1 + o));
            g2.drawLine(Math.round(x1 + o), Math.round(y0 - o), Math.round(x0 - o), Math.round(y1 + o));
        }

        private static void drawArrowHead(Graphics2D g2, Point from, Point to, int tileSize) {
            float fx = from.x * tileSize + tileSize * 0.5f;
            float fy = from.y * tileSize + tileSize * 0.5f;
            float tx = to.x * tileSize + tileSize * 0.5f;
            float ty = to.y * tileSize + tileSize * 0.5f;
            double dx = tx - fx;
            double dy = ty - fy;
            double len = Math.hypot(dx, dy);
            if (len < 1e-3) {
                return;
            }
            double ux = dx / len;
            double uy = dy / len;
            double tipInset = Math.min(tileSize * 0.38, len * 0.45);
            double baseX = tx - ux * tipInset;
            double baseY = ty - uy * tipInset;
            double side = tileSize * 0.3;
            double px = -uy * side;
            double py = ux * side;
            Path2D.Float tri = new Path2D.Float();
            tri.moveTo(tx, ty);
            tri.lineTo((float) (baseX + px), (float) (baseY + py));
            tri.lineTo((float) (baseX - px), (float) (baseY - py));
            tri.closePath();
            g2.setColor(new Color(255, 230, 140, 235));
            g2.fill(tri);
            g2.setColor(new Color(70, 55, 25, 210));
            g2.draw(tri);
        }

        private void drawTerrain(Graphics g, TerrainType terrainType, int x, int y) {
            Image img = assetManager.getTerrainImage(terrainType);
            if (img != null) {
                AssetManager.drawTerrainImageOnTile(g, img, x, y, tileSize, terrainType.fallbackMapColor());
                return;
            }

            g.setColor(terrainType.fallbackMapColor());
            g.fillRect(x, y, tileSize, tileSize);
        }

        private void drawStructure(Graphics g, Tile tile, int x, int y) {
            Structure structure = tile.getStructure();
            if (structure == null) {
                return;
            }
            Color tint = structureTintForMapStructure(structure);
            Image img = tint == null
                ? assetManager.getStructureImage(structure.getType())
                : assetManager.getStructureImageTinted(structure.getType(), tint);
            if (img != null) {
                int inset = Math.max(2, tileSize / 8);
                g.drawImage(img, x + inset, y + inset, tileSize - (inset * 2), tileSize - (inset * 2), null);
            }
        }

        /**
         * In play, ownership comes from {@link Structure#getOwner()}; neutral ({@code null} owner) uses gray trim.
         */
        private static Color structureTintForMapStructure(Structure structure) {
            if (structure.getOwner() == null) {
                return AssetManager.STRUCTURE_NEUTRAL_RECOLOR;
            }
            return spriteTintForOwner(structure.getOwner());
        }

        private float combatHopOffsetPixels(Unit unit) {
            if (!combatAnimRunning || unit == null) {
                return 0f;
            }
            long hopNanos = (long) (COMBAT_HOP_MS * 1_000_000L);
            long elapsed = System.nanoTime() - combatAnimPhaseStartNanos;
            float t = clamp01(elapsed / (float) hopNanos);
            float amplitude = tileSize / 4f;
            float hop = -amplitude * (float) Math.sin(Math.PI * t);
            if (combatAnimPhase == COMBAT_PHASE_ATTACK_HOP && unit == combatAnimAttacker) {
                return hop;
            }
            if (combatAnimPhase == COMBAT_PHASE_COUNTER_HOP && unit == combatAnimDefender) {
                return hop;
            }
            return 0f;
        }

        private static void drawUnitHealthBar(Graphics2D g2, Unit unit, int tilePixelX, int tilePixelY, int tileSize) {
            if (unit == null || !unit.isAlive()) {
                return;
            }
            int max = unit.getMaxHealth();
            if (max <= 0) {
                return;
            }
            float ratio = Math.min(1f, Math.max(0f, unit.getHealth() / (float) max));
            int pad = Math.max(1, Math.round(tileSize * 0.06f));
            int barW = Math.max(2, tileSize / 2);
            int barH = Math.max(2, Math.round(tileSize * 0.09f));
            int bx = tilePixelX + pad;
            int by = tilePixelY + pad;
            var hints = g2.getRenderingHints();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(12, 14, 18, 230));
                g2.fill(new RoundRectangle2D.Float(bx, by, barW, barH, 2f, 2f));
                int fillW = Math.max(0, Math.round(barW * ratio));
                Color fill;
                if (ratio >= 2f / 3f) {
                    fill = new Color(55, 200, 95);
                } else if (ratio >= 1f / 3f) {
                    fill = new Color(230, 200, 55);
                } else {
                    fill = new Color(230, 65, 65);
                }
                g2.setColor(fill);
                g2.fill(new RoundRectangle2D.Float(bx, by, fillW, barH, 2f, 2f));
                g2.setColor(new Color(0, 0, 0, 140));
                g2.draw(new RoundRectangle2D.Float(bx, by, barW, barH, 2f, 2f));
            } finally {
                g2.setRenderingHints(hints);
            }
        }

        /** Cloaked stealth units appear semi-transparent to their controller so they still look "hidden". */
        private static final float CLOAKED_OWNER_SPRITE_ALPHA = 0.5f;

        private boolean isOwnedCloakedStealthSprite(Unit unit) {
            return unit != null && unit.isCloaked() && session.isOwnedByActivePlayer(unit);
        }

        private void runWithOwnedCloakedSpriteOpacity(Graphics g, Unit unit, Runnable drawSpriteBody) {
            if (!(g instanceof Graphics2D g2)) {
                drawSpriteBody.run();
                return;
            }
            if (!isOwnedCloakedStealthSprite(unit)) {
                drawSpriteBody.run();
                return;
            }
            Composite saved = g2.getComposite();
            try {
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, CLOAKED_OWNER_SPRITE_ALPHA));
                drawSpriteBody.run();
            } finally {
                g2.setComposite(saved);
            }
        }

        private void drawPlacedUnit(Graphics g, Tile tile, int x, int y) {
            Unit unit = tile.getUnit();
            if (unit != null) {
                if (!session.canActivePlayerPerceiveUnit(unit)) {
                    return;
                }
                if (isGlidingUnitDrawnInOverlay(tile)) {
                    return;
                }
                float hopY = combatHopOffsetPixels(unit);
                String spriteId = tile.getUnitSpriteId();
                FacingDirection facing = tile.getUnitFacing();
                Image frame = spriteId != null
                    ? unitFrameForOwner(spriteId, facing == null ? FacingDirection.EAST : facing, unit.getOwner())
                    : null;
                if (frame != null) {
                    runWithOwnedCloakedSpriteOpacity(g, unit, () ->
                        AssetManager.drawUnitFrameOnTile(g, frame, x, y + hopY, tileSize));
                    drawUnitHealthBar((Graphics2D) g, unit, x, y, tileSize);
                    return;
                }
                Color c = ownerColor(unit.getOwner());
                runWithOwnedCloakedSpriteOpacity(g, unit, () -> {
                    g.setColor(c);
                    int inset = Math.max(4, tileSize / 6);
                    int oy = Math.round(hopY);
                    g.fillOval(x + inset, y + inset + oy, tileSize - inset * 2, tileSize - inset * 2);
                    g.setColor(Color.WHITE);
                    g.drawOval(x + inset, y + inset + oy, tileSize - inset * 2, tileSize - inset * 2);
                });
                drawUnitHealthBar((Graphics2D) g, unit, x, y, tileSize);
                return;
            }
            if (tile.getUnitSpriteId() == null) {
                return;
            }
            FacingDirection facing = tile.getUnitFacing();
            Color teamColor = teamColorForPlacedSprite(tile.getUnitTeamId());
            Image frame = teamColor == null
                ? assetManager.getUnitFrame(tile.getUnitSpriteId(), facing == null ? FacingDirection.EAST : facing, 0)
                : assetManager.getUnitFrameTinted(tile.getUnitSpriteId(), facing == null ? FacingDirection.EAST : facing, 0, teamColor);
            if (frame != null) {
                AssetManager.drawUnitFrameOnTile(g, frame, x, y, tileSize);
            }
        }

        private Image unitFrameForOwner(String spriteId, FacingDirection facing, Player owner) {
            Color tint = spriteTintForOwner(owner);
            if (tint == null) {
                return assetManager.getUnitFrame(spriteId, facing, 0);
            }
            return assetManager.getUnitFrameTinted(spriteId, facing, 0, tint);
        }

        private static Color teamColorForPlacedSprite(Integer teamId) {
            if (teamId == null || teamId <= 1) {
                return null;
            }
            return switch (teamId) {
                case 2 -> new Color(70, 130, 210);
                case 3 -> new Color(80, 170, 90);
                case 4 -> new Color(210, 190, 70);
                default -> new Color(150, 140, 200);
            };
        }

        private static Color spriteTintForOwner(Player owner) {
            if (owner == null) {
                return null;
            }
            return switch (owner.getName()) {
                case "Red" -> null;
                case "Blue" -> new Color(70, 130, 210);
                case "Green" -> new Color(80, 170, 90);
                case "Yellow" -> new Color(210, 190, 70);
                default -> new Color(150, 140, 200);
            };
        }

        private static Color ownerColor(Player owner) {
            if (owner == null) {
                return new Color(140, 140, 150);
            }
            return switch (owner.getName()) {
                case "Red" -> new Color(200, 70, 70);
                case "Blue" -> new Color(70, 130, 210);
                case "Green" -> new Color(80, 170, 90);
                case "Yellow" -> new Color(210, 190, 70);
                default -> new Color(150, 140, 200);
            };
        }

        private void drawCursor(Graphics g) {
            if (selectedCell == null) {
                return;
            }
            int px = selectedCell.x * tileSize;
            int py = selectedCell.y * tileSize;
            g.setColor(new Color(255, 245, 110, 220));
            g.drawRect(px + 1, py + 1, tileSize - 2, tileSize - 2);
            g.setColor(new Color(255, 180, 0, 220));
            g.drawRect(px + 2, py + 2, tileSize - 4, tileSize - 4);
        }
    }

    private record SelectionInfo(int x, int y, Tile tile) {
    }

    private static class GameInfoPanel extends JPanel {
        private static final String SECONDARY_HEX = "A5B1AC";
        private static final String PRIMARY_HEX = "E0E6E3";

        private GameMap map;
        private PlayableGameSession session;
        private final JLabel gameMissionLabel = makeBodyLabel();
        private final JLabel gameMapMetaLabel = makeMutedLabel();
        private final JLabel gameTeamLabel = makeMutedLabel();
        private final JLabel tileCoordLabel = makeBodyLabel();
        private final JLabel tileTerrainLabel = makeBodyLabel();
        private final JLabel tileStructureLabel = makeMutedLabel();
        private final JLabel unitBodyLabel = makeBodyLabel();
        private final JPanel unitAbilityStrip;

        private GameInfoPanel(GameMap map, PlayableGameSession session) {
            super(new BorderLayout());
            this.map = map;
            this.session = session;
            setBorder(BorderFactory.createCompoundBorder(
                Theme.topDivider(),
                Theme.padding(Theme.SPACING_MD, Theme.SPACING_MD)
            ));
            setBackground(Theme.PANEL);
            setPreferredSize(new Dimension(0, 210));

            tileTerrainLabel.setVerticalAlignment(JLabel.TOP);
            unitBodyLabel.setVerticalAlignment(JLabel.TOP);

            unitAbilityStrip = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
            unitAbilityStrip.setOpaque(false);

            JPanel columns = new JPanel(new GridLayout(1, 3, Theme.SPACING_MD, 0));
            columns.setOpaque(false);
            columns.add(MilitaryComponents.titledHudBlock("Mission", gameSection()));
            columns.add(MilitaryComponents.titledHudBlock("Tile", tileSection()));
            columns.add(MilitaryComponents.titledHudBlock("Unit", unitSection()));

            add(columns, BorderLayout.CENTER);
            resetInspectionPlaceholders();
        }

        void bindAuthoritativeSession(PlayableGameSession nextSession) {
            this.session = Objects.requireNonNull(nextSession);
            this.map = nextSession.getMap();
            resetInspectionPlaceholders();
        }

        private static JLabel makeBodyLabel() {
            JLabel l = new JLabel("");
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setForeground(Theme.TEXT_PRIMARY);
            l.setFont(Theme.fontBody());
            l.setOpaque(false);
            return l;
        }

        private static JLabel makeMutedLabel() {
            JLabel l = new JLabel("");
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            l.setForeground(Theme.TEXT_SECONDARY);
            l.setFont(Theme.fontMicro());
            l.setOpaque(false);
            return l;
        }

        private void resetInspectionPlaceholders() {
            tileCoordLabel.setText("—");
            tileTerrainLabel.setText("<html><span style='color:#" + SECONDARY_HEX
                + "'>Select a tile on the map.</span></html>");
            tileStructureLabel.setText(" ");
            unitBodyLabel.setText("<html><span style='color:#" + SECONDARY_HEX
                + "'>No tile selected.</span></html>");
            clearAbilityStrip();
        }

        private void clearAbilityStrip() {
            unitAbilityStrip.removeAll();
            unitAbilityStrip.revalidate();
            unitAbilityStrip.repaint();
        }

        private void setAbilitiesForUnit(Unit unit) {
            clearAbilityStrip();
            if (unit == null) {
                return;
            }
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (String a : unit.getAbilities()) {
                if (a != null && !a.isBlank()) {
                    seen.add(a);
                }
            }
            for (String a : seen) {
                JLabel chip = new JLabel(AbilityPresentation.abilityIcon(a, 22));
                chip.setToolTipText("<html><body style='width:280px'>" + escapeHtml(AbilityPresentation.tooltipText(a))
                    + "</body></html>");
                unitAbilityStrip.add(chip);
            }
            unitAbilityStrip.revalidate();
            unitAbilityStrip.repaint();
        }

        private JPanel gameSection() {
            JPanel p = new JPanel();
            p.setOpaque(false);
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.add(gameMissionLabel);
            p.add(Box.createVerticalStrut(Theme.SPACING_XS));
            p.add(gameMapMetaLabel);
            p.add(Box.createVerticalStrut(2));
            p.add(gameTeamLabel);
            return p;
        }

        private JPanel tileSection() {
            JPanel p = new JPanel();
            p.setOpaque(false);
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.add(tileCoordLabel);
            p.add(Box.createVerticalStrut(Theme.SPACING_XS));
            p.add(tileTerrainLabel);
            p.add(Box.createVerticalStrut(Theme.SPACING_XS));
            p.add(tileStructureLabel);
            return p;
        }

        private JPanel unitSection() {
            JPanel wrap = new JPanel(new BorderLayout(0, Theme.SPACING_XS));
            wrap.setOpaque(false);
            wrap.add(unitBodyLabel, BorderLayout.NORTH);
            wrap.add(unitAbilityStrip, BorderLayout.SOUTH);
            return wrap;
        }

        private void updateTurn(String levelName, int turnNumber) {
            String displayLevel = levelName == null || levelName.isBlank() ? "Skirmish" : levelName;
            gameMissionLabel.setText("<html><span style='color:#" + PRIMARY_HEX + "'><b>"
                + escapeHtml(displayLevel) + "</b></span></html>");
            gameMapMetaLabel.setText("Field " + map.getWidth() + " \u00D7 " + map.getHeight()
                + " \u00B7 " + map.getTeamCount() + " factions");
            gameTeamLabel.setText("Active team \u00B7 " + (session.getActivePlayerIndex() + 1));
        }

        private void updateSelection(SelectionInfo selectionInfo) {
            Tile tile = selectionInfo.tile();
            int x = selectionInfo.x();
            int y = selectionInfo.y();
            TerrainType terrain = tile.getTerrainType();

            tileCoordLabel.setText("<html><span style='color:#" + SECONDARY_HEX + "'>POS</span> "
                + "<b>(" + x + ", " + y + ")</b></html>");
            int defPct = (int) Math.round(terrain.getDefenseModifier() * 100.0);
            int footCost = terrain.movementCost(UnitType.Commando);
            String costNote = footCost >= 100 ? "Impassable (foot)" : "Move cost (foot) " + footCost;
            tileTerrainLabel.setText("<html><b>" + escapeHtml(niceName(terrain.name())) + "</b><br/>"
                + "<span style='color:#" + SECONDARY_HEX + "'>"
                + "Defense +" + defPct + "% \u00B7 " + costNote + "</span></html>");
            Structure st = tile.getStructure();
            if (st == null) {
                tileStructureLabel.setText("Structure \u00B7 none");
            } else {
                Player so = st.getOwner();
                String ownerStr = so == null ? "Neutral" : escapeHtml(so.getName());
                tileStructureLabel.setText("<html><span style='color:#" + PRIMARY_HEX + "'><b>"
                    + escapeHtml(niceName(st.getType().name()))
                    + "</b></span> \u00B7 " + ownerStr
                    + "<br/><span style='color:#03A9F4;font-size:11px'>"
                    + "Foot units capture by ending turn on tile.</span></html>");
            }

            Unit unit = tile.getUnit();
            if (unit != null) {
                String ownerName = unit.getOwner() == null ? "\u2014" : unit.getOwner().getName();
                boolean isYours = unit.getOwner() == session.getActivePlayer();
                String actionStatus = unit.hasMoved() ? "used" : "ready";
                String actionColor = unit.hasMoved() ? "#" + SECONDARY_HEX : "#4CAF50";
                String turnNote = isYours
                    ? ("<span style='color:" + actionColor + "'>Action " + actionStatus + "</span>")
                    : "<span style='color:#F44336'>Hostile contact</span>";
                unitBodyLabel.setText("<html><body style='width:260px;color:#" + PRIMARY_HEX + "'>"
                    + "<b>" + escapeHtml(niceName(unit.getUnitType().name())) + "</b>"
                    + " <span style='color:#" + SECONDARY_HEX + "'>\u00B7 " + escapeHtml(ownerName) + "</span><br/>"
                    + "<span style='color:#" + SECONDARY_HEX + "'>HP</span> " + unit.getHealth()
                    + " \u00B7 <span style='color:#" + SECONDARY_HEX + "'>Move</span> " + unit.getMovementSpeed()
                    + " \u00B7 <span style='color:#" + SECONDARY_HEX + "'>Range</span> "
                    + unit.getAttackRangeDisplayString()
                    + " \u00B7 <span style='color:#" + SECONDARY_HEX + "'>Power</span> " + unit.getAttackPower() + "<br/>"
                    + "<span style='color:#" + SECONDARY_HEX + "'>Armor</span> " + unit.getArmorType()
                    + " \u00B7 <span style='color:#" + SECONDARY_HEX + "'>Attack</span> " + unit.getAttackType() + "<br/>"
                    + turnNote
                    + "</body></html>");
                setAbilitiesForUnit(unit);
                return;
            }
            clearAbilityStrip();
            String sprite = tile.getUnitSpriteId();
            if (sprite != null) {
                FacingDirection face = tile.getUnitFacing();
                Integer ut = tile.getUnitTeamId();
                String teamPart = ut == null ? "" : "Team " + ut + " \u00B7 ";
                unitBodyLabel.setText("<html><body style='width:260px;color:#" + PRIMARY_HEX + "'>"
                    + "<b>Placed unit</b> <span style='color:#" + SECONDARY_HEX + "'>(map asset)</span><br/>"
                    + teamPart + "Sprite: " + escapeHtml(sprite) + "<br/>"
                    + "Facing: " + (face == null ? "east" : niceName(face.name())) + "<br/>"
                    + "<span style='color:#" + SECONDARY_HEX + ";font-size:11px'>"
                    + "Editor preview \u2014 start mission spawns real units.</span>"
                    + "</body></html>");
                return;
            }
            unitBodyLabel.setText("<html><span style='color:#" + SECONDARY_HEX
                + "'>No unit on this tile.</span></html>");
            clearAbilityStrip();
        }

        private static String escapeHtml(String s) {
            if (s == null) {
                return "";
            }
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }

        private String niceName(String enumValue) {
            return enumValue.toLowerCase().replace("_", " ");
        }
    }
}
