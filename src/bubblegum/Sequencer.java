package bubblegum;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.text.TextWindow;
import ij.util.Tools;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.io.CharArrayWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Properties;

import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import quicktime.QTSession;
import quicktime.io.QTFile;
import quicktime.qd.QDConstants;
import quicktime.qd.QDGraphics;
import quicktime.qd.QDRect;
import quicktime.std.StdQTException;
import quicktime.std.movies.media.UserData;
import quicktime.std.sg.SGVideoChannel;
import quicktime.std.sg.SequenceGrabber;


public class Sequencer extends JFrame implements Runnable {
	public static int ROWS = 10;

	public static int COLUMNS = 16;

	private boolean visualize = false;

	SequenceGrabber grabber;

	SGVideoChannel channel;

	QDRect cameraSize;

	QDGraphics gWorld;

	public int[] pixelData;

	ImagePlus original;

	ImagePlus processed;

	int intsPerRow;

	int width, height;

	boolean grabbing = true;

	int frame;

	boolean grabMode;

	// Distance in pixels between columns. 
	int gridSizeX = 20;

	// Distance in pixels between rows 
	int gridSizeY = 24;

	int radius = 2;

	int xOffset = 0;

	int yOffset = 1;

	int firstRow = 3;

	int lastRow = firstRow + 4;

	float brightnessThreshold = (float) .23;

	float dimnessThreshold = (float) .75;

	float saturationThreshold = (float) .25;

	// Upper bounds for: pink, yellow, green, purple, red (in that order).
	float[] colorBounds = new float[] { (float) .00, (float) .17, (float) .64,
			(float) .71, (float) .91 };

	String[] colorBoundsTitles = new String[] { "Pink (C)", "Yellow (D)",
			"Green (E)", "Purple (G)", "Red (H)" };

	JPanel[] colorSwatches = new JPanel[colorBounds.length];

	char[][] gridState = new char[COLUMNS][ROWS];

	JSlider gridSizeXSlider;

	JSlider gridSizeYSlider;

	JSlider radiusSlider;

	JSlider saturationThresholdSlider;

	JSlider brightnessThresholdSlider;

	JSlider dimnessThresholdSlider;

	JSlider[] colorBoundsSliders = new JSlider[colorBounds.length];

	JSlider xOffsetSlider;

	JSlider yOffsetSlider;

	JTextArea gridStateArea;

	JButton startButton;
	JButton cameraSettingsButton;

	JSpinner bpmSpinner = new JSpinner();

	boolean playing = false;

	LoopThread loop = new LoopThread(this);

	public static void main(String args[]) {
		Sequencer sequencer = new Sequencer();
		new Thread(sequencer).start();

	}

	public Sequencer() {
	}

	public void run() {
		// String options = Macro.getOptions();
		// if (options != null && options.indexOf("grab") != -1)
		// arg = "grab";
		// grabMode = arg.equals("grab");
		try {
			QTSession.open();
			initSequenceGrabber();
			width = cameraSize.getWidth();
			height = cameraSize.getHeight();
			intsPerRow = gWorld.getPixMap().getPixelData().getRowBytes() / 4;
			ImageProcessor ip = new ColorProcessor(width, height);
			original = new ImagePlus("Live (press space bar to stop)", ip);
			original.show();
			showProcessingWindow();

			initGui();

			pixelData = new int[intsPerRow * height];
			IJ.setKeyUp(KeyEvent.VK_ALT);
			IJ.setKeyUp(KeyEvent.VK_SPACE);
			if (IJ.debugMode) {
				IJ.log("Size: " + width + "x" + height);
				IJ.log("intsPerRow: " + intsPerRow);
			}
			preview();
		} catch (Exception e) {
			printStackTrace(e);
		} finally {
			QTSession.close();
		}
	}

	private void initGui() {
		setLayout(new FlowLayout());

		// Setup menu.
		JMenuBar mb = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenuItem openItem = new JMenuItem("Open settings file...", (char) 'O');
		openItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openSettingsFile();
			}
		});
		fileMenu.add(openItem);
		JMenuItem saveItem = new JMenuItem("Save settings file...", (char) 'S');
		saveItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveSettingsFile();
			}
		});
		fileMenu.add(saveItem);
		JMenuItem cameraSettingsItem = new JMenuItem("Camera settings...",
				(char) ',');
		cameraSettingsItem.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				openCameraSettings();
			}
		});
		fileMenu.add(cameraSettingsItem);
		mb.add(fileMenu);
		setJMenuBar(mb);

		// Add video screens.
		JPanel videoPanel = new JPanel();
		videoPanel.setLayout(new BoxLayout(videoPanel, BoxLayout.Y_AXIS));
		videoPanel.add(new JLabel("Original"));
		videoPanel.add(new JLabel(new ImageIcon(original.getImage())));
		videoPanel.add(new JLabel("Processed"));
		videoPanel.add(new JLabel(new ImageIcon(processed.getImage())));
		add(videoPanel);

		// Add settings sliders.
		JPanel settingsPanel = new JPanel();
		settingsPanel.setLayout(new BoxLayout(settingsPanel, BoxLayout.Y_AXIS));
		settingsPanel.setAlignmentY(TOP_ALIGNMENT);

		settingsPanel.add(new JLabel("Grid Size (X):"));
		gridSizeXSlider = new JSlider(10, 100, gridSizeX);
		gridSizeXSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				processed.getProcessor().setColor(Color.BLACK);
				processed.getProcessor().fill();
				gridSizeX = gridSizeXSlider.getValue();
				System.out.println("gridSizeX: " + gridSizeX);
				clearGrid();
			}
		});
		settingsPanel.add(gridSizeXSlider);

		settingsPanel.add(new JLabel("X-Offset:"));
		xOffsetSlider = new JSlider(-20, 30, xOffset);
		xOffsetSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				processed.getProcessor().setColor(Color.BLACK);
				processed.getProcessor().fill();
				xOffset = xOffsetSlider.getValue();
				System.out.println("xOffset: " + xOffset);
			}
		});
		settingsPanel.add(xOffsetSlider);

		settingsPanel.add(new JLabel("Grid Size (Y):"));
		gridSizeYSlider = new JSlider(10, 100, gridSizeY);
		gridSizeYSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				processed.getProcessor().setColor(Color.BLACK);
				processed.getProcessor().fill();
				gridSizeY = gridSizeYSlider.getValue();
				System.out.println("gridSizeY: " + gridSizeY);
				clearGrid();
			}
		});
		settingsPanel.add(gridSizeYSlider);

		settingsPanel.add(new JLabel("Y-Offset:"));
		yOffsetSlider = new JSlider(-20, 30, yOffset);
		yOffsetSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				processed.getProcessor().setColor(Color.BLACK);
				processed.getProcessor().fill();
				yOffset = yOffsetSlider.getValue();
				System.out.println("yOffset: " + yOffset);
			}
		});
		settingsPanel.add(yOffsetSlider);

		settingsPanel.add(new JLabel("Saturation threshold:"));
		saturationThresholdSlider = new JSlider(0, 100,
				(int) (saturationThreshold * 100));
		saturationThresholdSlider.setValue((int) (saturationThreshold * 100));
		saturationThresholdSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				saturationThreshold = (float) saturationThresholdSlider
						.getValue() / 100;
				System.out.println("saturation threshold: "
						+ saturationThreshold);
			}
		});
		settingsPanel.add(saturationThresholdSlider);

		settingsPanel.add(new JLabel("Brightness threshold:"));
		brightnessThresholdSlider = new JSlider(0, 100,
				(int) (brightnessThreshold * 100));
		brightnessThresholdSlider.setValue((int) (brightnessThreshold * 100));
		brightnessThresholdSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				brightnessThreshold = (float) brightnessThresholdSlider
						.getValue() / 100;
				System.out.println("brightness threshold: "
						+ brightnessThreshold);
			}
		});
		settingsPanel.add(brightnessThresholdSlider);

		settingsPanel.add(new JLabel("Dimness threshold:"));
		dimnessThresholdSlider = new JSlider(0, 100,
				(int) (dimnessThreshold * 100));
		dimnessThresholdSlider.setValue((int) (dimnessThreshold * 100));
		dimnessThresholdSlider.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				dimnessThreshold = (float) dimnessThresholdSlider.getValue() / 100;
				System.out.println("dimness threshold: " + dimnessThreshold);
			}
		});
		settingsPanel.add(dimnessThresholdSlider);

		// Add color bounds sliders.
		for (int i = 0; i < colorBounds.length; i++) {
			colorBoundsSliders[i] = new JSlider(0, 100,
					(int) colorBounds[i] * 100);
			colorBoundsSliders[i].setName("" + i);
			colorBoundsSliders[i].setValue((int) (colorBounds[i] * 100));
			colorBoundsSliders[i].addChangeListener(new ChangeListener() {
				public void stateChanged(ChangeEvent e) {
					JSlider source = (JSlider) e.getSource();
					int index = Integer.parseInt(source.getName());
					System.out.println("slider value: " + source.getValue());
					colorBounds[index] = ((float) source.getValue()) / 100;
					colorSwatches[index].setBackground(new Color(Color
							.HSBtoRGB(colorBounds[index], 1, 1)));
					System.out.println("Upper bounds "
							+ colorBoundsTitles[index] + ": "
							+ colorBounds[index]);
				}
			});
			settingsPanel.add(new JLabel("Upper bounds for "
					+ colorBoundsTitles[i]));
			colorSwatches[i] = new JPanel();
			colorSwatches[i].setBackground(new Color(Color.HSBtoRGB(
					colorBounds[i], 1, 1)));
			colorSwatches[i].setSize(10, 10);
			settingsPanel.add(colorSwatches[i]);
			settingsPanel.add(colorBoundsSliders[i]);
		}

		settingsPanel.add(new JLabel("Tempo (bpm):"));
		bpmSpinner.setValue(120);
		bpmSpinner.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent e) {
				loop.setTempo(Integer
						.parseInt(bpmSpinner.getValue().toString()));
				// System.out.println("Tempo: " + bpmSpinner.getValue() + bpm);
			}
		});
		settingsPanel.add(bpmSpinner);

		startButton = new JButton("Start");
		startButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!playing) {
					new Thread(loop).start();
					playing = true;
					startButton.setText("Stop");
				} else {
					loop.stopPlayback();
					playing = false;
					startButton.setText("Start");
				}
			}
		});
		settingsPanel.add(startButton);

		// gridStateArea = new JTextArea();
		// gridStateArea.setSize(200, 100);
		// settingsPanel.add(gridStateArea);

		add(settingsPanel);

		setSize(800, 600);
		pack();
		setVisible(true);
	}

	public void setTempo(int bpm) {
		this.bpmSpinner.setValue(bpm);
		System.out.println("Tempo: " + bpmSpinner.getValue());
	}

	/**
	 * Initializes the SequenceGrabber. Gets it's source video bounds, creates a
	 * gWorld with that size. Configures the video channel for grabbing,
	 * previewing and playing during recording.
	 */
	protected void initSequenceGrabber() throws Exception {
		grabber = new SequenceGrabber();
		this.channel = new SGVideoChannel(grabber);
		// channel.setDevice("Logitech QuickCam Vision Pro");
		cameraSize = channel.getSrcVideoBounds();
		// // added by Jeff Hardin to account for change in byte order on Intel
		// // Macs
		if (quicktime.util.EndianOrder.isNativeLittleEndian())
			gWorld = new QDGraphics(QDConstants.k32BGRAPixelFormat, cameraSize);
		else
			gWorld = new QDGraphics(QDGraphics.kDefaultPixelFormat, cameraSize);
		Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
		if (cameraSize.getHeight() > screen.height - 40) // iSight camera
			// claims to
			// 1600x1200!
			cameraSize.resize(640, 480);
		gWorld = new QDGraphics(cameraSize);
		grabber.setGWorld(gWorld, null);
		channel.setBounds(cameraSize);
		channel.setUsage(quicktime.std.StdQTConstants.seqGrabRecord
				| quicktime.std.StdQTConstants.seqGrabPreview
				| quicktime.std.StdQTConstants.seqGrabPlayDuringRecord);
		// channel.setFrameRate(0);
		channel.setFrameRate((float) 4);
		// startLoopThread();
		System.out.println("Frame rate: " + channel.getFrameRate());
		channel
				.setCompressorType(quicktime.std.StdQTConstants.kComponentVideoCodecType);
		UserData d = channel.getSettings();
		System.out.println("Settings: " + channel.getSettings());
	}

	// private void initSequenceGrabber2() throws Exception {
	// grabber = new SequenceGrabber();
	// // added by Jeff Hardin to account for change in byte order on Intel
	// // Macs
	// if (quicktime.util.EndianOrder.isNativeLittleEndian())
	// gWorld = new QDGraphics(QDConstants.k32BGRAPixelFormat, cameraSize);
	// else
	// gWorld = new QDGraphics(QDGraphics.kDefaultPixelFormat, cameraSize);
	// SGVideoChannel channel = new SGVideoChannel(grabber);
	// cameraSize = channel.getSrcVideoBounds();
	// Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
	// if (cameraSize.getHeight() > screen.height - 40) // iSight camera
	// // claims to
	// // 1600x1200!
	// cameraSize.resize(640, 480);
	// gWorld = new QDGraphics(cameraSize);
	// grabber.setGWorld(gWorld, null);
	// channel.setBounds(cameraSize);
	// channel.setUsage(quicktime.std.StdQTConstants.seqGrabPreview);
	// // channel.setFrameRate(0);
	// // channel.setCompressorType(
	// // quicktime.std.StdQTConstants.kComponentVideoCodecType);
	// }

	/**
	 * This is a bit tricky. We do not start Previewing, but recording. By
	 * setting the output to a dummy file (which will never be created (hope
	 * so)) with the quicktime.std.StdQTConstants.seqGrabDontMakeMovie flag set.
	 * This seems to be equivalent to preview mode with the advantage, that it
	 * refreshes correctly.
	 */
	protected void preview() throws Exception {
		QTFile movieFile = new QTFile(new java.io.File("NoFile"));
		grabber.setDataOutput(null,
				quicktime.std.StdQTConstants.seqGrabDontMakeMovie);
		grabber.prepare(true, true);
		grabber.startRecord();
		while (grabbing) {
			grabber.idle();
			grabber.update(null);
			displayFrame();
		}
	}

	protected void displayFrame() {
		gWorld.getPixMap().getPixelData().copyToArray(0, pixelData, 0,
				pixelData.length);
		ImageProcessor ip = original.getProcessor();
		int[] pixels = ip != null ? (int[]) ip.getPixels() : null;
		ImageWindow win = original.getWindow();
		if (pixels == null || win == null || IJ.spaceBarDown()) {
			grabbing = false;
			original.setTitle("Untitled");
			return;
		}
		if (IJ.altKeyDown()) {
			IJ.setKeyUp(KeyEvent.VK_ALT);
			IJ.run("Add Slice");
		}
		if (intsPerRow != width) {
			for (int i = 0; i < height; i++)
				System.arraycopy(pixelData, i * intsPerRow, pixels, i * width,
						width);
		} else
			ip.setPixels(pixelData);
		// ip.flipHorizontal();
		ip.flipVertical();
		averageGrid();
		drawGrid();
		original.updateAndDraw();
		if (grabMode && pixelData[0] != 0) {
			grabbing = false;
			original.setTitle("Untitled");
			return;
		}
		frame++;
		// updateProcessingWindow();
		IJ.wait(10);
	}

	protected void printStackTrace(Exception e) {
		String msg = e.getMessage();
		if (msg != null && msg.indexOf("-9405") >= 0)
			IJ.error("QT Capture", "QuickTime compatible camera not found");
		else {
			CharArrayWriter caw = new CharArrayWriter();
			PrintWriter pw = new PrintWriter(caw);
			e.printStackTrace(pw);
			String s = caw.toString();
			if (IJ.isMacintosh())
				s = Tools.fixNewLines(s);
			new TextWindow("Exception", s, 500, 300);
		}
	}

	private void showProcessingWindow() {
		ImageProcessor ip = new ColorProcessor(width, height);
		processed = new ImagePlus("Processed", ip);
		drawGrid();
		processed.show();
		processed.updateAndDraw();
	}

	private void updateProcessingWindow() {
		processed.getProcessor().insert(original.getProcessor(), 0, 0);
		// imp.getProcessor().copyBits(processed.getProcessor(), 0, 0,
		// Blitter.COPY);
		// drawGrid();
		// processed.show();
		processed.updateAndDraw();
	}

	private void drawGrid() {
		ImageProcessor ip = original.getProcessor();

		ip.setColor(Color.RED);

		// Draw horizontal lines.
		for (int y = 0; y < ip.getHeight(); y++) {
			if (y % gridSizeY == (gridSizeY / 2) + yOffset) {
				ip.drawLine(0, y, ip.getWidth(), y);
			}
		}

		// Draw vertical lines.
		for (int x = 0; x < ip.getWidth(); x++) {
			if (x % gridSizeX == (gridSizeX / 2) + xOffset) {
				ip.drawLine(x, 0, x, ip.getHeight());
			}
		}

		// Draw center dots.
		// for (int x = 0; x < ip.getWidth(); x++) {
		// for (int y = 0; y < ip.getHeight(); y++) {
		// if (x % gridSizeX == (gridSizeX / 2) + xOffset
		// && y % gridSizeY == gridSizeY / 2 + yOffset) {
		// ip.drawDot(x, y);
		// ip.drawLine(x, 0, x, ip.getHeight());
		// ip.drawLine(0, y, ip.getWidth(), y);
		// }
		// }
		// }
	}

	private void averageGrid() {

		int fieldSize = radius * 2 + 1;
		ImageProcessor ip = processed.getProcessor();
		if (ip == null)
			return;
		for (int yCent = (gridSizeY / 2) + yOffset; yCent < ip.getHeight(); yCent += gridSizeY) {
			for (int xCent = (gridSizeX / 2) + xOffset; xCent < ip.getWidth(); xCent += gridSizeX) {
				ip.putPixel(xCent, yCent, 255);
				int column = xCent / gridSizeX;
				int row = yCent / gridSizeY;

				Rectangle r = new Rectangle(xCent - radius, yCent - radius,
						fieldSize, fieldSize);
				float[] avgHSB = { 0, 0, 0 };
				// Averaging in the RGB space is more stable.
				int[] avgRGB = { 0, 0, 0 };

				// Loop over ROI pixels to compute average
				int pixelsCounted = 0;
				for (int y = r.y; y < (r.y + r.height); y++) {
					for (int x = r.x; x < (r.x + r.width); x++) {
						int[] color = original.getPixel(x, y);
						avgRGB[0] += color[0];
						avgRGB[1] += color[1];
						avgRGB[2] += color[2];
						pixelsCounted++;
					}
				}
				// Compute average.
				avgRGB[0] /= pixelsCounted;
				avgRGB[1] /= pixelsCounted;
				avgRGB[2] /= pixelsCounted;
				avgHSB = Color.RGBtoHSB(avgRGB[0], avgRGB[1], avgRGB[2], null);

				// Create color for processed image. Black by default.
				Color c = Color.black;

				// if average brightness and saturation thresholds are met,
				// paint with average hue.
				if (avgHSB[2] >= brightnessThreshold
						&& avgHSB[2] <= dimnessThreshold
						&& avgHSB[1] >= saturationThreshold) {

					c = new Color(Color.HSBtoRGB(avgHSB[0], (float) 1.0,
							(float) 1.0));
				}

				for (int y = r.y; y < (r.y + r.height); y++) {
					for (int x = r.x; x < (r.x + r.width); x++) {
						ip.putPixel(x, y, new int[] { c.getRed(), c.getGreen(),
								c.getBlue() });
					}
				}

				// Write measured hue to grid array.
				try {
					if (c != Color.BLACK) {
						gridState[column][row] = quantize(avgHSB[0]);
					} else {
						gridState[column][row] = '0';
					}
				} catch (ArrayIndexOutOfBoundsException e) {
					// System.out.println("Out of bounds: " + row + ", "
					// + column);
				}
				// updateGridStateArea();
			}
		}
		processed.updateAndDraw();
	}

	private void updateGridStateArea() {
		String text = "";
		for (int j = 0; j < gridState[0].length; j++) {
			for (int i = 0; i < gridState.length; i++) {
				text += gridState[i][j] + ",";
			}
			text += "\n";
		}
		gridStateArea.setText(text);
	}

	private void clearGrid() {
		for (int j = 0; j < gridState[0].length; j++) {
			for (int i = 0; i < gridState.length; i++) {
				gridState[i][j] = '0';
			}
		}
	}

	private char quantize(float hue) {
		if (hue <= colorBounds[0])
			return 'P';
		if (hue <= colorBounds[1])
			return 'Y';
		if (hue <= colorBounds[2])
			return 'G';
		if (hue <= colorBounds[3])
			return 'B';
		if (hue <= colorBounds[4])
			return 'R';
		return 'P';
	}

	/**
	 * Returns note values of all samples at column i.
	 * 
	 * @param col
	 * @return
	 */
	public int[] getNotesAt(int col) {
		// int[] result = new int[gridState[col].length];
		int[] result = new int[lastRow - firstRow];
		for (int i = 0; i < result.length; i++) {
			result[i] = convertToNote(gridState[col][firstRow + i]);
		}
		return result;
	}

	// public int[] getNotesAtMirrored(int col) {
	// return getNotesAt(gridState.length - col);
	// }

	private int convertToNote(char ch) {
		switch (ch) {
		// case 'P':
		// return 36; // BD
		// case 'Y':
		// return 39; // clap
		// case 'G':
		// return 38; // snare
		// case 'B':
		// return 44; // hh
		// case 'R':
		// return 46; // hhopen
		case 'P':
			return 60; // BD
		case 'Y':
			return 62; // clap
		case 'G':
			return 64; // snare
		case 'B':
			return 65; // hh
		case 'R':
			return 67; // hhopen
		}
		return 0;
	}

	/**
	 * @return the visualize
	 */
	public boolean isVisualize() {
		return visualize;
	}

	/**
	 * @param visualize
	 *            the visualize to set
	 */
	public void setVisualize(boolean visualize) {
		this.visualize = visualize;
	}

	private void openSettingsFile() {
		JFileChooser fc = new JFileChooser();
		fc.setDialogType(JFileChooser.OPEN_DIALOG);
		fc.showOpenDialog(null);
		File selFile = fc.getSelectedFile();
		try {
			Properties props = new Properties();
			props.load(new FileInputStream(selFile));
			// fin.close();
			System.out.println("gridSizeX: " + props.getProperty("gridSizeX"));
			try {
				gridSizeXSlider.setValue(Integer.parseInt(props
						.getProperty("gridSizeX")));
				gridSizeYSlider.setValue(Integer.parseInt(props
						.getProperty("gridSizeY")));
				xOffsetSlider.setValue(Integer.parseInt(props
						.getProperty("xOffset")));
				yOffsetSlider.setValue(Integer.parseInt(props
						.getProperty("yOffset")));
				brightnessThresholdSlider
						.setValue((int) (Float.parseFloat(props
								.getProperty("brightnessThreshold")) * 100));
				saturationThresholdSlider
						.setValue((int) (Float.parseFloat(props
								.getProperty("saturationThreshold")) * 100));
				dimnessThresholdSlider.setValue((int) (Float.parseFloat(props
						.getProperty("dimnessThreshold")) * 100));
				colorBoundsSliders[0].setValue((int) (Float.parseFloat(props
						.getProperty("endPink")) * 100));
				colorBoundsSliders[1].setValue((int) (Float.parseFloat(props
						.getProperty("endYellow")) * 100));
				colorBoundsSliders[2].setValue((int) (Float.parseFloat(props
						.getProperty("endGreen")) * 100));
				colorBoundsSliders[3].setValue((int) (Float.parseFloat(props
						.getProperty("endPurple")) * 100));
				colorBoundsSliders[4].setValue((int) (Float.parseFloat(props
						.getProperty("endRed")) * 100));
			} catch (NumberFormatException e) {
				e.printStackTrace();
			}

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	private void saveSettingsFile() {
		JFileChooser fc = new JFileChooser();
		fc.setDialogType(JFileChooser.SAVE_DIALOG);
		fc.showOpenDialog(null);
		File selFile = fc.getSelectedFile();
		try {
			Properties props = new Properties();
			props.setProperty("gridSizeX", gridSizeX + "");
			props.setProperty("gridSizeY", gridSizeY + "");
			props.setProperty("xOffset", xOffset + "");
			props.setProperty("yOffset", yOffset + "");
			props.setProperty("brightnessThreshold", brightnessThreshold + "");
			props.setProperty("saturationThreshold", saturationThreshold + "");
			props.setProperty("dimnessThreshold", dimnessThreshold + "");
			props.setProperty("endPink", colorBounds[0] + "");
			props.setProperty("endYellow", colorBounds[1] + "");
			props.setProperty("endGreen", colorBounds[2] + "");
			props.setProperty("endPurple", colorBounds[3] + "");
			props.setProperty("endRed", colorBounds[4] + "");
			props.store(new FileOutputStream(selFile), null);
			System.out.println("Settings saved.");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	private void openCameraSettings() {
		try {
			grabber.stop();
			channel.settingsDialog();
			grabber.startRecord();
		} catch (StdQTException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
