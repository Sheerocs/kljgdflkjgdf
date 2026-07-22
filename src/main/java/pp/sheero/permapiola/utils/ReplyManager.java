package pp.sheero.permapiola.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ReplyManager {
    private static final Map<UUID, ReplyData> replies = new HashMap<>();
    private static final long EXPIRATION_TIME = 5 * 60 * 1000;

    public static void setReplyTarget(UUID sender, UUID target) {
        long currentTime = System.currentTimeMillis();
        replies.put(sender, new ReplyData(target, currentTime));
        replies.put(target, new ReplyData(sender, currentTime));
    }
    public static UUID getReplyTarget(UUID player) {
        ReplyData data = replies.get(player);
        if (data == null) return null;
        if (System.currentTimeMillis() - data.timestamp > EXPIRATION_TIME) {
            replies.remove(player);
            return null;
        }
        return data.targetUUID;
    }
    private static class ReplyData {
        UUID targetUUID;
        long timestamp;
        public ReplyData(UUID targetUUID, long timestamp) {
            this.targetUUID = targetUUID;
            this.timestamp = timestamp;
        }
    }
}