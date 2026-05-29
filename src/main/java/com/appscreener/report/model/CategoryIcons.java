package com.appscreener.report.model;

import java.util.List;

/**
 * Иконки для выбора при создании категории (встроенные + дополнительный набор).
 */
public final class CategoryIcons {

  private CategoryIcons() {
  }

  /** Все доступные в выпадающем списке админки. */
  public static final List<String> PICKER_OPTIONS = List.of(
      "✅", "📊", "🖥️", "⚙️", "🔥", "#", "🗒️", "🤖", "📦", "🛠️",
      "🧪", "🚀", "🔒", "📱", "🌐", "⚡", "🎯", "📋", "🔧", "🐛",
      "💡", "🔔", "📈", "🛡️", "⭐", "🏷️", "🧩", "🔬", "📝", "🔄",
      "💾", "🎨", "🏗️", "📡", "🧬", "🗄️", "⏱️", "🔍"
  );

  public static boolean isAllowed(String icon) {
    return icon != null && !icon.isBlank() && PICKER_OPTIONS.contains(icon.trim());
  }
}
