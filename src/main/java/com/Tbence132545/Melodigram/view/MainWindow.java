package com.Tbence132545.Melodigram.view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class MainWindow extends JFrame {
    private JButton playButton = createModernButton("Play");
    private JButton settingsButton = createModernButton("Settings");
    private JButton quitButton = createModernButton("Quit");

    public MainWindow() {
        setTitle("Melodigram");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(600, 600));

        // Main layout uses BorderLayout for better responsiveness
        setLayout(new BorderLayout());

        // Main content panel with GridBagLayout for responsive positioning
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(Color.BLACK);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Center panel for main elements
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);

        // Add logo
        try {
            BufferedImage logoImage = ImageIO.read(getClass().getResourceAsStream("/images/logofinal.jpg"));
            int newWidth = logoImage.getWidth() / 2;
            int newHeight = logoImage.getHeight() / 2;
            Image resizedImage = logoImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);

            JLabel logoLabel = new JLabel(new ImageIcon(resizedImage));
            logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            centerPanel.add(logoLabel);
            centerPanel.add(Box.createVerticalStrut(20));
        } catch (IOException e) {
            System.err.println("Could not load logo image: " + e.getMessage());
        }

        // Add title and buttons
        JLabel titleLabel = new JLabel("Melodigram");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 36));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setForeground(Color.WHITE);

        centerPanel.add(titleLabel);
        centerPanel.add(Box.createVerticalStrut(40));
        centerPanel.add(playButton);
        centerPanel.add(Box.createVerticalStrut(20));
        centerPanel.add(quitButton);

        // Add the center panel to the content panel with constraints for better responsiveness
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.VERTICAL;
        contentPanel.add(centerPanel, gbc);

        // Add content panel to frame
        add(contentPanel, BorderLayout.CENTER);

        setVisible(true);
    }

    private JButton createModernButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Base color
                Color baseColor = new Color(200, 60, 60); // Slight red
                Color hoverColor = new Color(170, 40, 40); // Darker red for hover
                Color currentColor = getModel().isRollover() ? hoverColor : baseColor;

                // Rounded background
                g2.setColor(currentColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

                // Text
                g2.setColor(Color.WHITE);
                FontMetrics fm = g2.getFontMetrics();
                int textX = (getWidth() - fm.stringWidth(getText())) / 2;
                int textY = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                g2.drawString(getText(), textX, textY);

                g2.dispose();
            }
        };

        button.setFont(new Font("Segoe UI", Font.BOLD, 20));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setMaximumSize(new Dimension(250, 60));
        button.setPreferredSize(new Dimension(250, 60));

        return button;
    }

    public void addPlayButtonListener(ActionListener listener) {
        playButton.addActionListener(listener);
    }

    public void addSettingsButtonListener(ActionListener listener) {
        settingsButton.addActionListener(listener);
    }

    public void addQuitButtonListener(ActionListener listener) {
        quitButton.addActionListener(listener);
    }

    // === MAIN ===
    public static void main(String[] args) {
        SwingUtilities.invokeLater(MainWindow::new);
    }
}