package pp.sheero.permapiola.utils;

import net.md_5.bungee.api.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ColorUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("#[a-fA-F0-9]{6}");

    public static String format(String message) {
        if (message == null) return "";

        message = message.replace("\\&", "{AMPERSAND}");

        Matcher matcher = HEX_PATTERN.matcher(message);
        while (matcher.find()) {
            String color = message.substring(matcher.start(), matcher.end());
            message = message.replace(color, ChatColor.of(color) + "");
            matcher = HEX_PATTERN.matcher(message);
        }

        message = ChatColor.translateAlternateColorCodes('&', message);

        message = message.replace("{AMPERSAND}", "&");

        return message;
    }

    public static String stripColors(String message) {
        if (message == null) return "";
        message = message.replaceAll("#[a-fA-F0-9]{6}", "");
        message = message.replaceAll("(?i)&[0-9a-fk-or]", "");
        return message;
    }
}