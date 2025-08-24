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

    // --- Drawing & Animation Constants ---
    private static final double PIXELS_PER_MILLISECOND = 0.1;
    private static final long NOTE_FALL_DURATION_MS = 2000;
    private static final int NOTE_CORNER_RADIUS = 10;
    private static final Color COLOR_GRID_LINE = new Color(100, 100, 100, 150);
    private static final Color COLOR_BLACK_NOTE = new Color(255, 100, 100, 180);
    private static final Color COLOR_WHITE_NOTE = new Color(255, 215, 0, 180);

    // --- State ---
    private final List<FallingNote> notes = new CopyOnWriteArrayList<>();
    private final Function<Integer, PianoWindow.KeyInfo> keyInfoProvider;
    private long currentTimeMillis = 0;
    private long totalDurationMillis = 0;
    private final int lowestNote;
    private final int highestNote;

    // --- Callbacks for Controller ---
    private Runnable onDragStart;
    private LongConsumer onTimeChange;
    private Runnable onDragEnd;

    public AnimationPanel(Function<Integer, PianoWindow.KeyInfo> keyInfoProvider, int lowestNote, int highestNote) {
        this.keyInfoProvider = keyInfoProvider;
        this.lowestNote = lowestNote;
        this.highestNote = highestNote;

        setBackground(Color.BLACK);

        // Encapsulated drag handler is much cleaner
        TimelineDragHandler dragHandler = new TimelineDragHandler();
        addMouseListener(dragHandler);
        addMouseMotionListener(dragHandler);
    }

    // --- Public API for Controller ---

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

    public void updatePlaybackTime(long timeMillis) {
        this.currentTimeMillis = timeMillis;
    }

    public void addFallingNote(int midiNote, long noteOnTime, long noteOffTime, boolean isBlackKey) {
        notes.add(new FallingNote(midiNote, noteOnTime, noteOffTime, isBlackKey));
    }

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

    // --- Drawing Logic ---

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        drawGridLines(g2d);

        for (FallingNote note : notes) {
            note.draw(g2d, currentTimeMillis, getHeight());
        }
    }

    private void drawGridLines(Graphics2D g2d) {
        g2d.setColor(COLOR_GRID_LINE);
        for (int midiNote = lowestNote; midiNote <= highestNote; midiNote++) {
            // Draw a line at the start of every C key
            if (midiNote % 12 == 0) {
                PianoWindow.KeyInfo keyInfo = keyInfoProvider.apply(midiNote);
                if (keyInfo != null && !keyInfo.isBlack()) {
                    g2d.drawLine(keyInfo.x(), 0, keyInfo.x(), getHeight());
                }
            }
        }
    }

    // --- Inner Classes ---

    /**
     * An inner class that cleanly encapsulates all logic and state for handling timeline dragging.
     */
    private class TimelineDragHandler extends MouseAdapter {
        private boolean isDragging = false;
        private int pressY = 0;
        private long pressTime = 0;

        @Override
        public void mousePressed(MouseEvent e) {
            isDragging = true;
            pressY = e.getY();
            pressTime = currentTimeMillis;
            setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            if (onDragStart != null) onDragStart.run();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            if (!isDragging) return;

            int dy = e.getY() - pressY;
            long timeDelta = (long) (dy / PIXELS_PER_MILLISECOND);
            long newTime = pressTime - timeDelta;

            // Clamp the new time within the valid range
            newTime = Math.max(0, Math.min(newTime, totalDurationMillis));

            updatePlaybackTime(newTime);
            repaint();
            if (onTimeChange != null) onTimeChange.accept(newTime);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (!isDragging) return;
            isDragging = false;
            setCursor(Cursor.getDefaultCursor());
            if (onDragEnd != null) onDragEnd.run();
        }
    }

    /**
     * Represents a single falling note and handles its own drawing logic.
     */
    private class FallingNote {
        private final int midiNote;
        private final long noteOnTime;
        private final long noteOffTime;
        private final boolean isBlackKey;

        public FallingNote(int midiNote, long on, long off, boolean isBlackKey) {
            this.midiNote = midiNote;
            this.noteOnTime = on;
            this.noteOffTime = off;
            this.isBlackKey = isBlackKey;
        }

        void draw(Graphics2D g, long currentMillis, int panelHeight) {
            long fallStartTime = noteOnTime - NOTE_FALL_DURATION_MS;
            if (currentMillis < fallStartTime || currentMillis > noteOffTime) {
                return; // Note is not visible yet or has finished
            }

            PianoWindow.KeyInfo keyInfo = keyInfoProvider.apply(midiNote);
            if (keyInfo == null) return;

            long noteDuration = noteOffTime - noteOnTime;
            int noteHeight = (int) (noteDuration * PIXELS_PER_MILLISECOND);

            // The bottom of the note rectangle's position on the Y-axis
            int bottomY = (currentMillis < noteOnTime)
                    ? calculateFallingY(currentMillis, fallStartTime, noteHeight, panelHeight)
                    : calculateSinkingY(currentMillis, noteHeight, panelHeight);

            int topY = bottomY - noteHeight;

            // Only draw if the note is at all visible on the panel
            if (topY < panelHeight && bottomY > 0) {
                g.setColor(isBlackKey ? COLOR_BLACK_NOTE : COLOR_WHITE_NOTE);
                g.fillRoundRect(keyInfo.x(), topY, keyInfo.width(), noteHeight, NOTE_CORNER_RADIUS, NOTE_CORNER_RADIUS);
            }
        }

        /**
         * Calculates the note's Y position as it falls towards the play line.
         */
        private int calculateFallingY(long currentMillis, long fallStartTime, int noteHeight, int panelHeight) {
            double progress = (double) (currentMillis - fallStartTime) / NOTE_FALL_DURATION_MS;
            int startY = -noteHeight; // Starts completely off-screen at the top
            int endY = panelHeight;   // Ends at the play line
            return (int) (startY + progress * (endY - startY));
        }

        /**
         * Calculates the note's Y position as it "sinks" past the play line.
         */
        private int calculateSinkingY(long currentMillis, int noteHeight, int panelHeight) {
            long noteDuration = noteOffTime - noteOnTime;
            if (noteDuration <= 0) return panelHeight; // Avoid division by zero for very short notes

            double progress = (double) (currentMillis - noteOnTime) / noteDuration;
            int startY = panelHeight; // Starts at the play line
            int endY = panelHeight + noteHeight; // Ends when it has fully sunk below the line
            return (int) (startY + progress * (endY - startY));
        }
    }
}