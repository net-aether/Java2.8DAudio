package com.kilix.voice;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.*;
import java.util.Properties;

public class Client {
	private static final double PI_2 = Math.PI / 2;
	private static final double i180 = 1.0 / 180;
	private static final double FRAC_BIAS = Double.longBitsToDouble(4805340802404319232L);
	private static final double[] ASIN_TAB = new double[257];
	private static final double[] COS_TAB = new double[257];
	
	static {
		for (int i = 0; i < 257; i++) {
			double asin = Math.asin(i / 256D);
			COS_TAB[i] = Math.cos(asin);
			ASIN_TAB[i] = asin;
		}
	}
	
	private static final int SAMPLE_SIZE = 16;
	private static final float SAMPLE_RATE = 44_100f;
	private static final AudioFormat INPUT_FORMAT = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,
			SAMPLE_RATE,
			SAMPLE_SIZE,
			1,
			SAMPLE_SIZE / 8,
			SAMPLE_RATE,
			true
	);
	
	private static final AudioFormat OUTPUT_FORMAT = new AudioFormat(
			AudioFormat.Encoding.PCM_SIGNED,
			SAMPLE_RATE,
			SAMPLE_SIZE,
			2,
			SAMPLE_SIZE / 4,
			SAMPLE_RATE,
			true
	);
	
	private static final Properties PROPERTIES = new Properties();
	private static final File CONFIG_FILE = new File("./VoiceAgent.properties");
	private static final String INPUT_DEVICE = "audio.device.input";
	private static final String OUTPUT_DEVICE = "audio.device.output";
	
	public static void main(String[] args) throws Exception {
		try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
		catch (Exception e) {}
		
		loadProperties();
		
		PROPERTIES.computeIfAbsent(OUTPUT_DEVICE, key -> {
			String[] names = AudioSystemUtils.getOutputMixerNames();
			String name = pickDevice(names, "output", "no device configured");
			return name.trim().isEmpty() ? names[0] : name;
		});
		PROPERTIES.computeIfAbsent(INPUT_DEVICE, key -> {
			String[] names = AudioSystemUtils.getInputMixerNames();
			String name = pickDevice(names, "input", "no device configured");
			return name.trim().isEmpty() ? names[0] : name;
		});
		saveProperties();
		
		Mixer outputMixer = AudioSystemUtils.getOutputMixerByName(PROPERTIES.getProperty(OUTPUT_DEVICE)).get();
		Mixer inputMixer  = AudioSystemUtils.getInputMixerByName(PROPERTIES.getProperty(INPUT_DEVICE)).get();
		
		while (outputMixer == null || ! outputMixer.isLineSupported(new DataLine.Info(SourceDataLine.class, OUTPUT_FORMAT))) {
			String name = pickDevice(AudioSystemUtils.getOutputMixerNames(),
					"output",
					"the configured device is not supported");
			outputMixer = AudioSystemUtils.getOutputMixerByName(name).orElse(null);
		}
		while (inputMixer == null || ! inputMixer.isLineSupported(new DataLine.Info(TargetDataLine.class, OUTPUT_FORMAT))) {
			String name = pickDevice(AudioSystemUtils.getInputMixerNames(),
					"input",
					"the configured device is not supported");
			inputMixer = AudioSystemUtils.getOutputMixerByName(name).orElse(null);
		}
		
		SourceDataLine outputLine = AudioSystem.getSourceDataLine(OUTPUT_FORMAT);
		TargetDataLine inputLine = AudioSystem.getTargetDataLine(INPUT_FORMAT);
		
		
		outputLine.open(OUTPUT_FORMAT);
		outputLine.start();
		inputLine.open(INPUT_FORMAT);
		inputLine.start();
		
		int maxSampleCount = 1024;
		int inSize = 2 * maxSampleCount; // sample = 2bytes
		int outSize = 4 * maxSampleCount;
		byte[] inputBuffer = new byte[inSize];
		byte[] outputBuffer = new byte[outSize];
		
		// inputs
		double xd = 17;  // delta coords in minecraft space
		double yd = 0.0;  // delta coords in minecraft space
		double zd = 0.5; // delta coords in minecraft space
		double yRot = 90; // -180..180 (like vanilla)
		double maxRadius = 150;
		double maxVolRadius = 20;
		DebugPositionPicker debugInput = new DebugPositionPicker(maxRadius, maxVolRadius);
		
		while (true) {
			xd = debugInput.getXValue();
			zd = debugInput.getYValue();
			
			// math
			DO_NOT_TOUCH: {
				double balanceL, balanceR;
				if (xd * xd <= 1 && yd * yd <= 1 && zd * zd <= 1)
					balanceL = balanceR = 1;
				else {
					double yRotr = (yRot + 360) % 360;
					yRotr *= i180 * Math.PI; // yourself
					
					double theta = fatan2(xd, zd); // the other one
					double thetaRel = yRotr - theta; // 0..2pi // the other one relative to you
					
					// to flip L and R channel, swap +/- in the following two lines (in case of logic error here)
					double thetaRelL = (thetaRel - PI_2) % (2 * Math.PI); // won't get below -pi here -> all good
					double thetaRelR = (thetaRel + PI_2) % (2 * Math.PI); // won't get above 3pi here -> all good
					
					if (thetaRelL > Math.PI)
						thetaRelL -= 2 * Math.PI; // -pi..pi
					if (thetaRelR > Math.PI)
						thetaRelR -= 2 * Math.PI; // -pi..pi
					if (thetaRelL < 0) thetaRelL = -thetaRelL;
					if (thetaRelR < 0) thetaRelR = -thetaRelR;
					
					double r = Math.min(radius(xd, yd, zd), maxRadius); // do range check with this
					double distanceFac = r < maxVolRadius ? 1 : (maxRadius - r) / (maxRadius - maxVolRadius);
					double fac = norm(thetaRelL, thetaRelR) * distanceFac;
					balanceL = thetaRelL * fac;
					balanceR = thetaRelR * fac;
				}
				
				inputLine.read(inputBuffer, 0, inSize);
				
				for (int i = 0; i < inSize; i += 2) {
					int temp = ((inputBuffer[i] << 8) & ~0xFF | inputBuffer[i + 1] & 0xFF);
					
					outputBuffer[(i << 1)    ] = (byte) (((int) (temp * balanceL) >>> 8) & 0xFF);
					outputBuffer[(i << 1) + 1] = (byte) (((int) (temp * balanceL)      ) & 0xFF);
					outputBuffer[(i << 1) + 2] = (byte) (((int) (temp * balanceR) >>> 8) & 0xFF);
					outputBuffer[(i << 1) + 3] = (byte) (((int) (temp * balanceR)      ) & 0xFF);
				}
				
				outputLine.write(outputBuffer, 0, 2 * inSize);
			}
		} // DO_NOT_TOUCH END
	}
	
	
	static double clamp(double v, double m, double x) {
		return v < m ? m : v > x ? x : v;
	}
	
	static double pow(double d) {
		return d * d;
	}
	
	static double norm(double d0, double d1) {
		return frec(Math.max(d0, d1));
	}
	
	static double radius(double x, double y, double z) {
		return fsqrt(x*x + y*y + z*z);
	}
	
	static double radius(double x, double y) {
		return fsqrt(x*x + y*y);
	}
	
	static double fatan2(double x, double y) {
		double sqrsum = y * y + x * x;
		
		if (Double.isNaN(sqrsum))
			return Double.NaN;
		
		boolean x0 = x < 0;
		boolean y0 = y < 0;
		boolean xy = x < y;
		
		if (x0) x = -x;
		if (y0) y = -y;
		
		if (xy) {
			double tmp = x;
			x = y;
			y = tmp;
		}
		
		double norm = fisqrt(sqrsum);
		x *= norm;
		y *= norm;
		
		int i = (int) Double.doubleToRawLongBits(FRAC_BIAS + y);
		double asin = ASIN_TAB[i];
		double cos = COS_TAB[i];
		double d = y * cos - x * y;
		double res = asin + (6.0 + d * d) * d * 0.16666666666666666;
		
		if (xy) res = (Math.PI * 0.5) - res;
		if (x0) res = Math.PI - res;
		if (y0) res = -res;
		
		return res;
	}
	
	static double fsqrt(double d) { // magic version of sqrt(d) // about .1% error
		return frec(fisqrt(d));
	}
	
	static double frec(double d) { // magic version of 1 / d // about 2% error
		d = Double.longBitsToDouble((0xbfcdd6a18f6a6f52L - Double.doubleToLongBits(d)) >>> 1); // const is 0xbe6eb3be for float
		return d * d;
	}
	
	static double fisqrt(double d) { // magic version of 1 / sqrt(d)
		double half = d * 0.5;
		d = Double.longBitsToDouble(0x5fe6eb50c7b537a9L - (Double.doubleToLongBits(d) >>> 1)); // const is 0x5f3759df for float
		return d * (1.5F - half * d * d);
	}
	
	static void ensurePropertiesExist() throws IOException {
		if (! CONFIG_FILE.isFile()) { // not a file
			if (! CONFIG_FILE.exists()) { // does not exist
				CONFIG_FILE.createNewFile();
			} else throw new RuntimeException("Unable to write to config file '" + CONFIG_FILE.getAbsolutePath() + "'. Not a File.");
		}
	}
	static void loadProperties() throws IOException {
		ensurePropertiesExist();
		PROPERTIES.load(new FileInputStream(CONFIG_FILE));
	}
	static void saveProperties() throws IOException {
		ensurePropertiesExist();
		PROPERTIES.store(new FileOutputStream(CONFIG_FILE), "Stores some configurations for the VoiceAgent \"mod\".");
	}
	
	static String pickDevice(String[] options, String type, String reason) {
		return (String) JOptionPane.showInputDialog(
				null, reason != null ? reason : ("select " + type + " device"),
				"select " + type + " device",JOptionPane.PLAIN_MESSAGE, null,
				options, options[0]
		);
	}
}
