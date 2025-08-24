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
    public static class HandAssignment {
        public final int midiNote;
        public final long on;
        public final long off;
        public final String hand; // "LEFT" | "RIGHT"

        public HandAssignment(int midiNote, long on, long off, String hand) {
            this.midiNote = midiNote;
            this.on = on;
            this.off = off;
            this.hand = hand;
        }
    }
    // --- Drawing & Animation Constants ---
    private static final double PIXELS_PER_MILLISECOND = 0.1;
    private static final long NOTE_FALL_DURATION_MS = 2000;
    private static final int NOTE_CORNER_RADIUS = 10;
    private static final Color COLOR_GRID_LINE = new Color(100, 100, 100, 150);
    private static final Color COLOR_BLACK_NOTE = new Color(255, 100, 100, 180);
    private static final Color COLOR_WHITE_NOTE = new Color(255, 215, 0, 180);
    private static final Color COLOR_LEFT_WHITE = new Color(135, 206, 250, 220); // Light Sky Blue
    private static final Color COLOR_LEFT_BLACK = new Color(25, 25, 112, 220);   // Midnight Blue
    private static final Color COLOR_RIGHT_WHITE = new Color(250, 128, 114, 220); // Salmon
    private static final Color COLOR_RIGHT_BLACK = new Color(178, 34, 34, 220);  // Firebrick

    private static final Font NOTE_TEXT_FONT = new Font("SansSerif", Font.BOLD, 16);
    private static final Color NOTE_TEXT_COLOR = Color.WHITE;
    // --- State ---
    private final List<FallingNote> notes = new CopyOnWriteArrayList<>();
    private final Function<Integer, PianoWindow.KeyInfo> keyInfoProvider;
    private long currentTimeMillis = 0;
    private long totalDurationMillis = 0;
    private final int lowestNote;
    private final int highestNote;
    private boolean isHandAssignmentEnabled = false;

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
        NoteClickHandler clickHandler = new NoteClickHandler();
        addMouseListener(clickHandler);
    }

    // --- Public API for Controller ---
    public List<HandAssignment> getAssignedNotes() {
        List<HandAssignment> result = new ArrayList<>();
        for (FallingNote n : notes) {
            if (n.hand != null) {
                result.add(new HandAssignment(
                        n.midiNote,
                        n.noteOnTime,
                        n.noteOffTime,
                        n.hand.name()
                ));
            }
        }
        return result;
    }
    public void applyHandAssignments(List<HandAssignment> assignments) {
        if (assignments == null || assignments.isEmpty()) return;
        final long TOL_MS = 5; // small time tolerance to account for rounding

        for (HandAssignment a : assignments) {
            for (FallingNote n : notes) {
                if (n.midiNote == a.midiNote
                        && Math.abs(n.noteOnTime - a.on) <= TOL_MS
                        && Math.abs(n.noteOffTime - a.off) <= TOL_MS) {
                    try {
                        n.setHand(FallingNote.Hands.valueOf(a.hand));
                    } catch (IllegalArgumentException ignored) {
                        // Ignore invalid persisted value
                    }
                }
            }
        }
        repaint();
    }
    public void setHandAssignmentMode(boolean enabled) {
        this.isHandAssignmentEnabled = enabled;
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
    public Color getAssignedHighlightColor(int midiNote) {
        long t = getCurrentTimeMillis();
        for (int i = notes.size() - 1; i >= 0; i--) {
            FallingNote n = notes.get(i);
            if (n.midiNote == midiNote && t >= n.noteOnTime && t < n.noteOffTime && n.hand != null) {

                // --- THIS IS THE FIX ---

                // 1. Get the original semi-transparent color.
                Color semiTransparentColor = colorForHighlight(n);

                // 2. Return a NEW, FULLY OPAQUE version of that color for the key.
                return new Color(semiTransparentColor.getRed(), semiTransparentColor.getGreen(), semiTransparentColor.getBlue(), 255);
            }
        }
        return null;
    }

    private Color colorForHighlight(FallingNote n) {
        boolean isLeft = (n.hand == FallingNote.Hands.LEFT);
        boolean isBlack = n.isBlackKey;
        // Use the exact colors you use when drawing the falling notes
        return isLeft ? (isBlack ? COLOR_LEFT_BLACK : COLOR_LEFT_WHITE)
                : (isBlack ? COLOR_RIGHT_BLACK : COLOR_RIGHT_WHITE);
    }
    private class NoteClickHandler extends MouseAdapter {
        @Override
        public void mousePressed(MouseEvent e) {
            // Only process clicks if the mode is enabled
            if (!isHandAssignmentEnabled) {
                return;
            }

            // Iterate backwards to click the "top" note if they overlap
            for (int i = notes.size() - 1; i >= 0; i--) {
                FallingNote note = notes.get(i);
                if (note.getBounds().contains(e.getPoint())) {

                    if (SwingUtilities.isRightMouseButton(e)) {
                        note.setHand(FallingNote.Hands.RIGHT);
                    } else if (SwingUtilities.isLeftMouseButton(e)) {
                        // In assignment mode, left click assigns left hand
                        note.setHand(FallingNote.Hands.LEFT);
                    }

                    repaint(); // Redraw to show color change
                    return; // Stop after handling one note
                }
            }
        }
    }


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
        private enum Hands {
            LEFT, RIGHT
        }
        private final int midiNote;
        private final long noteOnTime;
        private final long noteOffTime;
        private final boolean isBlackKey;
        private Hands hand = null;
        private final Rectangle bounds = new Rectangle();
        public FallingNote(int midiNote, long on, long off, boolean isBlackKey) {
            this.midiNote = midiNote;
            this.noteOnTime = on;
            this.noteOffTime = off;
            this.isBlackKey = isBlackKey;
        }
        public void setHand(Hands hand) {
            this.hand = hand;
        }
        public Rectangle getBounds() {
            return this.bounds;
        }
        void draw(Graphics2D g, long currentMillis, int panelHeight) {
            long fallStartTime = noteOnTime - NOTE_FALL_DURATION_MS;
            if (currentMillis < fallStartTime || currentMillis > noteOffTime) {
                bounds.setBounds(0, 0, 0, 0); // Not visible, empty bounds
                return;
            }

            PianoWindow.KeyInfo keyInfo = keyInfoProvider.apply(midiNote);
            if (keyInfo == null) return;

            long noteDuration = noteOffTime - noteOnTime;
            int noteHeight = (int) (noteDuration * PIXELS_PER_MILLISECOND);

            int bottomY = (currentMillis < noteOnTime)
                    ? calculateFallingY(currentMillis, fallStartTime, noteHeight, panelHeight)
                    : calculateSinkingY(currentMillis, noteHeight, panelHeight);

            int topY = bottomY - noteHeight;

            bounds.setBounds(keyInfo.x(), topY, keyInfo.width(), noteHeight);

            if (topY < panelHeight && bottomY > 0) {

                if (hand == Hands.LEFT) {
                    g.setColor(isBlackKey ? COLOR_LEFT_BLACK : COLOR_LEFT_WHITE);
                } else if (hand == Hands.RIGHT) {
                    g.setColor(isBlackKey ? COLOR_RIGHT_BLACK : COLOR_RIGHT_WHITE);
                } else {
                    g.setColor(isBlackKey ? COLOR_BLACK_NOTE : COLOR_WHITE_NOTE);
                }


                g.fillRoundRect(bounds.x, bounds.y, bounds.width, noteHeight, NOTE_CORNER_RADIUS, NOTE_CORNER_RADIUS);
                if(hand!=null){
                    String text = (hand == Hands.LEFT) ? "L": "R";
                    g.setFont(NOTE_TEXT_FONT);
                    g.setColor(NOTE_TEXT_COLOR);
                    FontMetrics fm = g.getFontMetrics();
                    int textWidth = fm.stringWidth(text);
                    int textHeight = fm.getAscent();
                    int textX = bounds.x + (bounds.width - textWidth) / 2;
                    int textY = bounds.y + (bounds.height + textHeight) / 2;
                    g.drawString(text, textX, textY);
                }
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