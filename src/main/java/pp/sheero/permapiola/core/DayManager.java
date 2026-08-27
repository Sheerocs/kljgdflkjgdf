package pp.sheero.permapiola.core;

import pp.sheero.permapiola.PermaPiola;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

public class DayManager {

    private final PermaPiola plugin;
    private final ZoneId argentinaZone = ZoneId.of("America/Argentina/Buenos_Aires");

    public DayManager(PermaPiola plugin) {
        this.plugin = plugin;
    }

    public int getCurrentDay() {
        String startDateStr = plugin.getConfig().getString("system.start-date", "");

        if (startDateStr == null || startDateStr.isEmpty()) {
            return 1;
        }

        try {
            LocalDateTime localStart = LocalDateTime.parse(startDateStr);
            ZonedDateTime startDate = localStart.atZone(argentinaZone);

            ZonedDateTime now = ZonedDateTime.now(argentinaZone);

            if (now.isBefore(startDate)) {
                return 1;
            }

            long daysBetween = ChronoUnit.DAYS.between(startDate, now);

            return (int) daysBetween + 1;

        } catch (DateTimeParseException e) {
            plugin.getLogger().warning("Formato de start-date inválido en config.yml. Usa el formato: YYYY-MM-DDTHH:MM:SS");
            return 1;
        }
    }
}