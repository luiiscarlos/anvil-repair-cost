package com.luis.anvilrepaircost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnvilConfigTest {
	Path file;

	@BeforeEach
	void setUp(@TempDir Path dir) {
		file = dir.resolve("anvilrepaircost.json");
		AnvilConfig.useFile(file);
	}

	@Test
	void missingFileUsesDefault() {
		AnvilConfig.load();
		assertEquals(50, AnvilConfig.percent());
	}

	@Test
	void setPersistsAndReloads() throws IOException {
		AnvilConfig.set(25);
		assertEquals("{\"percent\":25}", Files.readString(file).trim());
		AnvilConfig.set(80);
		Files.writeString(file, "{\"percent\":25}");
		AnvilConfig.load();
		assertEquals(25, AnvilConfig.percent());
	}

	@Test
	void outOfRangeFileFallsBackToDefault() throws IOException {
		Files.writeString(file, "{\"percent\":150}");
		AnvilConfig.load();
		assertEquals(50, AnvilConfig.percent());
	}

	@Test
	void corruptFileFallsBackToDefault() throws IOException {
		Files.writeString(file, "basura");
		AnvilConfig.load();
		assertEquals(50, AnvilConfig.percent());
	}

	@Test
	void setRejectsOutOfRangeAndKeepsValue() throws IOException {
		AnvilConfig.set(40);
		assertThrows(IllegalArgumentException.class, () -> AnvilConfig.set(101));
		assertThrows(IllegalArgumentException.class, () -> AnvilConfig.set(-1));
		assertEquals(40, AnvilConfig.percent());
	}
}
