package com.harcore.duo.feature;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public class GradientText {

	public static Component of(String text) {
		return of(text, 0xFF00FFFF, 0xFF00FFAA);
	}

	public static Component of(String text, int fromRgb, int toRgb) {
		if (text == null || text.isEmpty()) {
			return Component.empty();
		}

		var result = Component.empty();
		int len = text.length();

		for (int i = 0; i < len; i++) {
			float t = len == 1 ? 0f : (float) i / (len - 1);
			int r = lerp((fromRgb >> 16) & 0xFF, (toRgb >> 16) & 0xFF, t);
			int g = lerp((fromRgb >> 8) & 0xFF, (toRgb >> 8) & 0xFF, t);
			int b = lerp(fromRgb & 0xFF, toRgb & 0xFF, t);
			int color = (r << 16) | (g << 8) | b;

			result.append(Component.literal(String.valueOf(text.charAt(i)))
					.withStyle(Style.EMPTY.withColor(TextColor.fromRgb(color))));
		}
		return result;
	}

	private static int lerp(int a, int b, float t) {
		return Math.round(a + (b - a) * t);
	}
}
