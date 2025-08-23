// java
package com.Tbence132545.Melodigram.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.LongConsumer;

public class AnimationPanel extends JPanel {
    private final List<FallingNote> notes = new CopyOnWriteArrayList<>();
    private final Function<Integer, PianoWindow.KeyInfo> keyInfoProvider;
    private long currentTimeMillis = 0;
    private final int lowestNote;
    private final int highestNote;

    public static final double PIXELS_PER_MS = 0.1;
    private long totalDurationMillis = 0;

    // Drag state
    private boolean dragging = false;
    private int pressY = 0;
    private long pressTime = 0;
    private Runnable onDragStart;
    private LongConsumer onTimeChange;
    private Runnable onDragEnd;

    private static final long FALL_DURATION_MS = 2000;

    public AnimationPanel(Function<Integer, PianoWindow.KeyInfo> keyInfoProvider, int lowestNote, int highestNote) {
        setPreferredSize(new Dimension(800, 200));
        setBackground(Color.BLACK);
        this.keyInfoProvider = keyInfoProvider;
        this.lowestNote = lowestNote;
        this.highestNote = highestNote;

        MouseAdapter drag = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                dragging = true;
                pressY = e.getY();
                pressTime = currentTimeMillis;
                setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
                if (onDragStart != null) onDragStart.run();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (!dragging) return;
                int dy = e.getY() - pressY;
                long newTime = pressTime - Math.round(dy / PIXELS_PER_MS);
                newTime = Math.max(0, Math.min(newTime, totalDurationMillis));
                updatePlaybackTime(newTime);
                repaint();
                if (onTimeChange != null) onTimeChange.accept(newTime);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!dragging) return;
                dragging = false;
                setCursor(Cursor.getDefaultCursor());
                if (onDragEnd != null) onDragEnd.run();
            }
        };
        addMouseListener(drag);
        addMouseMotionListener(drag);
    }

    public void setTotalDurationMillis(long totalDurationMillis) {
        this.totalDurationMillis = Math.max(0, totalDurationMillis);
    }

    public void setOnDragStart(Runnable onDragStart) { this.onDragStart = onDragStart; }
    public void setOnTimeChange(LongConsumer onTimeChange) { this.onTimeChange = onTimeChange; }
    public void setOnDragEnd(Runnable onDragEnd) { this.onDragEnd = onDragEnd; }

    public long getCurrentTimeMillis() {
        return currentTimeMillis;
    }

    public void tick(long deltaMillis) {
        currentTimeMillis += deltaMillis;
        repaint();
    }

    // Original practice helper (still used elsewhere if needed)
    public List<Integer> getActiveNotesToBePlayed(long currentTimeMillis) {
        List<Integer> active = new ArrayList<>();
        for (FallingNote note : notes) {
            if (Math.abs(note.noteOnTime - currentTimeMillis) < 50) {
                active.add(note.midiNote);
            }
        }
        return active;
    }

    // NEW: used to detect onsets crossed between frames for practice gating
    public List<Integer> getNotesStartingBetween(long startMs, long endMs) {
        List<Integer> onsets = new ArrayList<>();
        if (endMs < startMs) return onsets;
        for (FallingNote note : notes) {
            if (note.noteOnTime > startMs && note.noteOnTime <= endMs) {
                onsets.add(note.midiNote);
            }
        }
        return onsets;
    }

    public void updatePlaybackTime(long timeMillis) {
        this.currentTimeMillis = timeMillis;
    }

    public void addFallingNote(int midiNote, long noteOnTime, long noteOffTime, boolean isBlackKey) {
        notes.add(new FallingNote(midiNote, noteOnTime, noteOffTime, isBlackKey));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int panelHeight = getHeight();

        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        g2.setColor(new Color(100, 100, 100, 150));
        for (int midiNote = lowestNote; midiNote <= highestNote; midiNote++) {
            if (midiNote % 12 == 0) {
                PianoWindow.KeyInfo keyInfo = keyInfoProvider.apply(midiNote);
                if (keyInfo != null && !keyInfo.isBlack()) {
                    int x = keyInfo.x();
                    g2.drawLine(x, 0, x, panelHeight);
                }
            }
        }

        for (FallingNote note : notes) {
            note.draw(g2, currentTimeMillis, panelHeight);
        }
    }

    public class FallingNote {
        int midiNote;
        long noteOnTime, noteOffTime;
        boolean isBlackKey;

        public FallingNote(int midiNote, long on, long off, boolean isBlackKey) {
            this.midiNote = midiNote;
            this.noteOnTime = on;
            this.noteOffTime = off;
            this.isBlackKey = isBlackKey;
        }

        void draw(Graphics2D g, long currentMillis, int panelHeight) {
            long noteDuration = noteOffTime - noteOnTime;
            double pixelsPerMs = PIXELS_PER_MS;
            int noteHeight = (int) (noteDuration * pixelsPerMs);

            long fallStartTime = noteOnTime - FALL_DURATION_MS;

            if (currentMillis < fallStartTime || currentMillis > noteOffTime) return;

            PianoWindow.KeyInfo keyInfo = keyInfoProvider.apply(midiNote);
            if (keyInfo == null) return;
            int x = keyInfo.x();
            int width = keyInfo.width();

            int bottomY;
            if (currentMillis < noteOnTime) {
                double progress = (double) (currentMillis - fallStartTime) / FALL_DURATION_MS;
                progress = Math.min(Math.max(progress, 0), 1.0);

                int startY = -noteHeight;
                int endY = panelHeight;
                bottomY = (int) (startY + progress * (endY - startY));
            } else {
                double sinkProgress = (double) (currentMillis - noteOnTime) / noteDuration;
                sinkProgress = Math.min(Math.max(sinkProgress, 0), 1.0);

                int startY = panelHeight;
                int endY = panelHeight + noteHeight;
                bottomY = (int) (startY + sinkProgress * (endY - startY));
            }

            int topY = bottomY - noteHeight;
            if (topY < panelHeight) {
                setNoteColor((Graphics2D) g);
                g.fillRoundRect(x, topY, width, noteHeight, 10, 10);
            }
        }

        private void setNoteColor(Graphics2D g) {
            if (isBlackKey) {
                g.setColor(new Color(255, 100, 100, 180));
            } else {
                g.setColor(new Color(255, 215, 0, 180));
            }
        }
    }
}