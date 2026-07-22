package pp.sheero.permapiola.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TimeUtils {

    public static String formatTime(long seconds, String wLab, String dLab, String hLab, String mLab, String sLab) {
        long w = seconds / 604800;
        long d = (seconds % 604800) / 86400;
        long h = (seconds % 86400) / 3600;
        long m = (seconds % 3600) / 60;
        long s = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (w > 0) sb.append(w).append(wLab).append(" ");
        if (d > 0) sb.append(d).append(dLab).append(" ");
        if (h > 0) sb.append(h).append(hLab).append(" ");
        if (m > 0) sb.append(m).append(mLab).append(" ");
        sb.append(s).append(sLab);
        return sb.toString().trim();
    }

    public static long parseTimeString(String input) {
        long totalSeconds = 0;
        Pattern p = Pattern.compile("(\\d+)([wdhms])");
        Matcher m = p.matcher(input.toLowerCase());
        while (m.find()) {
            long val = Long.parseLong(m.group(1));
            switch (m.group(2)) {
                case "w": totalSeconds += val * 604800; break;
                case "d": totalSeconds += val * 86400; break;
                case "h": totalSeconds += val * 3600; break;
                case "m": totalSeconds += val * 60; break;
                case "s": totalSeconds += val; break;
            }
        }
        return totalSeconds;
    }
}