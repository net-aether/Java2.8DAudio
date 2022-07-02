package com.kilix.voice;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class DebugPositionPicker extends JFrame {
	
	Color bg = new Color(0x2f2f2f);
	Color guides = Color.yellow;
	Color pos = Color.red;
	
	final int border = 50;
	final Dimension preferredSize;
	final int radius, radius2;
	private int xPos, yPos;
	
	public DebugPositionPicker(Number radius, Number radius2) {
		super("pos");
		this.radius = radius.intValue();
		this.radius2 = radius2.intValue();
		preferredSize = new Dimension((this.radius + border) * 2, ((this.radius + border) * 2));
		
		getContentPane().add(new RelativePosition());
		pack();
		
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setResizable(false);
		setVisible(true);
	}
	
	public double getXValue() { return xPos - border - radius; }
	public double getYValue() { return - (yPos - border - radius); }
	
	class RelativePosition extends JPanel {
		
		
		RelativePosition() {
			addMouseMotionListener(new MouseMotionAdapter() {
				public void mouseDragged(MouseEvent e) {
					xPos = e.getX();
					yPos = e.getY();
					repaint();
				}
			});
			addMouseListener(new MouseAdapter() {
				@Override
				public void mouseClicked(MouseEvent e) {
					if (e.getButton() == 1) {
						xPos = e.getX();
						yPos = e.getY();
						repaint();
					}
					if (e.getButton() == 3) { resetPos(); repaint(); }
				}
			});
			resetPos();
		}
		
		void resetPos() {
			xPos = (int) (preferredSize.width * 0.5);
			yPos = (int) (preferredSize.height * 0.5);
		}
		
		@Override
		public void paint(Graphics g) {
			g.setColor(new Color(0x2f2f2f));
			g.fillRect(0, 0, getWidth(), getHeight());
			
			g.setColor(guides);
			g.drawOval(border, border, radius * 2, radius * 2);
			g.drawOval(border + radius - radius2, border + radius - radius2,  radius2 * 2, radius2 * 2);
			g.drawLine(border, border + radius, border + radius * 2, border + radius);
			g.drawLine(border + radius, border, border + radius, border + radius * 2);
			
			g.setColor(pos);
			g.fillOval(xPos - 3, yPos - 3, 6, 6);
			
			g.setColor(Color.white);
			g.drawString("x: " + String.format("%03d", (int) getXValue()), 5, 15);
			g.drawString("y: " + String.format("%03d", (int) getYValue()), 5, 25);
		}
		
		
		@Override
		public Dimension getPreferredSize() { return preferredSize; }
	}
}
