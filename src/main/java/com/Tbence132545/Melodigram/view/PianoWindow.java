// java
package com.Tbence132545.Melodigram.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.Map;

public class PianoWindow extends JFrame {
    private final JLayeredPane pianoPanel;
    private final JPanel controlPanel;
    private final AnimationPanel animationPanel;
    private SeekBar seekBar;
    private final String[] keyTypes = {"w", "b", "w", "b", "w", "w", "b", "w", "b", "w", "b", "w"};
    private int whiteKeyWidth = 50;
    private final int whiteKeyHeight = 150;
    private int blackKeyWidth = 30;
    private final int blackKeyHeight = 100;
    private final Map<Integer, JButton> noteToKeyButton = new HashMap<>();
    private final int lowestNote;
    private final int highestNote;
    private final JButton playButton;
    private final JButton backButton;
    private final JButton backwardButton;
    private final JButton forwardButton;

    public PianoWindow(int lowestNote, int highestNote) {
        this.lowestNote = Math.max(lowestNote, 0);
        this.highestNote = Math.min(highestNote, 127);

        setTitle("Piano Visualization");
        setSize(800, 250);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        pianoPanel = new JLayeredPane();
        pianoPanel.setPreferredSize(new Dimension(800, whiteKeyHeight));

        // Red line panel
        JPanel redLinePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.RED);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        redLinePanel.setPreferredSize(new Dimension(0, 3));

        JPanel pianoWithLine = new JPanel(new BorderLayout());
        pianoWithLine.add(redLinePanel, BorderLayout.NORTH);
        pianoWithLine.add(pianoPanel, BorderLayout.CENTER);

        controlPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        controlPanel.setBackground(new Color(45, 45, 45));

        int buttonWidth = 50;
        int buttonHeight = 50;
        Dimension buttonSize = new Dimension(buttonWidth, buttonHeight);

        MouseAdapter hoverEffect = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                e.getComponent().setBackground(new Color(80, 80, 80));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                e.getComponent().setBackground(new Color(60, 60, 60));
            }
        };

        backButton = new JButton("←");
        styleControlButton(backButton, buttonSize, hoverEffect);

        backwardButton = new JButton("⏪");
        styleControlButton(backwardButton, buttonSize, hoverEffect);

        playButton = new JButton("▶");
        styleControlButton(playButton, buttonSize, hoverEffect);

        forwardButton = new JButton("⏩");
        styleControlButton(forwardButton, buttonSize, hoverEffect);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridheight = 2;
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        controlPanel.add(backButton, gbc);

        JPanel centerButtonPanel = new JPanel(new GridBagLayout());
        centerButtonPanel.setOpaque(false);
        GridBagConstraints cbc = new GridBagConstraints();
        cbc.fill = GridBagConstraints.VERTICAL;

        centerButtonPanel.add(backwardButton, cbc);
        centerButtonPanel.add(playButton, cbc);
        centerButtonPanel.add(forwardButton, cbc);

        gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.fill = GridBagConstraints.VERTICAL;
        controlPanel.add(centerButtonPanel, gbc);

        animationPanel = new AnimationPanel(this::getKeyInfo, lowestNote, highestNote);
        add(controlPanel, BorderLayout.NORTH);
        add(animationPanel, BorderLayout.CENTER);
        add(pianoWithLine, BorderLayout.SOUTH);

        addComponentListener(new java.awt.event.ComponentAdapter() {
            public void componentResized(java.awt.event.ComponentEvent evt) {
                updatePianoKeys();
            }
        });
        updatePianoKeys();
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        getContentPane().setBackground(new Color(230, 230, 230));
        setVisible(true);
    }

    private void styleControlButton(JButton button, Dimension size, MouseAdapter hoverEffect) {
        button.setPreferredSize(size);
        button.setFont(new Font("SansSerif", Font.BOLD, 18));
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(60, 60, 60));
        button.addMouseListener(hoverEffect);
    }

    public void setBackwardButtonListener(ActionListener listener) {
        backwardButton.addActionListener(listener);
    }

    public void setForwardButtonListener(ActionListener listener) {
        forwardButton.addActionListener(listener);
    }

    private JButton createKeyButton(String keyType, int midiNote) {
        JButton keyButton = new JButton();
        keyButton.setFocusable(false);
        if ("w".equals(keyType)) {
            keyButton.setBackground(Color.WHITE);
            keyButton.setPreferredSize(new Dimension(whiteKeyWidth, whiteKeyHeight));
        } else {
            keyButton.setBackground(Color.BLACK);
            keyButton.setPreferredSize(new Dimension(blackKeyWidth, blackKeyHeight));
        }
        keyButton.setOpaque(true);
        keyButton.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        return keyButton;
    }

    public void disableButtons(boolean enabled) {
        if (enabled) {
            playButton.setEnabled(false);
            backwardButton.setEnabled(false);
            forwardButton.setEnabled(false);
            seekBar.setEnabled(false);
            seekBar.setUserInteractionEnabled(false);
        } else {
            playButton.setEnabled(true);
            backwardButton.setEnabled(true);
            forwardButton.setEnabled(true);
            seekBar.setEnabled(true);
            seekBar.setUserInteractionEnabled(true);
        }
    }

    public void setBackButtonListener(ActionListener listener) {
        backButton.addActionListener(listener);
    }

    private void updatePianoKeys() {
        pianoPanel.removeAll();

        int windowWidth = pianoPanel.getWidth();
        if (windowWidth == 0) windowWidth = getWidth();

        int whiteKeyCount = 0;
        for (int i = lowestNote; i <= highestNote; i++) {
            if ("w".equals(keyTypes[i % 12])) whiteKeyCount++;
        }

        whiteKeyWidth = windowWidth / whiteKeyCount;
        blackKeyWidth = (int) (whiteKeyWidth * 0.6);

        int whiteKeyIndex = 0;
        noteToKeyButton.clear();

        // --- Find closest C to the middle ---
        int midNote = (lowestNote + highestNote) / 2;
        int closestC = -1;
        int minDiff = Integer.MAX_VALUE;
        for (int i = lowestNote; i <= highestNote; i++) {
            if (i % 12 == 0) { // C note
                int diff = Math.abs(i - midNote);
                if (diff < minDiff) {
                    minDiff = diff;
                    closestC = i;
                }
            }
        }

        for (int i = lowestNote; i <= highestNote; i++) {
            String keyType = keyTypes[i % 12];
            JButton key = createKeyButton(keyType, i);

            if ("w".equals(keyType)) {
                key.setBounds(whiteKeyIndex * whiteKeyWidth, 0, whiteKeyWidth, whiteKeyHeight);

                // If this is the chosen C, put label on the key
                if (i == closestC) {
                    int octave = (i / 12) - 1;
                    JLabel label = new JLabel("C" + octave, SwingConstants.CENTER);
                    label.setFont(new Font("SansSerif", Font.BOLD, 14));
                    label.setForeground(Color.BLACK);
                    label.setBounds(0, whiteKeyHeight - 20, whiteKeyWidth, 20);
                    key.setLayout(null);
                    key.add(label);
                }

                pianoPanel.add(key, JLayeredPane.DEFAULT_LAYER);
                whiteKeyIndex++;
            } else {
                int x = (whiteKeyIndex - 1) * whiteKeyWidth + (whiteKeyWidth - blackKeyWidth / 2);
                key.setBounds(x, 0, blackKeyWidth, blackKeyHeight);
                pianoPanel.add(key, JLayeredPane.PALETTE_LAYER);
            }
            noteToKeyButton.put(i, key);
        }

        pianoPanel.revalidate();
        pianoPanel.repaint();
    }

    public void highlightNote(int midiNote) {
        JButton key = noteToKeyButton.get(midiNote);
        if (key != null) {
            String keyType = keyTypes[midiNote % 12];
            if ("w".equals(keyType)) {
                key.setBackground(new Color(255, 200, 100));
            } else {
                key.setBackground(Color.RED);
            }
            key.repaint();
        }
    }

    public void releaseNote(int midiNote) {
        JButton key = noteToKeyButton.get(midiNote);
        if (key != null) {
            String keyType = keyTypes[midiNote % 12];
            if ("w".equals(keyType)) {
                key.setBackground(Color.WHITE);
            } else {
                key.setBackground(Color.BLACK);
            }
            key.repaint();
        }
    }

    // NEW: Clear all key highlights (used when seeking/dragging in practice mode)
    public void releaseAllKeys() {
        for (Map.Entry<Integer, JButton> entry : noteToKeyButton.entrySet()) {
            int midiNote = entry.getKey();
            JButton key = entry.getValue();
            String keyType = keyTypes[midiNote % 12];
            if ("w".equals(keyType)) {
                key.setBackground(Color.WHITE);
            } else {
                key.setBackground(Color.BLACK);
            }
        }
        pianoPanel.repaint();
    }

    public void addSeekBar(JComponent seekBar) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 1;
        gbc.gridy = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;
        gbc.insets = new Insets(0, 5, 5, 5);
        controlPanel.add(seekBar, gbc);
        this.seekBar = (SeekBar) seekBar;
    }

    public void setPlayButtonListener(ActionListener listener) {
        playButton.addActionListener(listener);
    }

    public void setPlayButtonText(String text) {
        playButton.setText(text);
    }

    public AnimationPanel getAnimationPanel() {
        return animationPanel;
    }

    public boolean isBlackKey(int midiNote) {
        KeyInfo info = getKeyInfo(midiNote);
        return info != null && info.isBlack;
    }

    public record KeyInfo(boolean isBlack, int x, int width) {}

    public KeyInfo getKeyInfo(int midiNote) {
        JButton key = noteToKeyButton.get(midiNote);
        if (key == null) return null;

        boolean isBlack = "b".equals(keyTypes[midiNote % 12]);
        return new KeyInfo(isBlack, key.getX(), key.getWidth());
    }
}