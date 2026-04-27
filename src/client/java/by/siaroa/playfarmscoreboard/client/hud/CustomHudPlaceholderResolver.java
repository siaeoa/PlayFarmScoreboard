package by.siaroa.playfarmscoreboard.client.hud;

import by.siaroa.playfarmscoreboard.mixin.client.BossBarHudAccessor;
import by.siaroa.playfarmscoreboard.mixin.client.InGameHudAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.client.gui.hud.ClientBossBar;

import java.math.BigInteger;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CustomHudPlaceholderResolver {
    private static final Pattern MONEY_TOKEN = Pattern.compile("(?i)\\{money\\}");
    private static final Pattern CASH_TOKEN = Pattern.compile("(?i)\\{cash\\}");
    private static final Pattern CHAT_TOKEN = Pattern.compile("(?i)\\{chat\\}");
    private static final Pattern CHANNEL_TOKEN = Pattern.compile("(?i)\\{channel\\}");
    private static final Pattern FLY_TOKEN = Pattern.compile("(?i)\\{fly\\}");
    private static final Pattern AUTO_PLANT_TOKEN = Pattern.compile("(?i)\\{auto\\s*plant\\}");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![0-9,])(\\d{1,3}(?:,\\d{3})*|\\d+)(?![0-9,])");
    private static final Pattern CHANNEL_PATTERN = Pattern.compile("([LlIi])\\s*(\\d{1,3})(?:\\s*[cC])?");
    private static final Pattern FLY_DAY_PATTERN = Pattern.compile("(\\d+)\\s*일");
    private static final Pattern FLY_HOUR_PATTERN = Pattern.compile("(\\d+)\\s*(?:시|시간)");
    private static final Pattern FLY_MIN_PATTERN = Pattern.compile("(\\d+)\\s*분");
    private static final Pattern FLY_SEC_PATTERN = Pattern.compile("(\\d+)\\s*초");
    private static final Pattern FLY_KEYWORD_PATTERN = Pattern.compile("(?i)(?:플라이|fly)");
    private static final Pattern FLY_LABEL_PATTERN = Pattern.compile("(?i)(?:플라이|fly)\\s*(?:남은\\s*시간)?\\s*[:：-]?\\s*([^\\n\\r]+)");
    private static final Pattern FLY_CURRENT_PATTERN = Pattern.compile("\\(현재: ([^)]+)\\)");
    private static final Pattern FLY_REMAINING_PATTERN = Pattern.compile("\\(\\s*남은\\s*시간\\s*[:：]\\s*([^)]*?)\\s*\\)");
    private static final Pattern AUTO_PLANT_CURRENT_PATTERN = Pattern.compile("\\(현재\\s*[:：]\\s*([0-9][0-9,]*)\\s*회\\s*\\)");
    private static final Pattern AUTO_PLANT_KEYWORD_PATTERN = Pattern.compile("(?:자동\\s*심기|자심)");
    private static final Pattern AUTO_PLANT_REMAINING_PATTERN = Pattern.compile("남은\\s*횟수\\s*[:：]\\s*([0-9][0-9,]*)\\s*회");
    private static final Pattern AUTO_PLANT_PATTERN = Pattern.compile("(?:자동\\s*심기|자심)\\s*(?:[:：]\\s*)?([0-9][0-9,]*)\\s*회");
    private static final Pattern CHANNEL_DISPLAY_PATTERN = Pattern.compile("(스폰|섬)\\s*(\\d{1,3})\\s*(?:번\\s*)?채널", Pattern.CASE_INSENSITIVE);
    private static final Pattern FORMAT_CODE_PATTERN = Pattern.compile("§.");

    private static long lastSampleTick = Long.MIN_VALUE;
    private static BossBarSnapshot cachedSnapshot = BossBarSnapshot.EMPTY;
    private static String recentChannelDisplay = "";
    private static boolean flyOverlayVisible;
    private static boolean autoPlantOverlayVisible;

    private CustomHudPlaceholderResolver() {
    }

    public static String resolve(String rawText) {
        if (rawText == null || rawText.isEmpty() || rawText.indexOf('{') < 0) {
            return rawText;
        }

        // 토큰이 여러 개 섞여도 한 번에 쭉 처리한다. 사용자 입장에선 "그냥 다 바뀌면" 된다.
        BossBarSnapshot snapshot = resolveSnapshot();
        String resolved = rawText;
        resolved = replaceIfPresent(resolved, MONEY_TOKEN, snapshot.money());
        resolved = replaceIfPresent(resolved, CASH_TOKEN, snapshot.cash());
        resolved = replaceIfPresent(resolved, CHAT_TOKEN, snapshot.chatDisplay());
        resolved = replaceIfPresent(resolved, CHANNEL_TOKEN, snapshot.channelDisplay());
        resolved = replaceIfPresent(resolved, FLY_TOKEN, snapshot.flyDisplay());
        resolved = replaceIfPresent(resolved, AUTO_PLANT_TOKEN, snapshot.autoPlantDisplay());
        return resolved;
    }

    public static void observeOverlayMessage(String rawOverlayText) {
        if (rawOverlayText == null || rawOverlayText.isBlank()) {
            return;
        }

        // 액션바는 진짜 찰나에 사라질 때가 있어서, 보이는 순간 바로 챙겨둔다.
        String latestFly = parseFlyText(rawOverlayText);
        if (!latestFly.isBlank()) {
            CustomHudFlyStorage.updateFly(latestFly);
            cachedSnapshot = cachedSnapshot.withFly(latestFly);
            flyOverlayVisible = true;
        }

        // 자동심기도 같은 방식으로 즉시 반영: 놓치면 다음 틱에 흔적도 없다.
        String latestAutoPlant = parseAutoPlantText(rawOverlayText);
        if (!latestAutoPlant.isBlank()) {
            CustomHudFlyStorage.updateAutoPlant(latestAutoPlant);
            cachedSnapshot = cachedSnapshot.withAutoPlant(latestAutoPlant);
            autoPlantOverlayVisible = true;
        }

        String latestChannel = parseChannelText(rawOverlayText);
        if (!latestChannel.isBlank()) {
            recentChannelDisplay = latestChannel;
            cachedSnapshot = cachedSnapshot.withChannel(latestChannel);
        }
    }

    private static BossBarSnapshot resolveSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.inGameHud == null || client.world == null) {
            return BossBarSnapshot.EMPTY;
        }

        long tick = client.world.getTime();
        if (tick == lastSampleTick) {
            return cachedSnapshot;
        }
        lastSampleTick = tick;

        BossBarHud bossBarHud = client.inGameHud.getBossBarHud();
        if (!(bossBarHud instanceof BossBarHudAccessor accessor)) {
            cachedSnapshot = BossBarSnapshot.EMPTY;
            return cachedSnapshot;
        }

        // 보스바 여러 줄이 동시에 떠도 제일 그럴듯한 후보를 점수로 고르는 전략.
        Map<UUID, ClientBossBar> bars = accessor.playfarmscoreboard$getBossBars();
        String money = "";
        String cash = "";
        String chat = "";
        String channel = "";
        int moneyWeight = -1;
        int cashWeight = -1;
        int chatWeight = -1;
        int channelWeight = -1;

        for (ClientBossBar bar : bars.values()) {
            String raw = bar.getName().getString();
            BossBarSnapshot candidate = parseFromBossBar(raw);
            int weight = candidate.score();

            if (!candidate.money().isBlank() && weight > moneyWeight) {
                money = candidate.money();
                moneyWeight = weight;
            }
            if (!candidate.cash().isBlank() && weight > cashWeight) {
                cash = candidate.cash();
                cashWeight = weight;
            }
            if (!candidate.chatDisplay().isBlank() && weight > chatWeight) {
                chat = candidate.chatDisplay();
                chatWeight = weight;
            }
            if (!candidate.channelDisplay().isBlank() && weight > channelWeight) {
                channel = candidate.channelDisplay();
                channelWeight = weight;
            }
        }

        if (!channel.isBlank()) {
            recentChannelDisplay = channel;
        } else if (!recentChannelDisplay.isBlank()) {
            channel = recentChannelDisplay;
        }

        BossBarSnapshot resolved = new BossBarSnapshot(money, cash, chat, channel, "", "");
        cachedSnapshot = resolved.score() <= 0 ? BossBarSnapshot.EMPTY : resolved;
        cachedSnapshot = cachedSnapshot.withFly(resolveFlyDisplay(client));
        cachedSnapshot = cachedSnapshot.withAutoPlant(resolveAutoPlantDisplay(client));
        return cachedSnapshot;
    }

    private static String resolveFlyDisplay(MinecraftClient client) {
        String latestFly = "";
        if (client.inGameHud instanceof InGameHudAccessor accessor) {
            if (accessor.playfarmscoreboard$getOverlayRemaining() > 0 && accessor.playfarmscoreboard$getOverlayMessage() != null) {
                latestFly = parseFlyText(accessor.playfarmscoreboard$getOverlayMessage().getString());
            }
        }

        if (!latestFly.isBlank()) {
            CustomHudFlyStorage.updateFly(latestFly);
            flyOverlayVisible = true;
            return latestFly;
        }

        if (flyOverlayVisible) {
            CustomHudFlyStorage.flush();
            flyOverlayVisible = false;
        }

        String cachedFly = CustomHudFlyStorage.getCachedFly();
        if (!cachedFly.isBlank()) {
            return cachedFly;
        }

        return "알 수 없음";
    }

    private static String resolveAutoPlantDisplay(MinecraftClient client) {
        String latestAutoPlant = "";
        if (client.inGameHud instanceof InGameHudAccessor accessor) {
            if (accessor.playfarmscoreboard$getOverlayRemaining() > 0 && accessor.playfarmscoreboard$getOverlayMessage() != null) {
                latestAutoPlant = parseAutoPlantText(accessor.playfarmscoreboard$getOverlayMessage().getString());
            }
        }

        if (!latestAutoPlant.isBlank()) {
            CustomHudFlyStorage.updateAutoPlant(latestAutoPlant);
            autoPlantOverlayVisible = true;
            return latestAutoPlant;
        }

        if (autoPlantOverlayVisible) {
            CustomHudFlyStorage.flush();
            autoPlantOverlayVisible = false;
        }

        String cachedAutoPlant = CustomHudFlyStorage.getCachedAutoPlant();
        if (!cachedAutoPlant.isBlank()) {
            return cachedAutoPlant;
        }

        return "알 수 없음";
    }

    private static BossBarSnapshot parseFromBossBar(String raw) {
        if (raw == null || raw.isBlank()) {
            return BossBarSnapshot.EMPTY;
        }

        String normalized = normalizeForParsing(raw);
        if (normalized.isBlank()) {
            return BossBarSnapshot.EMPTY;
        }

        String money = "";
        String cash = "";
        char earliestChatCode = 0;
        int earliestChatCodeIndex = Integer.MAX_VALUE;
        // 문자열 파싱은 좀 어렵구만 AI한테 ㄱㄱ, 그래도 여기선 채팅 코드 경계를 먼저 잡아 둔다.
        for (char candidate : new char[]{'i', 'l', 'g'}) {
            int index = normalized.toLowerCase().indexOf(candidate);
            if (index >= 0 && index < earliestChatCodeIndex) {
                earliestChatCodeIndex = index;
                earliestChatCode = normalized.charAt(index);
            }
        }

        Matcher numberMatcher = NUMBER_PATTERN.matcher(normalized);
        while (numberMatcher.find()) {
            if (earliestChatCodeIndex != Integer.MAX_VALUE && numberMatcher.end() > earliestChatCodeIndex) {
                continue;
            }

            String numberToken = numberMatcher.group(1);
            if (money.isEmpty()) {
                money = numberToken;
                continue;
            }
            if (cash.isEmpty()) {
                cash = numberToken;
                break;
            }
        }

        int searchStart = 0;
        if (!cash.isEmpty()) {
            int cashIndex = normalized.indexOf(cash);
            if (cashIndex >= 0) {
                searchStart = cashIndex + cash.length();
            }
        } else if (!money.isEmpty()) {
            int moneyIndex = normalized.indexOf(money);
            if (moneyIndex >= 0) {
                searchStart = moneyIndex + money.length();
            }
        }

        char chatCode = findChatCode(normalized, searchStart);
        if (chatCode == 0) {
            chatCode = findChatCode(normalized, 0);
        }
        String chatDisplay = switch (Character.toLowerCase(chatCode)) {
            case 'i' -> "섬 채팅";
            case 'l' -> "지역 채팅";
            case 'g' -> "전체 채팅";
            default -> "";
        };

        String channelDisplay = "";
        // 채널 표기는 서버마다 미묘하게 달라서, 최대한 널널하게 매칭해 둔다.
        Matcher channelMatcher = CHANNEL_PATTERN.matcher(normalized);
        boolean foundChannel = channelMatcher.find(searchStart);
        if (!foundChannel) {
            foundChannel = channelMatcher.find();
        }
        if (foundChannel) {
            char scope = channelMatcher.group(1).charAt(0);
            String channelNumber = channelMatcher.group(2);
            if (scope == 'L') {
                channelDisplay = "스폰 " + channelNumber + "번 채널";
            } else {
                channelDisplay = "섬 " + channelNumber + "번 채널";
            }
        }

        return new BossBarSnapshot(money, cash, chatDisplay, channelDisplay, "", "");
    }

    private static String parseFlyText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = normalizeOverlayText(raw);
        if (normalized.isBlank()) {
            return "";
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        String parseTarget = "";
        boolean dedicatedRemainingSource = false;

        Matcher currentMatcher = FLY_CURRENT_PATTERN.matcher(normalized);
        if (currentMatcher.find()) {
            // "(현재: N)"가 있으면 N만 플라이 시간 소스로 쓴다.
            parseTarget = currentMatcher.group(1);
            dedicatedRemainingSource = true;
        } else {
            Matcher remainingMatcher = FLY_REMAINING_PATTERN.matcher(normalized);
            if (remainingMatcher.find() && FLY_KEYWORD_PATTERN.matcher(normalized).find()) {
                parseTarget = remainingMatcher.group(1);
                dedicatedRemainingSource = true;
            }
        }

        if (parseTarget.isBlank()) {
            Matcher labelMatcher = FLY_LABEL_PATTERN.matcher(normalized);
            if (labelMatcher.find()) {
                parseTarget = labelMatcher.group(1);
            } else if (normalized.contains("플라이") || lower.contains("fly")) {
                parseTarget = normalized;
            }
        }
        if (parseTarget.isBlank()) {
            return "";
        }

        String day = findUnit(FLY_DAY_PATTERN, parseTarget, "일");
        String hour = findUnit(FLY_HOUR_PATTERN, parseTarget, "시간");
        String minute = findUnit(FLY_MIN_PATTERN, parseTarget, "분");
        String second = findUnit(FLY_SEC_PATTERN, parseTarget, "초");

        // 표기는 최대한 사람이 읽기 편한 순서로만 정리한다. 복잡한 정규화는 여기서 욕심내지 않는다.
        StringBuilder builder = new StringBuilder();
        appendUnit(builder, day);
        appendUnit(builder, hour);
        appendUnit(builder, minute);
        appendUnit(builder, second);
        if (builder.isEmpty()) {
            if (dedicatedRemainingSource) {
                return parseTarget.trim();
            }
            return "";
        }
        return builder.toString();
    }

    private static String parseAutoPlantText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String normalized = normalizeOverlayText(raw);
        if (normalized.isBlank()) {
            return "";
        }

        Matcher currentMatcher = AUTO_PLANT_CURRENT_PATTERN.matcher(normalized);
        if (currentMatcher.find()) {
            return formatAutoPlantCount(currentMatcher.group(1));
        }

        Matcher remainingMatcher = AUTO_PLANT_REMAINING_PATTERN.matcher(normalized);
        if (remainingMatcher.find() && AUTO_PLANT_KEYWORD_PATTERN.matcher(normalized).find()) {
            return formatAutoPlantCount(remainingMatcher.group(1));
        }

        Matcher matcher = AUTO_PLANT_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return "";
        }

        return formatAutoPlantCount(matcher.group(1));
    }

    private static String formatAutoPlantCount(String rawNumeric) {
        if (rawNumeric == null) {
            return "";
        }

        String numeric = rawNumeric.replace(",", "");
        if (numeric.isEmpty()) {
            return "";
        }

        try {
            NumberFormat formatter = NumberFormat.getIntegerInstance(Locale.US);
            formatter.setGroupingUsed(true);
            return formatter.format(new BigInteger(numeric));
        } catch (NumberFormatException ignored) {
            return "";
        }
    }

    private static String parseChannelText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }

        String normalized = normalizeOverlayText(raw);
        if (normalized.isBlank()) {
            return "";
        }

        Matcher displayMatcher = CHANNEL_DISPLAY_PATTERN.matcher(normalized);
        if (displayMatcher.find()) {
            String scope = displayMatcher.group(1);
            String channelNumber = displayMatcher.group(2);
            if (scope == null || channelNumber == null) {
                return "";
            }
            boolean spawn = scope.toLowerCase(Locale.ROOT).contains("스폰");
            return spawn
                    ? "스폰 " + channelNumber + "번 채널"
                    : "섬 " + channelNumber + "번 채널";
        }

        String compact = normalizeForParsing(normalized);
        if (compact.isBlank()) {
            return "";
        }

        Matcher channelMatcher = CHANNEL_PATTERN.matcher(compact);
        if (!channelMatcher.find()) {
            return "";
        }

        char scope = Character.toLowerCase(channelMatcher.group(1).charAt(0));
        String channelNumber = channelMatcher.group(2);
        if (scope == 'l') {
            return "스폰 " + channelNumber + "번 채널";
        }
        return "섬 " + channelNumber + "번 채널";
    }

    private static String normalizeOverlayText(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return FORMAT_CODE_PATTERN.matcher(raw).replaceAll("").replace('\u00A0', ' ').trim();
    }

    private static String findUnit(Pattern pattern, String value, String suffix) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            return "";
        }
        // 누락된 단위는 과감히 생략, 있는 단위만 이어 붙이는 쪽이 실전에서 덜 아프다.
        return matcher.group(1) + suffix;
    }

    private static void appendUnit(StringBuilder builder, String unitText) {
        if (unitText == null || unitText.isBlank()) {
            return;
        }
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(unitText);
    }

    private static char findChatCode(String value, int start) {
        int from = Math.max(0, start);
        for (int i = from; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (c == 'i' || c == 'l' || c == 'g') {
                return c;
            }
        }
        return 0;
    }

    private static String normalizeForParsing(String raw) {
        StringBuilder builder = new StringBuilder(raw.length() + 8);
        boolean previousWasTokenChar = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            boolean tokenChar = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'z')
                    || (c >= 'A' && c <= 'Z')
                    || c == ',';
            if (tokenChar) {
                builder.append(c);
                previousWasTokenChar = true;
            } else if (previousWasTokenChar) {
                builder.append(' ');
                previousWasTokenChar = false;
            }
        }
        return builder.toString().trim();
    }

    private static String replaceIfPresent(String source, Pattern token, String value) {
        if (value == null || value.isBlank()) {
            return source;
        }
        return token.matcher(source).replaceAll(Matcher.quoteReplacement(value));
    }

    private record BossBarSnapshot(
            String money,
            String cash,
            String chatDisplay,
            String channelDisplay,
            String flyDisplay,
            String autoPlantDisplay
    ) {
        private static final BossBarSnapshot EMPTY = new BossBarSnapshot("", "", "", "", "알 수 없음", "알 수 없음");

        private int score() {
            int score = 0;
            if (!money.isBlank()) {
                score++;
            }
            if (!cash.isBlank()) {
                score++;
            }
            if (!chatDisplay.isBlank()) {
                score++;
            }
            if (!channelDisplay.isBlank()) {
                score++;
            }
            return score;
        }

        private BossBarSnapshot withFly(String fly) {
            return new BossBarSnapshot(money, cash, chatDisplay, channelDisplay, fly, autoPlantDisplay);
        }

        private BossBarSnapshot withAutoPlant(String autoPlant) {
            return new BossBarSnapshot(money, cash, chatDisplay, channelDisplay, flyDisplay, autoPlant);
        }

        private BossBarSnapshot withChannel(String channel) {
            return new BossBarSnapshot(money, cash, chatDisplay, channel, flyDisplay, autoPlantDisplay);
        }
    }
}
