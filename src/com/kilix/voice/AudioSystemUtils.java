package com.kilix.voice;

import javax.sound.sampled.*;
import java.util.*;
import java.util.stream.Collectors;

public interface AudioSystemUtils {

	public static List<Mixer.Info> getOutputMixerInfos() {
		return Arrays.stream(AudioSystem.getMixerInfo())
				.filter(it -> AudioSystem.getMixer(it).isLineSupported(new Line.Info(SourceDataLine.class)))
				.collect(Collectors.toList());
	}
	
	public static List<Mixer.Info> getInputMixerInfos() {
		return Arrays.stream(AudioSystem.getMixerInfo())
				.filter(it -> AudioSystem.getMixer(it).isLineSupported(new Line.Info(TargetDataLine.class)))
				.collect(Collectors.toList());
	}
	
	public static String[] getOutputMixerNames() {
		return getOutputMixerInfos().stream().map(Mixer.Info::getName).toArray(String[]::new);
	}
	
	public static String[] getInputMixerNames() {
		return getInputMixerInfos().stream().map(Mixer.Info::getName).toArray(String[]::new);
	}
	
	public static Optional<Mixer> getOutputMixerByName(String name) {
		assert name != null;
		return Arrays.stream(AudioSystem.getMixerInfo())
				.filter(it -> AudioSystem.getMixer(it).isLineSupported(new Line.Info(SourceDataLine.class)))
				.filter(it -> name.equals(it.getName()))
				.map(AudioSystem::getMixer)
				.findAny();
	}
	public static Optional<Mixer> getInputMixerByName(String name) {
		assert name != null;
		return Arrays.stream(AudioSystem.getMixerInfo())
				.filter(it -> AudioSystem.getMixer(it).isLineSupported(new Line.Info(TargetDataLine.class)))
				.filter(it -> name.equals(it.getName()))
				.map(AudioSystem::getMixer)
				.findAny();
	}
	
	
	
}
